package cpsc4620;

import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.Comparator;

/*
 * This file is where you will implement the methods needed to support this application.
 * You will write the code to retrieve and save information to the database and use that
 * information to build the various objects required by the applicaiton.
 * 
 * The class has several hard coded static variables used for the connection, you will need to
 * change those to your connection information
 * 
 * This class also has static string variables for pickup, delivery and dine-in. 
 * DO NOT change these constant values.
 * 
 * You can add any helper methods you need, but you must implement all the methods
 * in this class and use them to complete the project.  The autograder will rely on
 * these methods being implemented, so do not delete them or alter their method
 * signatures.
 * 
 * Make sure you properly open and close your DB connections in any method that
 * requires access to the DB.
 * Use the connect_to_db below to open your connection in DBConnector.
 * What is opened must be closed!
 */

/*
 * A utility class to help add and retrieve information from the database.
 * All application DB logic funnels through here.
 */

public final class DBNinja {

	// Single shared Connection object used by all static methods.
	private static Connection conn;

	// =========================================================
	// DO NOT change these variables! They are used as constants
	// throughout the project and expected by the autograder.
	// =========================================================
	public final static String pickup = "pickup";
	public final static String delivery = "delivery";
	public final static String dine_in = "dinein";

	public final static String size_s = "Small";
	public final static String size_m = "Medium";
	public final static String size_l = "Large";
	public final static String size_xl = "XLarge";

	public final static String crust_thin = "Thin";
	public final static String crust_orig = "Original";
	public final static String crust_pan = "Pan";
	public final static String crust_gf = "Gluten-Free";

	// Order state enum used for completeOrder transitions
	public enum order_state {
		PREPARED,
		DELIVERED,
		PICKEDUP
	}

	// =========================================================
	// CONNECTION MANAGEMENT
	// =========================================================

	private static boolean connect_to_db() throws SQLException, IOException 
	{
		/*
		 * Opens a connection via DBConnector if one does not already exist,
		 * or if the previous one was closed.
		 */

		try {
			// Only create a new connection if needed
			if (conn == null || conn.isClosed()) {
				conn = DBConnector.make_connection();
			}
			return true;
		} catch (SQLException e) {
			// if connecting fails, signal failure
			return false;
		} catch (IOException e) {
			// propagate same behavior for IOException
			return false;
		}

	}

	// =========================================================
	// PUBLIC API: WRITE OPERATIONS
	// =========================================================

	public static void addOrder(Order o) throws SQLException, IOException 
	{
		/*
		 * add code to add the order to the DB. Remember that we're not just
		 * adding the order to the order DB table, but we're also recording
		 * the necessary data for the delivery, dinein, pickup, pizzas, toppings
		 * on pizzas, order discounts and pizza discounts.
		 * 
		 * This is a KEY method as it must store all the data in the Order object
		 * in the database and make sure all the tables are correctly linked.
		 * 
		 * Remember, if the order is for Dine In, there is no customer...
		 * so the cusomter id coming from the Order object will be -1.
		 * 
		 */

		// Track whether this method is the one that opened the connection
		boolean openedHere = ensureConnection();

		try {
			// Use an explicit transaction to ensure all inserts succeed or fail together
			conn.setAutoCommit(false);

			// ----------------------------------------------------
			// 1. Insert into ordertable
			// ----------------------------------------------------
			String insertOrder = "INSERT INTO ordertable (customer_CustID, ordertable_OrderType, ordertable_OrderDateTime, "
					+ "ordertable_CustPrice, ordertable_BusPrice, ordertable_IsComplete) VALUES (?,?,?,?,?,?)";
			PreparedStatement os = conn.prepareStatement(insertOrder, Statement.RETURN_GENERATED_KEYS);

			// Dine-in orders have no customer, so custID <= 0 => set NULL
			if (o.getCustID() <= 0) {
				os.setNull(1, Types.INTEGER);
			} else {
				os.setInt(1, o.getCustID());
			}

			os.setString(2, o.getOrderType());
			os.setTimestamp(3, parseTimestamp(o.getDate()));
			os.setDouble(4, o.getCustPrice());
			os.setDouble(5, o.getBusPrice());
			os.setBoolean(6, o.getIsComplete());
			os.executeUpdate();

			// Retrieve generated order ID
			ResultSet keys = os.getGeneratedKeys();
			int orderId = -1;
			if (keys.next()) {
				orderId = keys.getInt(1);
				o.setOrderID(orderId);  // keep object in sync with DB
			}
			os.close();

			// ----------------------------------------------------
			// 2. Insert into appropriate child table (dinein/pickup/delivery)
			// ----------------------------------------------------
			addOrderTypeDetails(conn, o, orderId);

			// ----------------------------------------------------
			// 3. Insert pizzas and their related data
			// ----------------------------------------------------
			Timestamp orderDts = parseTimestamp(o.getDate());
			for (Pizza p : o.getPizzaList()) {
				p.setOrderID(orderId);
				insertPizza(conn, orderDts, orderId, p);
			}

			// ----------------------------------------------------
			// 4. Insert order-level discounts, if any
			// ----------------------------------------------------
			if (o.getDiscountList() != null) {
				String orderDiscSql = "INSERT INTO order_discount (ordertable_OrderID, discount_DiscountID) VALUES (?,?)";
				for (Discount d : o.getDiscountList()) {
					PreparedStatement ods = conn.prepareStatement(orderDiscSql);
					ods.setInt(1, orderId);
					ods.setInt(2, d.getDiscountID());
					ods.executeUpdate();
					ods.close();
				}
			}

			// Everything succeeded
			conn.commit();
		} catch (SQLException e) {
			// Any error => rollback the whole transaction
			conn.rollback();
			throw e;
		} finally {
			// Only close connection if we opened it here
			if (openedHere) {
				closeConn();
			}
		}
	}
	
	public static int addPizza(java.util.Date d, int orderID, Pizza p) throws SQLException, IOException
	{
		/*
		 * Add the code needed to insert the pizza into into the database.
		 * Keep in mind you must also add the pizza discounts and toppings 
		 * associated with the pizza.
		 * 
		 * NOTE: there is a Date object passed into this method so that the Order
		 * and ALL its Pizzas can be assigned the same DTS.
		 * 
		 * This method returns the id of the pizza just added.
		 * 
		 */

		// Again track whether this method opened the connection
		boolean openedHere = ensureConnection();
		int pizzaId;

		try {
			conn.setAutoCommit(false);

			// Insert pizza and its toppings/discounts in a helper
			pizzaId = insertPizza(conn, new java.sql.Timestamp(d.getTime()), orderID, p);

			conn.commit();
		} catch (SQLException e) {
			conn.rollback();
			throw e;
		} finally {
			if (openedHere) {
				closeConn();
			}
		}

		return pizzaId;
	}
	
	public static int addCustomer(Customer c) throws SQLException, IOException
	{
		/*
		 * This method adds a new customer to the database.
		 * 
		 */
		boolean openedHere = ensureConnection();
		int custId = -1;

		try {
			String sql = "INSERT INTO customer (customer_FName, customer_LName, customer_PhoneNum) VALUES (?,?,?)";
			PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);

			// bind incoming Customer object fields
			ps.setString(1, c.getFName());
			ps.setString(2, c.getLName());
			ps.setString(3, c.getPhone());
			ps.executeUpdate();

			// get generated customer ID
			ResultSet keys = ps.getGeneratedKeys();
			if (keys.next()) {
				custId = keys.getInt(1);
			}
			ps.close();
		} finally {
			if (openedHere) {
				closeConn();
			}
		}

		return custId;
	}

	public static void completeOrder(int OrderID, order_state newState ) throws SQLException, IOException
	{
		/*
		 * Mark that order as complete in the database.
		 * Note: if an order is complete, this means all the pizzas are complete as well.
		 * However, it does not mean that the order has been delivered or picked up!
		 *
		 * For newState = PREPARED: mark the order and all associated pizza's as completed
		 * For newState = DELIVERED: mark the delivery status
		 * FOR newState = PICKEDUP: mark the pickup status
		 * 
		 */

		boolean openedHere = ensureConnection();

		try {
			// Switch behavior based on requested state
			switch (newState) {
				case PREPARED:
					// mark order as complete
					PreparedStatement ps = conn.prepareStatement("UPDATE ordertable SET ordertable_IsComplete=1 WHERE ordertable_OrderID=?");
					ps.setInt(1, OrderID);
					ps.executeUpdate();
					ps.close();

					// mark all pizzas as completed
					PreparedStatement ps2 = conn.prepareStatement("UPDATE pizza SET pizza_PizzaState='completed' WHERE ordertable_OrderID=?");
					ps2.setInt(1, OrderID);
					ps2.executeUpdate();
					ps2.close();
					break;

				case DELIVERED:
					// mark delivery row as delivered
					PreparedStatement ps3 = conn.prepareStatement("UPDATE delivery SET delivery_IsDelivered=1 WHERE ordertable_OrderID=?");
					ps3.setInt(1, OrderID);
					ps3.executeUpdate();
					ps3.close();
					break;

				case PICKEDUP:
					// mark pickup row as picked up
					PreparedStatement ps4 = conn.prepareStatement("UPDATE pickup SET pickup_IsPickedUp=1 WHERE ordertable_OrderID=?");
					ps4.setInt(1, OrderID);
					ps4.executeUpdate();
					ps4.close();
					break;
			}
		} finally {
			if (openedHere) {
				closeConn();
			}
		}

	}

	// =========================================================
	// PUBLIC API: READ OPERATIONS
	// =========================================================

	public static ArrayList<Order> getOrders(int status) throws SQLException, IOException
	{
	/*
	 * Return an ArrayList of orders.
	 * 	status   == 1 => return a list of open (ie oder is not completed)
	 *           == 2 => return a list of completed orders (ie order is complete)
	 *           == 3 => return a list of all the orders
	 * Remember that in Java, we account for supertypes and subtypes
	 * which means that when we create an arrayList of orders, that really
	 * means we have an arrayList of dineinOrders, deliveryOrders, and pickupOrders.
	 *
	 * You must fully populate the Order object, this includes order discounts,
	 * and pizzas along with the toppings and discounts associated with them.
	 * 
	 * Don't forget to order the data according to their order sequence, ie, order 1, order 2, etc.
	 *
	 */

		boolean openedHere = ensureConnection();
		ArrayList<Order> orders = new ArrayList<Order>();

		try {
			// Base query from ordertable
			StringBuilder sql = new StringBuilder("SELECT * FROM ordertable");

			// Add filter based on status param
			if (status == 1) {
				sql.append(" WHERE ordertable_IsComplete=0");
			} else if (status == 2) {
				sql.append(" WHERE ordertable_IsComplete=1");
			}
			sql.append(" ORDER BY ordertable_OrderID ASC");

			PreparedStatement ps = conn.prepareStatement(sql.toString());
			ResultSet rs = ps.executeQuery();

			// For each order row, build a fully populated Order object
			while (rs.next()) {
				Order o = buildOrderFromResult(conn, rs);
				orders.add(o);
			}

			// Sort just in case; comparator uses orderID
			orders.sort(Comparator.comparingInt(Order::getOrderID));

			rs.close();
			ps.close();
		} finally {
			if (openedHere) {
				closeConn();
			}
		}

		return orders;
	}
	
	public static Order getLastOrder() throws SQLException, IOException 
	{
		/*
		 * Query the database for the LAST order added
		 * then return an Order object for that order.
		 * NOTE...there will ALWAYS be a "last order"!
		 */

		boolean openedHere = ensureConnection();
		Order order = null;

		try {
			// Last order = highest ordertable_OrderID
			String sql = "SELECT * FROM ordertable ORDER BY ordertable_OrderID DESC LIMIT 1";
			PreparedStatement ps = conn.prepareStatement(sql);
			ResultSet rs = ps.executeQuery();
			if (rs.next()) {
				order = buildOrderFromResult(conn, rs);
			}
			rs.close();
			ps.close();
		} finally {
			if (openedHere) {
				closeConn();
			}
		}

		return order;
	}

	public static ArrayList<Order> getOrdersByDate(String date) throws SQLException, IOException
	{
		/*
		 * Query the database for ALL the orders placed on a specific date
		 * and return a list of those orders.
		 *  
		 */

		boolean openedHere = ensureConnection();
		ArrayList<Order> orders = new ArrayList<Order>();

		try {
			// Use MySQL DATE() to compare date portion only
			String sql = "SELECT * FROM ordertable WHERE DATE(ordertable_OrderDateTime)=? ORDER BY ordertable_OrderDateTime ASC";
			PreparedStatement ps = conn.prepareStatement(sql);
			ps.setString(1, date);
			ResultSet rs = ps.executeQuery();

			// Build full Order objects for each matching row
			while (rs.next()) {
				orders.add(buildOrderFromResult(conn, rs));
			}
			rs.close();
			ps.close();
		} finally {
			if (openedHere) {
				closeConn();
			}
		}

		return orders;
	}
		
	public static ArrayList<Discount> getDiscountList() throws SQLException, IOException 
	{
		/* 
		 * Query the database for all the available discounts and 
		 * return them in an arrayList of discounts ordered by discount name.
		 * 
		*/

		boolean openedHere = ensureConnection();
		ArrayList<Discount> discounts = new ArrayList<Discount>();

		try {
			String sql = "SELECT * FROM discount ORDER BY discount_DiscountName ASC";
			PreparedStatement ps = conn.prepareStatement(sql);
			ResultSet rs = ps.executeQuery();

			// Map every row to a Discount object
			while (rs.next()) {
				discounts.add(mapDiscount(rs));
			}
			rs.close();
			ps.close();
		} finally {
			if (openedHere) {
				closeConn();
			}
		}

		return discounts;
	}

	public static Discount findDiscountByName(String name) throws SQLException, IOException 
	{
		/*
		 * Query the database for a discount using it's name.
		 * If found, then return an OrderDiscount object for the discount.
		 * If it's not found....then return null
		 *  
		 */

		boolean openedHere = ensureConnection();
		Discount d = null;

		try {
			String sql = "SELECT * FROM discount WHERE discount_DiscountName=?";
			PreparedStatement ps = conn.prepareStatement(sql);
			ps.setString(1, name);
			ResultSet rs = ps.executeQuery();

			if (rs.next()) {
				d = mapDiscount(rs);
			}
			rs.close();
			ps.close();
		} finally {
			if (openedHere) {
				closeConn();
			}
		}

		return d;
	}


	public static ArrayList<Customer> getCustomerList() throws SQLException, IOException 
	{
		/*
		 * Query the data for all the customers and return an arrayList of all the customers. 
		 * Don't forget to order the data coming from the database appropriately.
		 * 
		*/
		boolean openedHere = ensureConnection();
		ArrayList<Customer> customers = new ArrayList<Customer>();

		try {
			// Order by last name, then first name, then phone
			String sql = "SELECT * FROM customer ORDER BY customer_LName, customer_FName, customer_PhoneNum";
			PreparedStatement ps = conn.prepareStatement(sql);
			ResultSet rs = ps.executeQuery();

			// map each row to a Customer object
			while (rs.next()) {
				customers.add(new Customer(rs.getInt("customer_CustID"), rs.getString("customer_FName"),
						rs.getString("customer_LName"), rs.getString("customer_PhoneNum")));
			}
			rs.close();
			ps.close();
		} finally {
			if (openedHere) {
				closeConn();
			}
		}

		return customers;
	}

	public static Customer findCustomerByPhone(String phoneNumber)  throws SQLException, IOException 
	{
		/*
		 * Query the database for a customer using a phone number.
		 * If found, then return a Customer object for the customer.
		 * If it's not found....then return null
		 *  
		 */

		boolean openedHere = ensureConnection();
		Customer c = null;

		try {
			String sql = "SELECT * FROM customer WHERE customer_PhoneNum=?";
			PreparedStatement ps = conn.prepareStatement(sql);
			ps.setString(1, phoneNumber);
			ResultSet rs = ps.executeQuery();

			// there should be at most one match
			if (rs.next()) {
				c = new Customer(rs.getInt("customer_CustID"), rs.getString("customer_FName"),
						rs.getString("customer_LName"), rs.getString("customer_PhoneNum"));
			}
			rs.close();
			ps.close();
		} finally {
			if (openedHere) {
				closeConn();
			}
		}

		return c;
	}

	public static String getCustomerName(int CustID) throws SQLException, IOException 
	{
		/*
		 * COMPLETED...WORKING Example!
		 * 
		 * This is a helper method to fetch and format the name of a customer
		 * based on a customer ID. This is an example of how to interact with
		 * your database from Java.  
		 * 
		 * Notice how the connection to the DB made at the start of the 
		 *
		 */

		boolean openedHere = ensureConnection();

		/* 
		 * an example query using a constructed string...
		 * remember, this style of query construction could be subject to sql injection attacks!
		 * 
		 */
		String cname1 = "";
		String cname2 = "";
		String query = "Select customer_FName, customer_LName From customer WHERE customer_CustID=" + CustID + ";";
		Statement stmt = conn.createStatement();
		ResultSet rset = stmt.executeQuery(query);
		
		while(rset.next())
		{
			cname1 = rset.getString(1) + " " + rset.getString(2); 
		}

		/* 
		* an BETTER example of the same query using a prepared statement...
		* with exception handling
		* 
		*/
		try {
			PreparedStatement os;
			ResultSet rset2;
			String query2;
			query2 = "Select customer_FName, customer_LName From customer WHERE customer_CustID=?;";
			os = conn.prepareStatement(query2);
			os.setInt(1, CustID);
			rset2 = os.executeQuery();
			while(rset2.next())
			{
				cname2 = rset2.getString("customer_FName") + " " + rset2.getString("customer_LName"); // note the use of field names in the getSting methods
			}
		} catch (SQLException e) {
			e.printStackTrace();
			// process the error or re-raise the exception to a higher level
		}

		if (openedHere) {
			closeConn();
		}

		// returns value from first approach;
		// both cname1 and cname2 are effectively the same.
		return cname1;
		// OR
		// return cname2;

	}


	public static ArrayList<Topping> getToppingList() throws SQLException, IOException 
	{
		/*
		 * Query the database for the aviable toppings and 
		 * return an arrayList of all the available toppings. 
		 * Don't forget to order the data coming from the database appropriately.
		 * 
		 */

		boolean openedHere = ensureConnection();
		ArrayList<Topping> tops = new ArrayList<Topping>();

		try {
			String sql = "SELECT * FROM topping ORDER BY topping_TopName";
			PreparedStatement ps = conn.prepareStatement(sql);
			ResultSet rs = ps.executeQuery();

			// Map all topping rows into Topping objects
			while (rs.next()) {
				tops.add(mapTopping(rs));
			}
			rs.close();
			ps.close();
		} finally {
			if (openedHere) {
				closeConn();
			}
		}

		return tops;
	}

	public static Topping findToppingByName(String name) throws SQLException, IOException 
	{
		/*
		 * Query the database for the topping using it's name.
		 * If found, then return a Topping object for the topping.
		 * If it's not found....then return null
		 *  
		 */

		boolean openedHere = ensureConnection();
		Topping t = null;

		try {
			String sql = "SELECT * FROM topping WHERE topping_TopName=?";
			PreparedStatement ps = conn.prepareStatement(sql);
			ps.setString(1, name);
			ResultSet rs = ps.executeQuery();

			if (rs.next()) {
				t = mapTopping(rs);
			}
			rs.close();
			ps.close();
		} finally {
			if (openedHere) {
				closeConn();
			}
		}

		return t;
	}

	public static ArrayList<Topping> getToppingsOnPizza(Pizza p) throws SQLException, IOException 
	{
		/* 
		 * This method builds an ArrayList of the toppings ON a pizza.
		 * The list can then be added to the Pizza object elsewhere in the
		 */

		boolean openedHere = ensureConnection();
		ArrayList<Topping> tops = new ArrayList<Topping>();

		try {
			// delegate to helper that queries pizza_topping + topping
			tops = fetchPizzaToppings(conn, p.getPizzaID());
		} finally {
			if (openedHere) {
				closeConn();
			}
		}

		return tops;	
	}

	public static void addToInventory(int toppingID, double quantity) throws SQLException, IOException 
	{
		/*
		 * Updates the quantity of the topping in the database by the amount specified.
		 * A positive quantity increases inventory.
		 * 
		 */

		boolean openedHere = ensureConnection();

		try {
			String sql = "UPDATE topping SET topping_CurINVT = topping_CurINVT + ? WHERE topping_TopID=?";
			PreparedStatement ps = conn.prepareStatement(sql);
			ps.setDouble(1, quantity);
			ps.setInt(2, toppingID);
			ps.executeUpdate();
			ps.close();
		} finally {
			if (openedHere) {
				closeConn();
			}
		}
	}
	
	
	public static ArrayList<Pizza> getPizzas(Order o) throws SQLException, IOException 
	{
		/*
		 * Build an ArrayList of all the Pizzas associated with the Order.
		 * Each Pizza object will be fully populated (toppings + discounts).
		 */

		boolean openedHere = ensureConnection();
		ArrayList<Pizza> pizzas = new ArrayList<Pizza>();

		try {
			// Use helper that queries the pizza table and related data
			pizzas = fetchPizzasForOrder(conn, o.getOrderID());
		} finally {
			if (openedHere) {
				closeConn();
			}
		}

		return pizzas;
	}

	public static ArrayList<Discount> getDiscounts(Order o) throws SQLException, IOException 
	{
		/* 
		 * Build an array list of all the Discounts associted with the Order.
		 * Discounts come from order_discount bridge table.
		 */

		boolean openedHere = ensureConnection();
		ArrayList<Discount> discounts = new ArrayList<Discount>();

		try {
			discounts = fetchOrderDiscounts(conn, o.getOrderID());
		} finally {
			if (openedHere) {
				closeConn();
			}
		}

		return discounts;
	}

	public static ArrayList<Discount> getDiscounts(Pizza p) throws SQLException, IOException 
	{
		/* 
		 * Build an array list of all the Discounts associted with the Pizza.
		 * Discounts come from pizza_discount bridge table.
		 */

		boolean openedHere = ensureConnection();
		ArrayList<Discount> discounts = new ArrayList<Discount>();

		try {
			discounts = fetchPizzaDiscounts(conn, p.getPizzaID());
		} finally {
			if (openedHere) {
				closeConn();
			}
		}
	
		return discounts;
	}

	public static double getBaseCustPrice(String size, String crust) throws SQLException, IOException 
	{
		/* 
		 * Query the database fro the base customer price for that size and crust pizza.
		 * This comes from the baseprice table.
		 * 
		*/

		boolean openedHere = ensureConnection();
		double price = 0.0;

		try {
			String sql = "SELECT baseprice_CustPrice FROM baseprice WHERE baseprice_Size=? AND baseprice_CrustType=?";
			PreparedStatement ps = conn.prepareStatement(sql);
			ps.setString(1, size);
			ps.setString(2, crust);
			ResultSet rs = ps.executeQuery();

			if (rs.next()) {
				price = rs.getDouble(1);
			}
			rs.close();
			ps.close();
		} finally {
			if (openedHere) {
				closeConn();
			}
		}

		return price;
	}

	public static double getBaseBusPrice(String size, String crust) throws SQLException, IOException 
	{
		/* 
		 * Query the database fro the base business price for that size and crust pizza.
		 * This is the cost side of baseprice.
		 * 
		*/

		boolean openedHere = ensureConnection();
		double price = 0.0;

		try {
			String sql = "SELECT baseprice_BusPrice FROM baseprice WHERE baseprice_Size=? AND baseprice_CrustType=?";
			PreparedStatement ps = conn.prepareStatement(sql);
			ps.setString(1, size);
			ps.setString(2, crust);
			ResultSet rs = ps.executeQuery();

			if (rs.next()) {
				price = rs.getDouble(1);
			}
			rs.close();
			ps.close();
		} finally {
			if (openedHere) {
				closeConn();
			}
		}

		return price;
	}


	public static void printToppingReport() throws SQLException, IOException
	{
		/*
		 * Prints the ToppingPopularity view. Remember that this view
		 * needs to exist in your DB, so be sure you've run your createViews.sql
		 * files on your testing DB if you haven't already.
		 * 
		 * The result should be readable and sorted as indicated in the prompt.
		 * 
		 * HINT: You need to match the expected output EXACTLY....I would suggest
		 * you look at the printf method (rather that the simple print of println).
		 * It operates the same in Java as it does in C and will make your code
		 * better.
		 * 
		 */

		boolean openedHere = ensureConnection();

		try {
			// Simple dump of the ToppingPopularity view
			String sql = "SELECT * FROM ToppingPopularity";
			PreparedStatement ps = conn.prepareStatement(sql);
			ResultSet rs = ps.executeQuery();

			// Print header
			System.out.printf("%-15s%-15s%n", "Topping", "Topping Count");
			System.out.printf("%-15s%-15s%n", "-------", "-------------");

			// Print each topping row in aligned columns
			while (rs.next()) {
				System.out.printf("%-15s%-15s%n", rs.getString(1), rs.getInt(2));
			}
			rs.close();
			ps.close();
		} finally {
			if (openedHere) {
				closeConn();
			}
		}
	}
	
	public static void printProfitByPizzaReport() throws SQLException, IOException 
	{
		/*
		 * Prints the ProfitByPizza view. Remember that this view
		 * needs to exist in your DB, so be sure you've run your createViews.sql
		 * files on your testing DB if you haven't already.
		 * 
		 * The result should be readable and sorted as indicated in the prompt.
		 * 
		 * HINT: You need to match the expected output EXACTLY....I would suggest
		 * you look at the printf method (rather that the simple print of println).
		 * It operates the same in Java as it does in C and will make your code
		 * better.
		 * 
		 */

		boolean openedHere = ensureConnection();

		try {
			String sql = "SELECT * FROM ProfitByPizza";
			PreparedStatement ps = conn.prepareStatement(sql);
			ResultSet rs = ps.executeQuery();

			// Print report header
			System.out.printf("%-20s%-20s%-20s%-20s%n", "Pizza Size", "Pizza Crust", "Profit", "Last Order Date");
			System.out.printf("%-20s%-20s%-20s%-20s%n", "----------", "-----------", "------", "---------------");

			// Print each row from the view
			while (rs.next()) {
				System.out.printf("%-20s%-20s%-20s%-20s%n", rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4));
			}
			rs.close();
			ps.close();
		} finally {
			if (openedHere) {
				closeConn();
			}
		}
	}
	
	public static void printProfitByOrderTypeReport() throws SQLException, IOException
	{
		/*
		 * Prints the ProfitByOrderType view. Remember that this view
		 * needs to exist in your DB, so be sure you've run your createViews.sql
		 * files on your testing DB if you haven't already.
		 * 
		 * The result should be readable and sorted as indicated in the prompt.
		 *
		 * HINT: You need to match the expected output EXACTLY....I would suggest
		 * you look at the printf method (rather that the simple print of println).
		 * It operates the same in Java as it does in C and will make your code
		 * better.
		 * 
		 */

		boolean openedHere = ensureConnection();

		try {
			String sql = "SELECT * FROM ProfitByOrderType";
			PreparedStatement ps = conn.prepareStatement(sql);
			ResultSet rs = ps.executeQuery();

			// Print report header
			System.out.printf("%-20s%-20s%-20s%-20s%-20s%n", "Customer Type", "Order Month", "Total Order Price", "Total Order Cost", "Profit");
			System.out.printf("%-20s%-20s%-20s%-20s%-20s%n", "-------------", "-----------", "-----------------", "----------------", "------");

			// Print each row from the view
			while (rs.next()) {
				System.out.printf("%-20s%-20s%-20s%-20s%-20s%n", rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5));
			}
			rs.close();
			ps.close();
		} finally {
			if (openedHere) {
				closeConn();
			}
		}
	}
	
	
	// =========================================================
	// DATE HELPERS (string-based)
	// =========================================================
	
	/*
	 * These private methods help get the individual components of an SQL datetime object. 
	 * You're welcome to keep them or remove them....but they are usefull!
	 */
	private static int getYear(String date)// assumes date format 'YYYY-MM-DD HH:mm:ss'
	{
		return Integer.parseInt(date.substring(0,4));
	}
	private static int getMonth(String date)// assumes date format 'YYYY-MM-DD HH:mm:ss'
	{
		return Integer.parseInt(date.substring(5, 7));
	}
	private static int getDay(String date)// assumes date format 'YYYY-MM-DD HH:mm:ss'
	{
		return Integer.parseInt(date.substring(8, 10));
	}

	public static boolean checkDate(int year, int month, int day, String dateOfOrder)
	{
		// Compare year, then month, then day to see if dateOfOrder >= (year, month, day)
		if(getYear(dateOfOrder) > year)
			return true;
		else if(getYear(dateOfOrder) < year)
			return false;
		else
		{
			if(getMonth(dateOfOrder) > month)
				return true;
			else if(getMonth(dateOfOrder) < month)
				return false;
			else
			{
				if(getDay(dateOfOrder) >= day)
					return true;
				else
					return false;
			}
		}
	}

	// =========================================================
	// INTERNAL HELPERS (CONNECTION, PRICES, INVENTORY, MAPPING)
	// =========================================================

	private static Timestamp parseTimestamp(String dateStr) {
		// Safely parse a timestamp string, falling back to "now" if invalid
		try {
			return Timestamp.valueOf(dateStr);
		} catch (IllegalArgumentException e) {
			return new Timestamp(new java.util.Date().getTime());
		}
	}

	private static void closeConn() throws SQLException {
		// Centralized method to close the shared connection if open
		if (conn != null && !conn.isClosed()) {
			conn.close();
		}
	}

	private static boolean ensureConnection() throws SQLException, IOException {
		/*
		 * Helper that ensures we have an open connection.
		 * Returns:
		 *   true  - if this call opened the connection
		 *   false - if the connection was already open
		 */
		if (conn == null || conn.isClosed()) {
			return connect_to_db();
		}
		return false;
	}

	private static int insertPizza(Connection connection, Timestamp d, int orderID, Pizza p) throws SQLException {
		/*
		 * Helper to insert a pizza and its discounts and toppings.
		 * Also updates topping inventory based on size and double flags.
		 */

		// Adjust topping "double" flags to match price given (if needed)
		reconcileToppingDoubles(p);

		String insertPizza = "INSERT INTO pizza (pizza_Size, pizza_CrustType, ordertable_OrderID, pizza_PizzaState, pizza_PizzaDate, pizza_CustPrice, pizza_BusPrice) "
				+ "VALUES (?,?,?,?,?,?,?)";
		PreparedStatement ps = connection.prepareStatement(insertPizza, Statement.RETURN_GENERATED_KEYS);
		ps.setString(1, p.getSize());
		ps.setString(2, p.getCrustType());
		ps.setInt(3, orderID);
		ps.setString(4, p.getPizzaState());
		ps.setTimestamp(5, d);
		ps.setDouble(6, p.getCustPrice());
		ps.setDouble(7, p.getBusPrice());
		ps.executeUpdate();
		ResultSet keys = ps.getGeneratedKeys();
		int pizzaId = -1;
		if (keys.next()) {
			pizzaId = keys.getInt(1);
			p.setPizzaID(pizzaId);  // keep Pizza object in sync
		}
		ps.close();

		// Insert pizza-level discounts (if any)
		String insertPizzaDisc = "INSERT INTO pizza_discount (pizza_PizzaID, discount_DiscountID) VALUES (?,?)";
		if (p.getDiscounts() != null) {
			for (Discount dsc : p.getDiscounts()) {
				PreparedStatement pds = connection.prepareStatement(insertPizzaDisc);
				pds.setInt(1, pizzaId);
				pds.setInt(2, dsc.getDiscountID());
				pds.executeUpdate();
				pds.close();
			}
		}

		// Insert toppings and update inventory
		if (p.getToppings() != null) {
			String insertTop = "INSERT INTO pizza_topping (pizza_PizzaID, topping_TopID, pizza_topping_IsDouble) VALUES (?,?,?)";
			for (Topping t : p.getToppings()) {
				boolean isDouble = t.getDoubled();
				PreparedStatement pts = connection.prepareStatement(insertTop);
				pts.setInt(1, pizzaId);
				pts.setInt(2, t.getTopID());
				pts.setInt(3, isDouble ? 1 : 0);
				pts.executeUpdate();
				pts.close();

				// Each topping insertion consumes inventory units
				updateInventoryForTopping(connection, t, p.getSize(), isDouble);
			}
		}

		return pizzaId;
	}

	private static void reconcileToppingDoubles(Pizza p) throws SQLException {
		/*
		 * Tries to infer which toppings should be "double" based on the
		 * total pizza price vs. base price + topping prices.
		 */

		if (p.getToppings() == null || p.getToppings().isEmpty()) {
			return;
		}

		double baseCust;
		double baseBus;
		try {
			baseCust = getBaseCustPrice(p.getSize(), p.getCrustType());
			baseBus = getBaseBusPrice(p.getSize(), p.getCrustType());
		} catch (IOException e) {
			throw new SQLException("Failed to retrieve base prices", e);
		}
		double expectedCust = baseCust;
		double expectedBus = baseBus;

		// compute expected prices using single portions
		for (Topping t : p.getToppings()) {
			double unitsNeeded = toppingUnitsForSize(t, p.getSize());
			expectedCust += unitsNeeded * t.getCustPrice();
			expectedBus += unitsNeeded * t.getBusPrice();
		}

		// apply pizza-level discounts to expected customer price
		double expectedCustAfterDiscounts = applyPizzaDiscounts(expectedCust, p.getDiscounts());
		double deltaCust = p.getCustPrice() - expectedCustAfterDiscounts;
		double deltaBus = p.getBusPrice() - expectedBus;

		// Use extra price room to "upgrade" toppings to double if possible
		for (Topping t : p.getToppings()) {
			if (t.getDoubled()) {
				continue;
			}
			double unitsNeeded = toppingUnitsForSize(t, p.getSize());
			double extraCust = unitsNeeded * t.getCustPrice();
			double extraBus = unitsNeeded * t.getBusPrice();

			if (deltaCust + 1e-6 >= extraCust && deltaBus + 1e-6 >= extraBus) {
				t.setDoubled(true);
				deltaCust -= extraCust;
				deltaBus -= extraBus;
			}
		}
	}

	private static double applyPizzaDiscounts(double price, ArrayList<Discount> discounts) {
		/*
		 * Apply all pizza-level discounts to a base price.
		 * Percentage discounts are multiplicative; dollar discounts subtract.
		 */

		if (discounts == null) {
			return price;
		}
		double adjusted = price;
		for (Discount d : discounts) {
			if (d.isPercent()) {
				adjusted = adjusted * (1 - (d.getAmount() / 100.0));
			} else {
				adjusted = adjusted - d.getAmount();
			}
		}
		return adjusted;
	}

	private static void updateInventoryForTopping(Connection connection, Topping t, String size, boolean isDouble) throws SQLException {
		/*
		 * Decrement topping inventory based on pizza size and whether topping is double.
		 * Throws SQLException if there is not enough inventory.
		 */

		double unitsNeeded = toppingUnitsForSize(t, size);
		if (isDouble) {
			unitsNeeded *= 2;
		}
		// Round up required units to whole units
		double unitsToUse = Math.ceil(unitsNeeded);

		// Check current inventory level
		String checkSql = "SELECT topping_CurINVT FROM topping WHERE topping_TopID=?";
		PreparedStatement check = connection.prepareStatement(checkSql);
		check.setInt(1, t.getTopID());
		ResultSet r = check.executeQuery();
		if (r.next()) {
			double cur = r.getDouble(1);
			if (cur - unitsToUse < 0) {
				r.close();
				check.close();
				throw new SQLException("Not enough inventory for topping " + t.getTopName());
			}
		}
		r.close();
		check.close();

		// Actually subtract inventory
		String updateSql = "UPDATE topping SET topping_CurINVT = topping_CurINVT - ? WHERE topping_TopID=?";
		PreparedStatement ups = connection.prepareStatement(updateSql);
		ups.setDouble(1, unitsToUse);
		ups.setInt(2, t.getTopID());
		ups.executeUpdate();
		ups.close();
	}

	private static void addOrderTypeDetails(Connection connection, Order o, int orderId) throws SQLException {
		/*
		 * Insert into dinein/pickup/delivery depending on actual Order subtype.
		 */

		if (o instanceof DineinOrder) {
			DineinOrder d = (DineinOrder) o;
			PreparedStatement ps = connection.prepareStatement("INSERT INTO dinein (ordertable_OrderID, dinein_TableNum) VALUES (?,?)");
			ps.setInt(1, orderId);
			ps.setInt(2, d.getTableNum());
			ps.executeUpdate();
			ps.close();
		} else if (o instanceof PickupOrder) {
			PickupOrder p = (PickupOrder) o;
			PreparedStatement ps = connection.prepareStatement("INSERT INTO pickup (ordertable_OrderID, pickup_IsPickedUp) VALUES (?,?)");
			ps.setInt(1, orderId);
			ps.setBoolean(2, p.getIsPickedUp());
			ps.executeUpdate();
			ps.close();
		} else if (o instanceof DeliveryOrder) {
			DeliveryOrder d = (DeliveryOrder) o;
			String[] parts = parseAddress(d.getAddress());
			PreparedStatement ps = connection.prepareStatement("INSERT INTO delivery (ordertable_OrderID, delivery_HouseNum, delivery_Street, delivery_City, delivery_State, delivery_Zip, delivery_IsDelivered) "
					+ "VALUES (?,?,?,?,?,?,?)");
			ps.setInt(1, orderId);
			ps.setInt(2, Integer.parseInt(parts[0]));
			ps.setString(3, parts[1]);
			ps.setString(4, parts[2]);
			ps.setString(5, parts[3]);
			ps.setInt(6, Integer.parseInt(parts[4]));
			if (parts.length > 5) {
				ps.setBoolean(7, Boolean.parseBoolean(parts[5]));
			} else {
				ps.setBoolean(7, false);
			}
			ps.executeUpdate();
			ps.close();
		}
	}

	private static String[] parseAddress(String address) {
		/*
		 * Parses an address string into parts. First try tab-separated,
		 * then fall back to whitespace-based splitting.
		 * Ensures at least 5 parts (house, street, city, state, zip).
		 */

		String[] parts = address.split("\\t");
		if (parts.length < 5) {
			String[] fallback = address.split("\\s+");
			if (fallback.length >= 5) {
				return new String[]{fallback[0], fallback[1], fallback[2], fallback[3], fallback[4]};
			}
		}
		if (parts.length < 5) {
			// Very defensive fallback
			return new String[]{"0", "", "", "", "0"};
		}
		return parts;
	}

	private static Order buildOrderFromResult(Connection connection, ResultSet rs) throws SQLException {
		/*
		 * Builds a full Order object (correct subtype) from an ordertable row:
		 *   - DineinOrder with table
		 *   - PickupOrder with pickup status
		 *   - DeliveryOrder with address + delivery status
		 * and then attaches pizzas and discounts.
		 */

		int orderId = rs.getInt("ordertable_OrderID");
		int custId = rs.getInt("customer_CustID");
		if (rs.wasNull()) {
			custId = -1; // -1 indicates "no customer" (dine-in) per project conventions
		}
		String orderType = rs.getString("ordertable_OrderType");
		String date = rs.getString("ordertable_OrderDateTime");
		double custPrice = rs.getDouble("ordertable_CustPrice");
		double busPrice = rs.getDouble("ordertable_BusPrice");
		boolean isComplete = rs.getBoolean("ordertable_IsComplete");

		Order o;

		// Determine which child table to use based on orderType constant
		if (orderType.equals(dine_in)) {

			// Fetch table number for dine-in
			PreparedStatement ps = connection.prepareStatement("SELECT dinein_TableNum FROM dinein WHERE ordertable_OrderID=?");
			ps.setInt(1, orderId);
			ResultSet d = ps.executeQuery();
			int table = 0;
			if (d.next()) {
				table = d.getInt(1);
			}
			d.close();
			ps.close();

			o = new DineinOrder(orderId, custId, date, custPrice, busPrice, isComplete, table);

		} else if (orderType.equals(pickup)) {

			// Fetch pickup status
			PreparedStatement ps = connection.prepareStatement("SELECT pickup_IsPickedUp FROM pickup WHERE ordertable_OrderID=?");
			ps.setInt(1, orderId);
			ResultSet d = ps.executeQuery();
			boolean picked = false;
			if (d.next()) {
				picked = d.getBoolean(1);
			}
			d.close();
			ps.close();

			o = new PickupOrder(orderId, custId, date, custPrice, busPrice, picked, isComplete);

		} else {

			// Fetch full delivery address + delivered flag
			PreparedStatement ps = connection.prepareStatement("SELECT delivery_HouseNum, delivery_Street, delivery_City, delivery_State, delivery_Zip, delivery_IsDelivered "
					+ "FROM delivery WHERE ordertable_OrderID=?");
			ps.setInt(1, orderId);
			ResultSet d = ps.executeQuery();
			String addr = "";
			boolean delivered = false;
			if (d.next()) {
				addr = d.getInt(1) + "\t" + d.getString(2) + "\t" + d.getString(3) + "\t" + d.getString(4) + "\t" + d.getInt(5);
				delivered = d.getBoolean(6);
			}
			d.close();
			ps.close();

			o = new DeliveryOrder(orderId, custId, date, custPrice, busPrice, isComplete, delivered, addr);
		}

		// Attach pizzas and discounts
		o.setPizzaList(fetchPizzasForOrder(connection, orderId));
		o.setDiscountList(fetchOrderDiscounts(connection, orderId));

		return o;
	}

	private static ArrayList<Pizza> fetchPizzasForOrder(Connection connection, int orderId) throws SQLException {
		/*
		 * Helper that returns all pizzas for a given order ID.
		 * Also populates toppings and discounts for each pizza.
		 */

		ArrayList<Pizza> pizzas = new ArrayList<Pizza>();
		String sql = "SELECT * FROM pizza WHERE ordertable_OrderID=? ORDER BY pizza_PizzaID";
		PreparedStatement ps = connection.prepareStatement(sql);
		ps.setInt(1, orderId);
		ResultSet rs = ps.executeQuery();
		while (rs.next()) {
			int pizzaId = rs.getInt("pizza_PizzaID");
			String size = rs.getString("pizza_Size");
			String crust = rs.getString("pizza_CrustType");
			String state = rs.getString("pizza_PizzaState");
			String date = rs.getString("pizza_PizzaDate");
			double custPrice = rs.getDouble("pizza_CustPrice");
			double busPrice = rs.getDouble("pizza_BusPrice");

			// Construct Pizza object
			Pizza p = new Pizza(pizzaId, size, crust, orderId, state, date, custPrice, busPrice);

			// Add toppings & discounts
			p.setToppings(fetchPizzaToppings(connection, pizzaId));
			p.setDiscounts(fetchPizzaDiscounts(connection, pizzaId));

			pizzas.add(p);
		}
		rs.close();
		ps.close();
		return pizzas;
	}

	private static ArrayList<Discount> fetchOrderDiscounts(Connection connection, int orderId) throws SQLException {
		/*
		 * Helper to fetch all discounts applied at the order level.
		 */

		ArrayList<Discount> discounts = new ArrayList<Discount>();
		String sql = "SELECT d.* FROM discount d JOIN order_discount od ON d.discount_DiscountID = od.discount_DiscountID "
				+ "WHERE od.ordertable_OrderID=? ORDER BY d.discount_DiscountID";
		PreparedStatement ps = connection.prepareStatement(sql);
		ps.setInt(1, orderId);
		ResultSet rs = ps.executeQuery();
		while (rs.next()) {
			discounts.add(mapDiscount(rs));
		}
		rs.close();
		ps.close();
		return discounts;
	}

	private static ArrayList<Discount> fetchPizzaDiscounts(Connection connection, int pizzaId) throws SQLException {
		/*
		 * Helper to fetch all discounts applied at the pizza level.
		 */

		ArrayList<Discount> discounts = new ArrayList<Discount>();
		String sql = "SELECT d.* FROM discount d JOIN pizza_discount pd ON d.discount_DiscountID = pd.discount_DiscountID "
				+ "WHERE pd.pizza_PizzaID=? ORDER BY d.discount_DiscountID";
		PreparedStatement ps = connection.prepareStatement(sql);
		ps.setInt(1, pizzaId);
		ResultSet rs = ps.executeQuery();
		while (rs.next()) {
			discounts.add(mapDiscount(rs));
		}
		rs.close();
		ps.close();
		return discounts;
	}

	private static ArrayList<Topping> fetchPizzaToppings(Connection connection, int pizzaId) throws SQLException {
		/*
		 * Helper to fetch all toppings on a pizza, including IsDouble flag.
		 */

		ArrayList<Topping> tops = new ArrayList<Topping>();
		String sql = "SELECT t.*, pt.pizza_topping_IsDouble FROM topping t JOIN pizza_topping pt ON t.topping_TopID = pt.topping_TopID "
				+ "WHERE pt.pizza_PizzaID=? ORDER BY t.topping_TopName";
		PreparedStatement ps = connection.prepareStatement(sql);
		ps.setInt(1, pizzaId);
		ResultSet rs = ps.executeQuery();
		while (rs.next()) {
			Topping t = mapTopping(rs);
			t.setDoubled(rs.getInt("pizza_topping_IsDouble") == 1);
			tops.add(t);
		}
		rs.close();
		ps.close();
		return tops;
	}

	private static double toppingUnitsForSize(Topping t, String size) {
		/*
		 * Returns the topping amount used based on pizza size
		 * using the topping's per-size fields.
		 */

		if (size.equals(size_s)) {
			return t.getSmallAMT();
		} else if (size.equals(size_m)) {
			return t.getMedAMT();
		} else if (size.equals(size_l)) {
			return t.getLgAMT();
		} else {
			// default to XL size amount
			return t.getXLAMT();
		}
	}

	private static Discount mapDiscount(ResultSet rs) throws SQLException {
		/*
		 * Maps the current row of a ResultSet to a Discount object.
		 */

		return new Discount(rs.getInt("discount_DiscountID"), rs.getString("discount_DiscountName"),
				rs.getDouble("discount_Amount"), rs.getBoolean("discount_IsPercent"));
	}

	private static Topping mapTopping(ResultSet rs) throws SQLException {
		/*
		 * Maps the current row of a ResultSet to a Topping object.
		 */

		return new Topping(rs.getInt("topping_TopID"), rs.getString("topping_TopName"), rs.getDouble("topping_SmallAMT"),
				rs.getDouble("topping_MedAMT"), rs.getDouble("topping_LgAMT"), rs.getDouble("topping_XLAMT"),
				rs.getDouble("topping_CustPrice"), rs.getDouble("topping_BusPrice"), rs.getInt("topping_MinINVT"),
				rs.getInt("topping_CurINVT"));
	}

}
