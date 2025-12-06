package cpsc4620;

import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.Comparator;

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

	private static boolean connect_to_db() throws SQLException, IOException {
		/*
		 * Opens a connection via DBConnector if one does not already exist,
		 * or if the previous one was closed.
		 */
		try {
			if (conn == null || conn.isClosed()) {
				conn = DBConnector.make_connection();
			}
			return true;
		} catch (SQLException | IOException e) {
			return false;
		}
	}

	private static boolean ensureConnection() throws SQLException, IOException {
		/*
		 * Helper that ensures we have an open connection.
		 * Returns:
		 *   true  - if this call opened the connection
		 *   false - if the connection was already open
		 */
		return (conn == null || conn.isClosed()) && connect_to_db();
	}

	private static void closeConn() throws SQLException {
		if (conn != null && !conn.isClosed()) {
			conn.close();
		}
	}

	// =========================================================
	// PUBLIC API: WRITE OPERATIONS
	// =========================================================

	public static void addOrder(Order o) throws SQLException, IOException {
		/*
		 * Add the order to the DB: ordertable, child table (delivery/dinein/pickup),
		 * pizzas, toppings, and discounts (order + pizza).
		 *
		 * Dine-in orders have no customer, so custID <= 0 => NULL.
		 */

		boolean openedHere = ensureConnection();

		try {
			conn.setAutoCommit(false);

			// ----------------------------------------------------
			// 1. Insert into ordertable
			// ----------------------------------------------------
			String insertOrder =
					"INSERT INTO ordertable (customer_CustID, ordertable_OrderType, ordertable_OrderDateTime, " +
							"ordertable_CustPrice, ordertable_BusPrice, ordertable_IsComplete) VALUES (?,?,?,?,?,?)";

			int orderId = -1;
			try (PreparedStatement os = conn.prepareStatement(insertOrder, Statement.RETURN_GENERATED_KEYS)) {

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

				try (ResultSet keys = os.getGeneratedKeys()) {
					if (keys.next()) {
						orderId = keys.getInt(1);
						o.setOrderID(orderId); // keep object in sync with DB
					}
				}
			}

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
				String orderDiscSql =
						"INSERT INTO order_discount (ordertable_OrderID, discount_DiscountID) VALUES (?,?)";
				for (Discount d : o.getDiscountList()) {
					try (PreparedStatement ods = conn.prepareStatement(orderDiscSql)) {
						ods.setInt(1, orderId);
						ods.setInt(2, d.getDiscountID());
						ods.executeUpdate();
					}
				}
			}

			conn.commit();
		} catch (SQLException e) {
			conn.rollback();
			throw e;
		} finally {
			// leave autocommit as-is, just like your version
			if (openedHere) {
				closeConn();
			}
		}
	}

	public static int addPizza(java.util.Date d, int orderID, Pizza p) throws SQLException, IOException {
		/*
		 * Insert the pizza into the database.
		 * Also insert pizza discounts and toppings.
		 *
		 * NOTE: Date d ensures all pizzas for an order share the same timestamp.
		 * Returns the id of the pizza just added.
		 */

		boolean openedHere = ensureConnection();
		int pizzaId;

		try {
			conn.setAutoCommit(false);

			pizzaId = insertPizza(conn, new Timestamp(d.getTime()), orderID, p);

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

	public static int addCustomer(Customer c) throws SQLException, IOException {
		/*
		 * Adds a new customer to the database.
		 */

		boolean openedHere = ensureConnection();
		int custId = -1;

		try {
			String sql =
					"INSERT INTO customer (customer_FName, customer_LName, customer_PhoneNum) VALUES (?,?,?)";
			try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
				ps.setString(1, c.getFName());
				ps.setString(2, c.getLName());
				ps.setString(3, c.getPhone());
				ps.executeUpdate();

				try (ResultSet keys = ps.getGeneratedKeys()) {
					if (keys.next()) {
						custId = keys.getInt(1);
					}
				}
			}
		} finally {
			if (openedHere) {
				closeConn();
			}
		}

		return custId;
	}

	public static void completeOrder(int OrderID, order_state newState) throws SQLException, IOException {
		/*
		 * Mark an order as complete or delivered/picked up.
		 *
		 * PREPARED  -> mark order and all pizzas as completed
		 * DELIVERED -> mark delivery record as delivered
		 * PICKEDUP  -> mark pickup record as picked up
		 */

		boolean openedHere = ensureConnection();

		try {
			switch (newState) {
				case PREPARED:
					try (PreparedStatement ps =
							     conn.prepareStatement(
									     "UPDATE ordertable SET ordertable_IsComplete=1 WHERE ordertable_OrderID=?");
					     PreparedStatement ps2 =
							     conn.prepareStatement(
									     "UPDATE pizza SET pizza_PizzaState='completed' WHERE ordertable_OrderID=?")) {
						ps.setInt(1, OrderID);
						ps.executeUpdate();

						ps2.setInt(1, OrderID);
						ps2.executeUpdate();
					}
					break;

				case DELIVERED:
					try (PreparedStatement ps =
							     conn.prepareStatement(
									     "UPDATE delivery SET delivery_IsDelivered=1 WHERE ordertable_OrderID=?")) {
						ps.setInt(1, OrderID);
						ps.executeUpdate();
					}
					break;

				case PICKEDUP:
					try (PreparedStatement ps =
							     conn.prepareStatement(
									     "UPDATE pickup SET pickup_IsPickedUp=1 WHERE ordertable_OrderID=?")) {
						ps.setInt(1, OrderID);
						ps.executeUpdate();
					}
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

	public static ArrayList<Order> getOrders(int status) throws SQLException, IOException {
		/*
		 * Return an ArrayList of orders.
		 * 	status == 1 => open orders (IsComplete = 0)
		 * 	        == 2 => completed orders (IsComplete = 1)
		 * 	        == 3 => all orders
		 *
		 * Fully populates each Order: discounts + pizzas + pizza toppings/discounts.
		 */

		boolean openedHere = ensureConnection();
		ArrayList<Order> orders = new ArrayList<>();

		try {
			StringBuilder sql = new StringBuilder("SELECT * FROM ordertable");

			if (status == 1) {
				sql.append(" WHERE ordertable_IsComplete=0");
			} else if (status == 2) {
				sql.append(" WHERE ordertable_IsComplete=1");
			}
			sql.append(" ORDER BY ordertable_OrderID ASC");

			try (PreparedStatement ps = conn.prepareStatement(sql.toString());
			     ResultSet rs = ps.executeQuery()) {

				while (rs.next()) {
					orders.add(buildOrderFromResult(conn, rs));
				}
			}

			orders.sort(Comparator.comparingInt(Order::getOrderID));
		} finally {
			if (openedHere) {
				closeConn();
			}
		}

		return orders;
	}

	public static Order getLastOrder() throws SQLException, IOException {
		/*
		 * Query the database for the LAST order added
		 * then return an Order object for that order.
		 * There will always be a "last order".
		 */

		boolean openedHere = ensureConnection();
		Order order = null;

		try {
			String sql = "SELECT * FROM ordertable ORDER BY ordertable_OrderID DESC LIMIT 1";
			try (PreparedStatement ps = conn.prepareStatement(sql);
			     ResultSet rs = ps.executeQuery()) {

				if (rs.next()) {
					order = buildOrderFromResult(conn, rs);
				}
			}
		} finally {
			if (openedHere) {
				closeConn();
			}
		}

		return order;
	}

	public static ArrayList<Order> getOrdersByDate(String date) throws SQLException, IOException {
		/*
		 * Query the database for ALL the orders placed on a specific date
		 * (YYYY-MM-DD) and return a list of those orders.
		 */

		boolean openedHere = ensureConnection();
		ArrayList<Order> orders = new ArrayList<>();

		try {
			String sql =
					"SELECT * FROM ordertable WHERE DATE(ordertable_OrderDateTime)=? ORDER BY ordertable_OrderDateTime ASC";
			try (PreparedStatement ps = conn.prepareStatement(sql)) {
				ps.setString(1, date);

				try (ResultSet rs = ps.executeQuery()) {
					while (rs.next()) {
						orders.add(buildOrderFromResult(conn, rs));
					}
				}
			}
		} finally {
			if (openedHere) {
				closeConn();
			}
		}

		return orders;
	}

	public static ArrayList<Discount> getDiscountList() throws SQLException, IOException {
		/*
		 * Query the database for all the available discounts and
		 * return them ordered by discount name.
		 */

		boolean openedHere = ensureConnection();
		ArrayList<Discount> discounts = new ArrayList<>();

		try {
			String sql = "SELECT * FROM discount ORDER BY discount_DiscountName ASC";
			try (PreparedStatement ps = conn.prepareStatement(sql);
			     ResultSet rs = ps.executeQuery()) {

				while (rs.next()) {
					discounts.add(mapDiscount(rs));
				}
			}
		} finally {
			if (openedHere) {
				closeConn();
			}
		}

		return discounts;
	}

	public static Discount findDiscountByName(String name) throws SQLException, IOException {
		/*
		 * Query the database for a discount using its name.
		 * If found, return a Discount object; otherwise return null.
		 */

		boolean openedHere = ensureConnection();
		Discount d = null;

		try {
			String sql = "SELECT * FROM discount WHERE discount_DiscountName=?";
			try (PreparedStatement ps = conn.prepareStatement(sql)) {
				ps.setString(1, name);

				try (ResultSet rs = ps.executeQuery()) {
					if (rs.next()) {
						d = mapDiscount(rs);
					}
				}
			}
		} finally {
			if (openedHere) {
				closeConn();
			}
		}

		return d;
	}

	public static ArrayList<Customer> getCustomerList() throws SQLException, IOException {
		/*
		 * Query the DB for all customers and return them ordered by:
		 * last name, first name, then phone.
		 */

		boolean openedHere = ensureConnection();
		ArrayList<Customer> customers = new ArrayList<>();

		try {
			String sql =
					"SELECT * FROM customer ORDER BY customer_LName, customer_FName, customer_PhoneNum";
			try (PreparedStatement ps = conn.prepareStatement(sql);
			     ResultSet rs = ps.executeQuery()) {

				while (rs.next()) {
					customers.add(new Customer(
							rs.getInt("customer_CustID"),
							rs.getString("customer_FName"),
							rs.getString("customer_LName"),
							rs.getString("customer_PhoneNum")));
				}
			}
		} finally {
			if (openedHere) {
				closeConn();
			}
		}

		return customers;
	}

	public static Customer findCustomerByPhone(String phoneNumber) throws SQLException, IOException {
		/*
		 * Query the database for a customer using a phone number.
		 * If found, return a Customer object; otherwise return null.
		 */

		boolean openedHere = ensureConnection();
		Customer c = null;

		try {
			String sql = "SELECT * FROM customer WHERE customer_PhoneNum=?";
			try (PreparedStatement ps = conn.prepareStatement(sql)) {
				ps.setString(1, phoneNumber);

				try (ResultSet rs = ps.executeQuery()) {
					if (rs.next()) {
						c = new Customer(
								rs.getInt("customer_CustID"),
								rs.getString("customer_FName"),
								rs.getString("customer_LName"),
								rs.getString("customer_PhoneNum"));
					}
				}
			}
		} finally {
			if (openedHere) {
				closeConn();
			}
		}

		return c;
	}

	public static String getCustomerName(int CustID) throws SQLException, IOException {
		/*
		 * Helper method to fetch and format the name of a customer
		 * based on a customer ID.
		 */

		boolean openedHere = ensureConnection();
		String name = "";

		String sql =
				"SELECT customer_FName, customer_LName FROM customer WHERE customer_CustID=?";
		try (PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setInt(1, CustID);

			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					name = rs.getString("customer_FName") + " " +
							rs.getString("customer_LName");
				}
			}
		} finally {
			if (openedHere) {
				closeConn();
			}
		}

		return name;
	}

	public static ArrayList<Topping> getToppingList() throws SQLException, IOException {
		/*
		 * Query the database for the available toppings and
		 * return an ArrayList of all the toppings ordered by name.
		 */

		boolean openedHere = ensureConnection();
		ArrayList<Topping> tops = new ArrayList<>();

		try {
			String sql = "SELECT * FROM topping ORDER BY topping_TopName";
			try (PreparedStatement ps = conn.prepareStatement(sql);
			     ResultSet rs = ps.executeQuery()) {

				while (rs.next()) {
					tops.add(mapTopping(rs));
				}
			}
		} finally {
			if (openedHere) {
				closeConn();
			}
		}

		return tops;
	}

	public static Topping findToppingByName(String name) throws SQLException, IOException {
		/*
		 * Query the database for a topping using its name.
		 * If found, return a Topping object; otherwise return null.
		 */

		boolean openedHere = ensureConnection();
		Topping t = null;

		try {
			String sql = "SELECT * FROM topping WHERE topping_TopName=?";
			try (PreparedStatement ps = conn.prepareStatement(sql)) {
				ps.setString(1, name);

				try (ResultSet rs = ps.executeQuery()) {
					if (rs.next()) {
						t = mapTopping(rs);
					}
				}
			}
		} finally {
			if (openedHere) {
				closeConn();
			}
		}

		return t;
	}

	public static ArrayList<Topping> getToppingsOnPizza(Pizza p) throws SQLException, IOException {
		/*
		 * Builds an ArrayList of the toppings ON a pizza.
		 */

		boolean openedHere = ensureConnection();
		ArrayList<Topping> tops;

		try {
			tops = fetchPizzaToppings(conn, p.getPizzaID());
		} finally {
			if (openedHere) {
				closeConn();
			}
		}

		return tops;
	}

	public static void addToInventory(int toppingID, double quantity) throws SQLException, IOException {
		/*
		 * Updates the quantity of the topping in the database by the amount specified.
		 * Positive quantity increases inventory.
		 */

		boolean openedHere = ensureConnection();

		try {
			String sql =
					"UPDATE topping SET topping_CurINVT = topping_CurINVT + ? WHERE topping_TopID=?";
			try (PreparedStatement ps = conn.prepareStatement(sql)) {
				ps.setDouble(1, quantity);
				ps.setInt(2, toppingID);
				ps.executeUpdate();
			}
		} finally {
			if (openedHere) {
				closeConn();
			}
		}
	}

	public static ArrayList<Pizza> getPizzas(Order o) throws SQLException, IOException {
		/*
		 * Build an ArrayList of all the Pizzas associated with the Order.
		 * Each Pizza object is fully populated (toppings + discounts).
		 */

		boolean openedHere = ensureConnection();
		ArrayList<Pizza> pizzas;

		try {
			pizzas = fetchPizzasForOrder(conn, o.getOrderID());
		} finally {
			if (openedHere) {
				closeConn();
			}
		}

		return pizzas;
	}

	public static ArrayList<Discount> getDiscounts(Order o) throws SQLException, IOException {
		/*
		 * Build an ArrayList of all the Discounts associated with the Order.
		 */

		boolean openedHere = ensureConnection();
		ArrayList<Discount> discounts;

		try {
			discounts = fetchOrderDiscounts(conn, o.getOrderID());
		} finally {
			if (openedHere) {
				closeConn();
			}
		}

		return discounts;
	}

	public static ArrayList<Discount> getDiscounts(Pizza p) throws SQLException, IOException {
		/*
		 * Build an ArrayList of all the Discounts associated with the Pizza.
		 */

		boolean openedHere = ensureConnection();
		ArrayList<Discount> discounts;

		try {
			discounts = fetchPizzaDiscounts(conn, p.getPizzaID());
		} finally {
			if (openedHere) {
				closeConn();
			}
		}

		return discounts;
	}

	public static double getBaseCustPrice(String size, String crust) throws SQLException, IOException {
		/*
		 * Query the database for the base customer price for that size and crust.
		 */

		boolean openedHere = ensureConnection();
		double price = 0.0;

		try {
			String sql =
					"SELECT baseprice_CustPrice FROM baseprice WHERE baseprice_Size=? AND baseprice_CrustType=?";
			try (PreparedStatement ps = conn.prepareStatement(sql)) {
				ps.setString(1, size);
				ps.setString(2, crust);

				try (ResultSet rs = ps.executeQuery()) {
					if (rs.next()) {
						price = rs.getDouble(1);
					}
				}
			}
		} finally {
			if (openedHere) {
				closeConn();
			}
		}

		return price;
	}

	public static double getBaseBusPrice(String size, String crust) throws SQLException, IOException {
		/*
		 * Query the database for the base business price for that size and crust.
		 */

		boolean openedHere = ensureConnection();
		double price = 0.0;

		try {
			String sql =
					"SELECT baseprice_BusPrice FROM baseprice WHERE baseprice_Size=? AND baseprice_CrustType=?";
			try (PreparedStatement ps = conn.prepareStatement(sql)) {
				ps.setString(1, size);
				ps.setString(2, crust);

				try (ResultSet rs = ps.executeQuery()) {
					if (rs.next()) {
						price = rs.getDouble(1);
					}
				}
			}
		} finally {
			if (openedHere) {
				closeConn();
			}
		}

		return price;
	}

	public static void printToppingReport() throws SQLException, IOException {
		/*
		 * Prints the ToppingPopularity view.
		 */

		boolean openedHere = ensureConnection();

		try {
			String sql = "SELECT * FROM ToppingPopularity";
			try (PreparedStatement ps = conn.prepareStatement(sql);
			     ResultSet rs = ps.executeQuery()) {

				System.out.printf("%-15s%-15s%n", "Topping", "Topping Count");
				System.out.printf("%-15s%-15s%n", "-------", "-------------");

				while (rs.next()) {
					System.out.printf("%-15s%-15d%n",
							rs.getString(1),
							rs.getInt(2));
				}
			}
		} finally {
			if (openedHere) {
				closeConn();
			}
		}
	}

	public static void printProfitByPizzaReport() throws SQLException, IOException {
		/*
		 * Prints the ProfitByPizza view.
		 */

		boolean openedHere = ensureConnection();

		try {
			String sql = "SELECT * FROM ProfitByPizza";
			try (PreparedStatement ps = conn.prepareStatement(sql);
			     ResultSet rs = ps.executeQuery()) {

				System.out.printf("%-20s%-20s%-20s%-20s%n",
						"Pizza Size", "Pizza Crust", "Profit", "Last Order Date");
				System.out.printf("%-20s%-20s%-20s%-20s%n",
						"----------", "-----------", "------", "---------------");

				while (rs.next()) {
					System.out.printf("%-20s%-20s%-20s%-20s%n",
							rs.getString(1),
							rs.getString(2),
							rs.getString(3),
							rs.getString(4));
				}
			}
		} finally {
			if (openedHere) {
				closeConn();
			}
		}
	}

	public static void printProfitByOrderTypeReport() throws SQLException, IOException {
		/*
		 * Prints the ProfitByOrderType view.
		 */

		boolean openedHere = ensureConnection();

		try {
			String sql = "SELECT * FROM ProfitByOrderType";
			try (PreparedStatement ps = conn.prepareStatement(sql);
			     ResultSet rs = ps.executeQuery()) {

				System.out.printf("%-20s%-20s%-20s%-20s%-20s%n",
						"Customer Type", "Order Month",
						"Total Order Price", "Total Order Cost", "Profit");
				System.out.printf("%-20s%-20s%-20s%-20s%-20s%n",
						"-------------", "-----------",
						"-----------------", "----------------", "------");

				while (rs.next()) {
					System.out.printf("%-20s%-20s%-20s%-20s%-20s%n",
							rs.getString(1),
							rs.getString(2),
							rs.getString(3),
							rs.getString(4),
							rs.getString(5));
				}
			}
		} finally {
			if (openedHere) {
				closeConn();
			}
		}
	}

	// =========================================================
	// DATE HELPERS (string-based)
	// =========================================================

	private static int getYear(String date) { // 'YYYY-MM-DD HH:mm:ss'
		return Integer.parseInt(date.substring(0, 4));
	}

	private static int getMonth(String date) { // 'YYYY-MM-DD HH:mm:ss'
		return Integer.parseInt(date.substring(5, 7));
	}

	private static int getDay(String date) { // 'YYYY-MM-DD HH:mm:ss'
		return Integer.parseInt(date.substring(8, 10));
	}

	public static boolean checkDate(int year, int month, int day, String dateOfOrder) {
		// Returns true if dateOfOrder >= (year, month, day)
		int y = getYear(dateOfOrder);
		if (y != year) return y > year;

		int m = getMonth(dateOfOrder);
		if (m != month) return m > month;

		int d = getDay(dateOfOrder);
		return d >= day;
	}

	// =========================================================
	// INTERNAL HELPERS (TIMESTAMP, PIZZA INSERT, INVENTORY, MAPPING)
	// =========================================================

	private static Timestamp parseTimestamp(String dateStr) {
		// Safely parse a timestamp string, falling back to "now" if invalid
		try {
			return Timestamp.valueOf(dateStr);
		} catch (IllegalArgumentException e) {
			return new Timestamp(new java.util.Date().getTime());
		}
	}

	private static int insertPizza(Connection connection, Timestamp d, int orderID, Pizza p) throws SQLException {
		/*
		 * Helper to insert a pizza and its discounts and toppings.
		 * Also updates topping inventory based on size and double flags.
		 */

		// Adjust topping "double" flags to match price given (if needed)
		reconcileToppingDoubles(p);

		String insertPizza =
				"INSERT INTO pizza (pizza_Size, pizza_CrustType, ordertable_OrderID, pizza_PizzaState, " +
						"pizza_PizzaDate, pizza_CustPrice, pizza_BusPrice) VALUES (?,?,?,?,?,?,?)";

		int pizzaId = -1;
		try (PreparedStatement ps = connection.prepareStatement(insertPizza, Statement.RETURN_GENERATED_KEYS)) {
			ps.setString(1, p.getSize());
			ps.setString(2, p.getCrustType());
			ps.setInt(3, orderID);
			ps.setString(4, p.getPizzaState());
			ps.setTimestamp(5, d);
			ps.setDouble(6, p.getCustPrice());
			ps.setDouble(7, p.getBusPrice());
			ps.executeUpdate();

			try (ResultSet keys = ps.getGeneratedKeys()) {
				if (keys.next()) {
					pizzaId = keys.getInt(1);
					p.setPizzaID(pizzaId); // keep Pizza object in sync
				}
			}
		}

		// pizza-level discounts
		if (p.getDiscounts() != null) {
			String insertPizzaDisc =
					"INSERT INTO pizza_discount (pizza_PizzaID, discount_DiscountID) VALUES (?,?)";
			for (Discount dsc : p.getDiscounts()) {
				try (PreparedStatement pds = connection.prepareStatement(insertPizzaDisc)) {
					pds.setInt(1, pizzaId);
					pds.setInt(2, dsc.getDiscountID());
					pds.executeUpdate();
				}
			}
		}

		// toppings + inventory
		if (p.getToppings() != null) {
			String insertTop =
					"INSERT INTO pizza_topping (pizza_PizzaID, topping_TopID, pizza_topping_IsDouble) VALUES (?,?,?)";
			for (Topping t : p.getToppings()) {
				boolean isDouble = t.getDoubled();

				try (PreparedStatement pts = connection.prepareStatement(insertTop)) {
					pts.setInt(1, pizzaId);
					pts.setInt(2, t.getTopID());
					pts.setInt(3, isDouble ? 1 : 0);
					pts.executeUpdate();
				}

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

		for (Topping t : p.getToppings()) {
			double unitsNeeded = toppingUnitsForSize(t, p.getSize());
			expectedCust += unitsNeeded * t.getCustPrice();
			expectedBus += unitsNeeded * t.getBusPrice();
		}

		double expectedCustAfterDiscounts =
				applyPizzaDiscounts(expectedCust, p.getDiscounts());
		double deltaCust = p.getCustPrice() - expectedCustAfterDiscounts;
		double deltaBus = p.getBusPrice() - expectedBus;

		for (Topping t : p.getToppings()) {
			if (t.getDoubled()) continue;

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

		if (discounts == null) return price;

		double adjusted = price;
		for (Discount d : discounts) {
			if (d.isPercent()) {
				adjusted *= (1 - (d.getAmount() / 100.0));
			} else {
				adjusted -= d.getAmount();
			}
		}
		return adjusted;
	}

	private static void updateInventoryForTopping(Connection connection, Topping t, String size,
	                                              boolean isDouble) throws SQLException {
		/*
		 * Decrement topping inventory based on pizza size and whether topping is double.
		 * Throws SQLException if there is not enough inventory.
		 */

		double unitsNeeded = toppingUnitsForSize(t, size);
		if (isDouble) unitsNeeded *= 2;

		double unitsToUse = Math.ceil(unitsNeeded);

		String checkSql = "SELECT topping_CurINVT FROM topping WHERE topping_TopID=?";
		try (PreparedStatement check = connection.prepareStatement(checkSql)) {
			check.setInt(1, t.getTopID());
			try (ResultSet r = check.executeQuery()) {
				if (r.next()) {
					double cur = r.getDouble(1);
					if (cur - unitsToUse < 0) {
						throw new SQLException("Not enough inventory for topping " + t.getTopName());
					}
				}
			}
		}

		String updateSql =
				"UPDATE topping SET topping_CurINVT = topping_CurINVT - ? WHERE topping_TopID=?";
		try (PreparedStatement ups = connection.prepareStatement(updateSql)) {
			ups.setDouble(1, unitsToUse);
			ups.setInt(2, t.getTopID());
			ups.executeUpdate();
		}
	}

	private static void addOrderTypeDetails(Connection connection, Order o, int orderId) throws SQLException {
		/*
		 * Insert into dinein/pickup/delivery depending on actual Order subtype.
		 */

		if (o instanceof DineinOrder) {
			DineinOrder d = (DineinOrder) o;
			String sql = "INSERT INTO dinein (ordertable_OrderID, dinein_TableNum) VALUES (?,?)";
			try (PreparedStatement ps = connection.prepareStatement(sql)) {
				ps.setInt(1, orderId);
				ps.setInt(2, d.getTableNum());
				ps.executeUpdate();
			}
		} else if (o instanceof PickupOrder) {
			PickupOrder p = (PickupOrder) o;
			String sql =
					"INSERT INTO pickup (ordertable_OrderID, pickup_IsPickedUp) VALUES (?,?)";
			try (PreparedStatement ps = connection.prepareStatement(sql)) {
				ps.setInt(1, orderId);
				ps.setBoolean(2, p.getIsPickedUp());
				ps.executeUpdate();
			}
		} else if (o instanceof DeliveryOrder) {
			DeliveryOrder d = (DeliveryOrder) o;
			String[] parts = parseAddress(d.getAddress());

			String sql =
					"INSERT INTO delivery (ordertable_OrderID, delivery_HouseNum, delivery_Street, " +
							"delivery_City, delivery_State, delivery_Zip, delivery_IsDelivered) " +
							"VALUES (?,?,?,?,?,?,?)";
			try (PreparedStatement ps = connection.prepareStatement(sql)) {
				ps.setInt(1, orderId);
				ps.setInt(2, Integer.parseInt(parts[0]));
				ps.setString(3, parts[1]);
				ps.setString(4, parts[2]);
				ps.setString(5, parts[3]);
				ps.setInt(6, Integer.parseInt(parts[4]));
				ps.setBoolean(7, parts.length > 5 && Boolean.parseBoolean(parts[5]));
				ps.executeUpdate();
			}
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
			custId = -1; // -1 indicates "no customer" (dine-in)
		}
		String orderType = rs.getString("ordertable_OrderType");
		String date = rs.getString("ordertable_OrderDateTime");
		double custPrice = rs.getDouble("ordertable_CustPrice");
		double busPrice = rs.getDouble("ordertable_BusPrice");
		boolean isComplete = rs.getBoolean("ordertable_IsComplete");

		Order o;

		if (orderType.equals(dine_in)) {
			String sql = "SELECT dinein_TableNum FROM dinein WHERE ordertable_OrderID=?";
			int table = 0;
			try (PreparedStatement ps = connection.prepareStatement(sql)) {
				ps.setInt(1, orderId);
				try (ResultSet d = ps.executeQuery()) {
					if (d.next()) {
						table = d.getInt(1);
					}
				}
			}
			o = new DineinOrder(orderId, custId, date, custPrice, busPrice, isComplete, table);

		} else if (orderType.equals(pickup)) {
			String sql =
					"SELECT pickup_IsPickedUp FROM pickup WHERE ordertable_OrderID=?";
			boolean picked = false;
			try (PreparedStatement ps = connection.prepareStatement(sql)) {
				ps.setInt(1, orderId);
				try (ResultSet d = ps.executeQuery()) {
					if (d.next()) {
						picked = d.getBoolean(1);
					}
				}
			}
			o = new PickupOrder(orderId, custId, date, custPrice, busPrice, picked, isComplete);

		} else {
			String sql =
					"SELECT delivery_HouseNum, delivery_Street, delivery_City, delivery_State, " +
							"delivery_Zip, delivery_IsDelivered FROM delivery WHERE ordertable_OrderID=?";
			String addr = "";
			boolean delivered = false;
			try (PreparedStatement ps = connection.prepareStatement(sql)) {
				ps.setInt(1, orderId);
				try (ResultSet d = ps.executeQuery()) {
					if (d.next()) {
						addr = d.getInt(1) + "\t" +
								d.getString(2) + "\t" +
								d.getString(3) + "\t" +
								d.getString(4) + "\t" +
								d.getInt(5);
						delivered = d.getBoolean(6);
					}
				}
			}
			o = new DeliveryOrder(orderId, custId, date, custPrice, busPrice, isComplete, delivered, addr);
		}

		o.setPizzaList(fetchPizzasForOrder(connection, orderId));
		o.setDiscountList(fetchOrderDiscounts(connection, orderId));

		return o;
	}

	private static ArrayList<Pizza> fetchPizzasForOrder(Connection connection, int orderId) throws SQLException {
		/*
		 * Helper that returns all pizzas for a given order ID.
		 * Also populates toppings and discounts for each pizza.
		 */

		ArrayList<Pizza> pizzas = new ArrayList<>();
		String sql = "SELECT * FROM pizza WHERE ordertable_OrderID=? ORDER BY pizza_PizzaID";
		try (PreparedStatement ps = connection.prepareStatement(sql)) {
			ps.setInt(1, orderId);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					int pizzaId = rs.getInt("pizza_PizzaID");
					String size = rs.getString("pizza_Size");
					String crust = rs.getString("pizza_CrustType");
					String state = rs.getString("pizza_PizzaState");
					String date = rs.getString("pizza_PizzaDate");
					double custPrice = rs.getDouble("pizza_CustPrice");
					double busPrice = rs.getDouble("pizza_BusPrice");

					Pizza p = new Pizza(pizzaId, size, crust, orderId, state, date, custPrice, busPrice);
					p.setToppings(fetchPizzaToppings(connection, pizzaId));
					p.setDiscounts(fetchPizzaDiscounts(connection, pizzaId));

					pizzas.add(p);
				}
			}
		}
		return pizzas;
	}

	private static ArrayList<Discount> fetchOrderDiscounts(Connection connection, int orderId) throws SQLException {
		/*
		 * Helper to fetch all discounts applied at the order level.
		 */

		ArrayList<Discount> discounts = new ArrayList<>();
		String sql =
				"SELECT d.* FROM discount d " +
						"JOIN order_discount od ON d.discount_DiscountID = od.discount_DiscountID " +
						"WHERE od.ordertable_OrderID=? ORDER BY d.discount_DiscountID";
		try (PreparedStatement ps = connection.prepareStatement(sql)) {
			ps.setInt(1, orderId);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					discounts.add(mapDiscount(rs));
				}
			}
		}
		return discounts;
	}

	private static ArrayList<Discount> fetchPizzaDiscounts(Connection connection, int pizzaId) throws SQLException {
		/*
		 * Helper to fetch all discounts applied at the pizza level.
		 */

		ArrayList<Discount> discounts = new ArrayList<>();
		String sql =
				"SELECT d.* FROM discount d " +
						"JOIN pizza_discount pd ON d.discount_DiscountID = pd.discount_DiscountID " +
						"WHERE pd.pizza_PizzaID=? ORDER BY d.discount_DiscountID";
		try (PreparedStatement ps = connection.prepareStatement(sql)) {
			ps.setInt(1, pizzaId);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					discounts.add(mapDiscount(rs));
				}
			}
		}
		return discounts;
	}

	private static ArrayList<Topping> fetchPizzaToppings(Connection connection, int pizzaId) throws SQLException {
		/*
		 * Helper to fetch all toppings on a pizza, including IsDouble flag.
		 */

		ArrayList<Topping> tops = new ArrayList<>();
		String sql =
				"SELECT t.*, pt.pizza_topping_IsDouble " +
						"FROM topping t " +
						"JOIN pizza_topping pt ON t.topping_TopID = pt.topping_TopID " +
						"WHERE pt.pizza_PizzaID=? ORDER BY t.topping_TopName";
		try (PreparedStatement ps = connection.prepareStatement(sql)) {
			ps.setInt(1, pizzaId);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					Topping t = mapTopping(rs);
					t.setDoubled(rs.getInt("pizza_topping_IsDouble") == 1);
					tops.add(t);
				}
			}
		}
		return tops;
	}

	private static double toppingUnitsForSize(Topping t, String size) {
		/*
		 * Returns the topping amount used based on pizza size.
		 */

		switch (size) {
			case size_s: return t.getSmallAMT();
			case size_m: return t.getMedAMT();
			case size_l: return t.getLgAMT();
			default:     return t.getXLAMT();
		}
	}

	private static Discount mapDiscount(ResultSet rs) throws SQLException {
		/*
		 * Maps the current row of a ResultSet to a Discount object.
		 */
		return new Discount(
				rs.getInt("discount_DiscountID"),
				rs.getString("discount_DiscountName"),
				rs.getDouble("discount_Amount"),
				rs.getBoolean("discount_IsPercent"));
	}

	private static Topping mapTopping(ResultSet rs) throws SQLException {
		/*
		 * Maps the current row of a ResultSet to a Topping object.
		 */
		return new Topping(
				rs.getInt("topping_TopID"),
				rs.getString("topping_TopName"),
				rs.getDouble("topping_SmallAMT"),
				rs.getDouble("topping_MedAMT"),
				rs.getDouble("topping_LgAMT"),
				rs.getDouble("topping_XLAMT"),
				rs.getDouble("topping_CustPrice"),
				rs.getDouble("topping_BusPrice"),
				rs.getInt("topping_MinINVT"),
				rs.getInt("topping_CurINVT"));
	}
}
