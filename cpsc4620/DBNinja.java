package cpsc4620;

import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.Comparator;

/*
 * All DB logic for the pizza app flows through this class.
 * DO NOT change any of the public method signatures or constants.
 */
public final class DBNinja {
	private static Connection conn;

	// DO NOT change these constants
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

	public enum order_state { PREPARED, DELIVERED, PICKEDUP }

	private static boolean connect_to_db() throws SQLException, IOException {
		try {
			if (conn == null || conn.isClosed()) conn = DBConnector.make_connection();
			return true;
		} catch (SQLException | IOException e) {
			return false;
		}
	}

	// =========================================
	// WRITE OPERATIONS
	// =========================================
	public static void addOrder(Order o) throws SQLException, IOException {
		boolean openedHere = ensureConnection();
		try {
			conn.setAutoCommit(false);
			int orderId = -1;
			String insertOrder = "INSERT INTO ordertable (customer_CustID, ordertable_OrderType, ordertable_OrderDateTime, " +
					"ordertable_CustPrice, ordertable_BusPrice, ordertable_IsComplete) VALUES (?,?,?,?,?,?)";
			try (PreparedStatement os = conn.prepareStatement(insertOrder, Statement.RETURN_GENERATED_KEYS)) {
				if (o.getCustID() <= 0) os.setNull(1, Types.INTEGER); else os.setInt(1, o.getCustID());
				os.setString(2, o.getOrderType());
				os.setTimestamp(3, parseTimestamp(o.getDate()));
				os.setDouble(4, o.getCustPrice());
				os.setDouble(5, o.getBusPrice());
				os.setBoolean(6, o.getIsComplete());
				os.executeUpdate();
				try (ResultSet keys = os.getGeneratedKeys()) {
					if (keys.next()) {
						orderId = keys.getInt(1);
						o.setOrderID(orderId);
					}
				}
			}
			addOrderTypeDetails(conn, o, orderId);

			Timestamp orderDts = parseTimestamp(o.getDate());
			for (Pizza p : o.getPizzaList()) {
				p.setOrderID(orderId);
				insertPizza(conn, orderDts, orderId, p);
			}

			if (o.getDiscountList() != null) {
				String orderDiscSql = "INSERT INTO order_discount (ordertable_OrderID, discount_DiscountID) VALUES (?,?)";
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
			if (openedHere) closeConn();
		}
	}

	public static int addPizza(java.util.Date d, int orderID, Pizza p) throws SQLException, IOException {
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
			if (openedHere) closeConn();
		}
		return pizzaId;
	}

	public static int addCustomer(Customer c) throws SQLException, IOException {
		boolean openedHere = ensureConnection();
		int custId = -1;
		String sql = "INSERT INTO customer (customer_FName, customer_LName, customer_PhoneNum) VALUES (?,?,?)";
		try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
			ps.setString(1, c.getFName());
			ps.setString(2, c.getLName());
			ps.setString(3, c.getPhone());
			ps.executeUpdate();
			try (ResultSet keys = ps.getGeneratedKeys()) {
				if (keys.next()) custId = keys.getInt(1);
			}
		} finally {
			if (openedHere) closeConn();
		}
		return custId;
	}

	public static void completeOrder(int OrderID, order_state newState) throws SQLException, IOException {
		boolean openedHere = ensureConnection();
		try {
			switch (newState) {
				case PREPARED -> {
					try (PreparedStatement ps = conn.prepareStatement(
							"UPDATE ordertable SET ordertable_IsComplete=1 WHERE ordertable_OrderID=?")) {
						ps.setInt(1, OrderID);
						ps.executeUpdate();
					}
					try (PreparedStatement ps2 = conn.prepareStatement(
							"UPDATE pizza SET pizza_PizzaState='completed' WHERE ordertable_OrderID=?")) {
						ps2.setInt(1, OrderID);
						ps2.executeUpdate();
					}
				}
				case DELIVERED -> {
					try (PreparedStatement ps3 = conn.prepareStatement(
							"UPDATE delivery SET delivery_IsDelivered=1 WHERE ordertable_OrderID=?")) {
						ps3.setInt(1, OrderID);
						ps3.executeUpdate();
					}
				}
				case PICKEDUP -> {
					try (PreparedStatement ps4 = conn.prepareStatement(
							"UPDATE pickup SET pickup_IsPickedUp=1 WHERE ordertable_OrderID=?")) {
						ps4.setInt(1, OrderID);
						ps4.executeUpdate();
					}
				}
			}
		} finally {
			if (openedHere) closeConn();
		}
	}

	// =========================================
	// READ OPERATIONS
	// =========================================
	public static ArrayList<Order> getOrders(int status) throws SQLException, IOException {
		boolean openedHere = ensureConnection();
		ArrayList<Order> orders = new ArrayList<>();
		StringBuilder sql = new StringBuilder("SELECT * FROM ordertable");
		if (status == 1) sql.append(" WHERE ordertable_IsComplete=0");
		else if (status == 2) sql.append(" WHERE ordertable_IsComplete=1");
		sql.append(" ORDER BY ordertable_OrderID ASC");

		try (PreparedStatement ps = conn.prepareStatement(sql.toString());
		     ResultSet rs = ps.executeQuery()) {
			while (rs.next()) orders.add(buildOrderFromResult(conn, rs));
			orders.sort(Comparator.comparingInt(Order::getOrderID));
		} finally {
			if (openedHere) closeConn();
		}
		return orders;
	}

	public static Order getLastOrder() throws SQLException, IOException {
		boolean openedHere = ensureConnection();
		Order order = null;
		String sql = "SELECT * FROM ordertable ORDER BY ordertable_OrderID DESC LIMIT 1";
		try (PreparedStatement ps = conn.prepareStatement(sql);
		     ResultSet rs = ps.executeQuery()) {
			if (rs.next()) order = buildOrderFromResult(conn, rs);
		} finally {
			if (openedHere) closeConn();
		}
		return order;
	}

	public static ArrayList<Order> getOrdersByDate(String date) throws SQLException, IOException {
		boolean openedHere = ensureConnection();
		ArrayList<Order> orders = new ArrayList<>();
		String sql = "SELECT * FROM ordertable WHERE DATE(ordertable_OrderDateTime)=? ORDER BY ordertable_OrderDateTime ASC";
		try (PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setString(1, date);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) orders.add(buildOrderFromResult(conn, rs));
			}
		} finally {
			if (openedHere) closeConn();
		}
		return orders;
	}

	public static ArrayList<Discount> getDiscountList() throws SQLException, IOException {
		boolean openedHere = ensureConnection();
		ArrayList<Discount> discounts = new ArrayList<>();
		String sql = "SELECT * FROM discount ORDER BY discount_DiscountName ASC";
		try (PreparedStatement ps = conn.prepareStatement(sql);
		     ResultSet rs = ps.executeQuery()) {
			while (rs.next()) discounts.add(mapDiscount(rs));
		} finally {
			if (openedHere) closeConn();
		}
		return discounts;
	}

	public static Discount findDiscountByName(String name) throws SQLException, IOException {
		boolean openedHere = ensureConnection();
		Discount d = null;
		String sql = "SELECT * FROM discount WHERE discount_DiscountName=?";
		try (PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setString(1, name);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) d = mapDiscount(rs);
			}
		} finally {
			if (openedHere) closeConn();
		}
		return d;
	}

	public static ArrayList<Customer> getCustomerList() throws SQLException, IOException {
		boolean openedHere = ensureConnection();
		ArrayList<Customer> customers = new ArrayList<>();
		String sql = "SELECT * FROM customer ORDER BY customer_LName, customer_FName, customer_PhoneNum";
		try (PreparedStatement ps = conn.prepareStatement(sql);
		     ResultSet rs = ps.executeQuery()) {
			while (rs.next())
				customers.add(new Customer(rs.getInt("customer_CustID"), rs.getString("customer_FName"),
						rs.getString("customer_LName"), rs.getString("customer_PhoneNum")));
		} finally {
			if (openedHere) closeConn();
		}
		return customers;
	}

	public static Customer findCustomerByPhone(String phoneNumber) throws SQLException, IOException {
		boolean openedHere = ensureConnection();
		Customer c = null;
		String sql = "SELECT * FROM customer WHERE customer_PhoneNum=?";
		try (PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setString(1, phoneNumber);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next())
					c = new Customer(rs.getInt("customer_CustID"), rs.getString("customer_FName"),
							rs.getString("customer_LName"), rs.getString("customer_PhoneNum"));
			}
		} finally {
			if (openedHere) closeConn();
		}
		return c;
	}

	public static String getCustomerName(int CustID) throws SQLException, IOException {
		boolean openedHere = ensureConnection();
		String cname1 = "", cname2 = "";
		String query = "Select customer_FName, customer_LName From customer WHERE customer_CustID=" + CustID + ";";
		try (Statement stmt = conn.createStatement();
		     ResultSet rset = stmt.executeQuery(query)) {
			while (rset.next()) cname1 = rset.getString(1) + " " + rset.getString(2);
		}
		try {
			String query2 = "Select customer_FName, customer_LName From customer WHERE customer_CustID=?;";
			try (PreparedStatement os = conn.prepareStatement(query2)) {
				os.setInt(1, CustID);
				try (ResultSet rset2 = os.executeQuery()) {
					while (rset2.next())
						cname2 = rset2.getString("customer_FName") + " " + rset2.getString("customer_LName");
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		if (openedHere) closeConn();
		return cname1; // or cname2
	}

	public static ArrayList<Topping> getToppingList() throws SQLException, IOException {
		boolean openedHere = ensureConnection();
		ArrayList<Topping> tops = new ArrayList<>();
		String sql = "SELECT * FROM topping ORDER BY topping_TopName";
		try (PreparedStatement ps = conn.prepareStatement(sql);
		     ResultSet rs = ps.executeQuery()) {
			while (rs.next()) tops.add(mapTopping(rs));
		} finally {
			if (openedHere) closeConn();
		}
		return tops;
	}

	public static Topping findToppingByName(String name) throws SQLException, IOException {
		boolean openedHere = ensureConnection();
		Topping t = null;
		String sql = "SELECT * FROM topping WHERE topping_TopName=?";
		try (PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setString(1, name);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) t = mapTopping(rs);
			}
		} finally {
			if (openedHere) closeConn();
		}
		return t;
	}

	public static ArrayList<Topping> getToppingsOnPizza(Pizza p) throws SQLException, IOException {
		boolean openedHere = ensureConnection();
		ArrayList<Topping> tops;
		try {
			tops = fetchPizzaToppings(conn, p.getPizzaID());
		} finally {
			if (openedHere) closeConn();
		}
		return tops;
	}

	public static void addToInventory(int toppingID, double quantity) throws SQLException, IOException {
		boolean openedHere = ensureConnection();
		String sql = "UPDATE topping SET topping_CurINVT = topping_CurINVT + ? WHERE topping_TopID=?";
		try (PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setDouble(1, quantity);
			ps.setInt(2, toppingID);
			ps.executeUpdate();
		} finally {
			if (openedHere) closeConn();
		}
	}

	public static ArrayList<Pizza> getPizzas(Order o) throws SQLException, IOException {
		boolean openedHere = ensureConnection();
		ArrayList<Pizza> pizzas;
		try {
			pizzas = fetchPizzasForOrder(conn, o.getOrderID());
		} finally {
			if (openedHere) closeConn();
		}
		return pizzas;
	}

	public static ArrayList<Discount> getDiscounts(Order o) throws SQLException, IOException {
		boolean openedHere = ensureConnection();
		ArrayList<Discount> discounts;
		try {
			discounts = fetchOrderDiscounts(conn, o.getOrderID());
		} finally {
			if (openedHere) closeConn();
		}
		return discounts;
	}

	public static ArrayList<Discount> getDiscounts(Pizza p) throws SQLException, IOException {
		boolean openedHere = ensureConnection();
		ArrayList<Discount> discounts;
		try {
			discounts = fetchPizzaDiscounts(conn, p.getPizzaID());
		} finally {
			if (openedHere) closeConn();
		}
		return discounts;
	}

	public static double getBaseCustPrice(String size, String crust) throws SQLException, IOException {
		boolean openedHere = ensureConnection();
		double price = 0.0;
		String sql = "SELECT baseprice_CustPrice FROM baseprice WHERE baseprice_Size=? AND baseprice_CrustType=?";
		try (PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setString(1, size);
			ps.setString(2, crust);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) price = rs.getDouble(1);
			}
		} finally {
			if (openedHere) closeConn();
		}
		return price;
	}

	public static double getBaseBusPrice(String size, String crust) throws SQLException, IOException {
		boolean openedHere = ensureConnection();
		double price = 0.0;
		String sql = "SELECT baseprice_BusPrice FROM baseprice WHERE baseprice_Size=? AND baseprice_CrustType=?";
		try (PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setString(1, size);
			ps.setString(2, crust);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) price = rs.getDouble(1);
			}
		} finally {
			if (openedHere) closeConn();
		}
		return price;
	}

	public static void printToppingReport() throws SQLException, IOException {
		boolean openedHere = ensureConnection();
		String sql = "SELECT * FROM ToppingPopularity";
		try (PreparedStatement ps = conn.prepareStatement(sql);
		     ResultSet rs = ps.executeQuery()) {
			System.out.printf("%-15s%-15s%n", "Topping", "Topping Count");
			System.out.printf("%-15s%-15s%n", "-------", "-------------");
			while (rs.next()) System.out.printf("%-15s%-15s%n", rs.getString(1), rs.getInt(2));
		} finally {
			if (openedHere) closeConn();
		}
	}

	public static void printProfitByPizzaReport() throws SQLException, IOException {
		boolean openedHere = ensureConnection();
		String sql = "SELECT * FROM ProfitByPizza";
		try (PreparedStatement ps = conn.prepareStatement(sql);
		     ResultSet rs = ps.executeQuery()) {
			System.out.printf("%-20s%-20s%-20s%-20s%n", "Pizza Size", "Pizza Crust", "Profit", "Last Order Date");
			System.out.printf("%-20s%-20s%-20s%-20s%n", "----------", "-----------", "------", "---------------");
			while (rs.next())
				System.out.printf("%-20s%-20s%-20s%-20s%n",
						rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4));
		} finally {
			if (openedHere) closeConn();
		}
	}

	public static void printProfitByOrderTypeReport() throws SQLException, IOException {
		boolean openedHere = ensureConnection();
		String sql = "SELECT * FROM ProfitByOrderType";
		try (PreparedStatement ps = conn.prepareStatement(sql);
		     ResultSet rs = ps.executeQuery()) {
			System.out.printf("%-20s%-20s%-20s%-20s%-20s%n",
					"Customer Type", "Order Month", "Total Order Price", "Total Order Cost", "Profit");
			System.out.printf("%-20s%-20s%-20s%-20s%-20s%n",
					"-------------", "-----------", "-----------------", "----------------", "------");
			while (rs.next())
				System.out.printf("%-20s%-20s%-20s%-20s%-20s%n",
						rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5));
		} finally {
			if (openedHere) closeConn();
		}
	}

	// =========================================
	// DATE HELPERS
	// =========================================
	private static int getYear(String date) { return Integer.parseInt(date.substring(0, 4)); }
	private static int getMonth(String date) { return Integer.parseInt(date.substring(5, 7)); }
	private static int getDay(String date) { return Integer.parseInt(date.substring(8, 10)); }

	public static boolean checkDate(int year, int month, int day, String dateOfOrder) {
		if (getYear(dateOfOrder) > year) return true;
		if (getYear(dateOfOrder) < year) return false;
		if (getMonth(dateOfOrder) > month) return true;
		if (getMonth(dateOfOrder) < month) return false;
		return getDay(dateOfOrder) >= day;
	}

	// =========================================
	// INTERNAL HELPERS
	// =========================================
	private static Timestamp parseTimestamp(String dateStr) {
		try { return Timestamp.valueOf(dateStr); }
		catch (IllegalArgumentException e) { return new Timestamp(new java.util.Date().getTime()); }
	}

	private static void closeConn() throws SQLException {
		if (conn != null && !conn.isClosed()) conn.close();
	}

	private static boolean ensureConnection() throws SQLException, IOException {
		if (conn == null || conn.isClosed()) return connect_to_db();
		return false;
	}

	private static int insertPizza(Connection connection, Timestamp d, int orderID, Pizza p) throws SQLException {
		reconcileToppingDoubles(p);
		String insertPizza = "INSERT INTO pizza (pizza_Size, pizza_CrustType, ordertable_OrderID, pizza_PizzaState, " +
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
					p.setPizzaID(pizzaId);
				}
			}
		}

		String insertPizzaDisc = "INSERT INTO pizza_discount (pizza_PizzaID, discount_DiscountID) VALUES (?,?)";
		if (p.getDiscounts() != null) {
			for (Discount dsc : p.getDiscounts()) {
				try (PreparedStatement pds = connection.prepareStatement(insertPizzaDisc)) {
					pds.setInt(1, pizzaId);
					pds.setInt(2, dsc.getDiscountID());
					pds.executeUpdate();
				}
			}
		}

		if (p.getToppings() != null) {
			String insertTop = "INSERT INTO pizza_topping (pizza_PizzaID, topping_TopID, pizza_topping_IsDouble) VALUES (?,?,?)";
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
		if (p.getToppings() == null || p.getToppings().isEmpty()) return;
		double baseCust, baseBus;
		try {
			baseCust = getBaseCustPrice(p.getSize(), p.getCrustType());
			baseBus = getBaseBusPrice(p.getSize(), p.getCrustType());
		} catch (IOException e) {
			throw new SQLException("Failed to retrieve base prices", e);
		}
		double expectedCust = baseCust, expectedBus = baseBus;
		for (Topping t : p.getToppings()) {
			double units = toppingUnitsForSize(t, p.getSize());
			expectedCust += units * t.getCustPrice();
			expectedBus += units * t.getBusPrice();
		}
		double expectedCustAfterDiscounts = applyPizzaDiscounts(expectedCust, p.getDiscounts());
		double deltaCust = p.getCustPrice() - expectedCustAfterDiscounts;
		double deltaBus = p.getBusPrice() - expectedBus;

		for (Topping t : p.getToppings()) {
			if (t.getDoubled()) continue;
			double units = toppingUnitsForSize(t, p.getSize());
			double extraCust = units * t.getCustPrice();
			double extraBus = units * t.getBusPrice();
			if (deltaCust + 1e-6 >= extraCust && deltaBus + 1e-6 >= extraBus) {
				t.setDoubled(true);
				deltaCust -= extraCust;
				deltaBus -= extraBus;
			}
		}
	}

	private static double applyPizzaDiscounts(double price, ArrayList<Discount> discounts) {
		if (discounts == null) return price;
		double adjusted = price;
		for (Discount d : discounts)
			adjusted = d.isPercent() ? adjusted * (1 - d.getAmount() / 100.0) : adjusted - d.getAmount();
		return adjusted;
	}

	private static void updateInventoryForTopping(Connection connection, Topping t, String size, boolean isDouble) throws SQLException {
		double unitsNeeded = toppingUnitsForSize(t, size);
		if (isDouble) unitsNeeded *= 2;
		double unitsToUse = Math.ceil(unitsNeeded);

		double cur = 0;
		String checkSql = "SELECT topping_CurINVT FROM topping WHERE topping_TopID=?";
		try (PreparedStatement check = connection.prepareStatement(checkSql)) {
			check.setInt(1, t.getTopID());
			try (ResultSet r = check.executeQuery()) {
				if (r.next()) cur = r.getDouble(1);
			}
		}
		if (cur - unitsToUse < 0) throw new SQLException("Not enough inventory for topping " + t.getTopName());

		String updateSql = "UPDATE topping SET topping_CurINVT = topping_CurINVT - ? WHERE topping_TopID=?";
		try (PreparedStatement ups = connection.prepareStatement(updateSql)) {
			ups.setDouble(1, unitsToUse);
			ups.setInt(2, t.getTopID());
			ups.executeUpdate();
		}
	}

	private static void addOrderTypeDetails(Connection connection, Order o, int orderId) throws SQLException {
		if (o instanceof DineinOrder d) {
			try (PreparedStatement ps = connection.prepareStatement(
					"INSERT INTO dinein (ordertable_OrderID, dinein_TableNum) VALUES (?,?)")) {
				ps.setInt(1, orderId);
				ps.setInt(2, d.getTableNum());
				ps.executeUpdate();
			}
		} else if (o instanceof PickupOrder p) {
			try (PreparedStatement ps = connection.prepareStatement(
					"INSERT INTO pickup (ordertable_OrderID, pickup_IsPickedUp) VALUES (?,?)")) {
				ps.setInt(1, orderId);
				ps.setBoolean(2, p.getIsPickedUp());
				ps.executeUpdate();
			}
		} else if (o instanceof DeliveryOrder d) {
			String[] parts = parseAddress(d.getAddress());
			try (PreparedStatement ps = connection.prepareStatement(
					"INSERT INTO delivery (ordertable_OrderID, delivery_HouseNum, delivery_Street, delivery_City, delivery_State, delivery_Zip, delivery_IsDelivered) " +
							"VALUES (?,?,?,?,?,?,?)")) {
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
		String[] parts = address.split("\\t");
		if (parts.length < 5) {
			String[] fallback = address.split("\\s+");
			if (fallback.length >= 5)
				return new String[]{fallback[0], fallback[1], fallback[2], fallback[3], fallback[4]};
		}
		if (parts.length < 5) return new String[]{"0", "", "", "", "0"};
		return parts;
	}

	private static Order buildOrderFromResult(Connection connection, ResultSet rs) throws SQLException {
		int orderId = rs.getInt("ordertable_OrderID");
		int custId = rs.getInt("customer_CustID");
		if (rs.wasNull()) custId = -1;
		String orderType = rs.getString("ordertable_OrderType");
		String date = rs.getString("ordertable_OrderDateTime");
		double custPrice = rs.getDouble("ordertable_CustPrice");
		double busPrice = rs.getDouble("ordertable_BusPrice");
		boolean isComplete = rs.getBoolean("ordertable_IsComplete");

		Order o;
		if (orderType.equals(dine_in)) {
			int table = 0;
			try (PreparedStatement ps = connection.prepareStatement(
					"SELECT dinein_TableNum FROM dinein WHERE ordertable_OrderID=?")) {
				ps.setInt(1, orderId);
				try (ResultSet d = ps.executeQuery()) {
					if (d.next()) table = d.getInt(1);
				}
			}
			o = new DineinOrder(orderId, custId, date, custPrice, busPrice, isComplete, table);
		} else if (orderType.equals(pickup)) {
			boolean picked = false;
			try (PreparedStatement ps = connection.prepareStatement(
					"SELECT pickup_IsPickedUp FROM pickup WHERE ordertable_OrderID=?")) {
				ps.setInt(1, orderId);
				try (ResultSet d = ps.executeQuery()) {
					if (d.next()) picked = d.getBoolean(1);
				}
			}
			o = new PickupOrder(orderId, custId, date, custPrice, busPrice, picked, isComplete);
		} else {
			String addr = "";
			boolean delivered = false;
			try (PreparedStatement ps = connection.prepareStatement(
					"SELECT delivery_HouseNum, delivery_Street, delivery_City, delivery_State, delivery_Zip, delivery_IsDelivered " +
							"FROM delivery WHERE ordertable_OrderID=?")) {
				ps.setInt(1, orderId);
				try (ResultSet d = ps.executeQuery()) {
					if (d.next()) {
						addr = d.getInt(1) + "\t" + d.getString(2) + "\t" + d.getString(3) + "\t" +
								d.getString(4) + "\t" + d.getInt(5);
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
		ArrayList<Pizza> pizzas = new ArrayList<>();
		String sql = "SELECT * FROM pizza WHERE ordertable_OrderID=? ORDER BY pizza_PizzaID";
		try (PreparedStatement ps = connection.prepareStatement(sql)) {
			ps.setInt(1, orderId);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					int pizzaId = rs.getInt("pizza_PizzaID");
					Pizza p = new Pizza(pizzaId,
							rs.getString("pizza_Size"),
							rs.getString("pizza_CrustType"),
							orderId,
							rs.getString("pizza_PizzaState"),
							rs.getString("pizza_PizzaDate"),
							rs.getDouble("pizza_CustPrice"),
							rs.getDouble("pizza_BusPrice"));
					p.setToppings(fetchPizzaToppings(connection, pizzaId));
					p.setDiscounts(fetchPizzaDiscounts(connection, pizzaId));
					pizzas.add(p);
				}
			}
		}
		return pizzas;
	}

	private static ArrayList<Discount> fetchOrderDiscounts(Connection connection, int orderId) throws SQLException {
		ArrayList<Discount> discounts = new ArrayList<>();
		String sql = "SELECT d.* FROM discount d JOIN order_discount od ON d.discount_DiscountID = od.discount_DiscountID " +
				"WHERE od.ordertable_OrderID=? ORDER BY d.discount_DiscountID";
		try (PreparedStatement ps = connection.prepareStatement(sql)) {
			ps.setInt(1, orderId);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) discounts.add(mapDiscount(rs));
			}
		}
		return discounts;
	}

	private static ArrayList<Discount> fetchPizzaDiscounts(Connection connection, int pizzaId) throws SQLException {
		ArrayList<Discount> discounts = new ArrayList<>();
		String sql = "SELECT d.* FROM discount d JOIN pizza_discount pd ON d.discount_DiscountID = pd.discount_DiscountID " +
				"WHERE pd.pizza_PizzaID=? ORDER BY d.discount_DiscountID";
		try (PreparedStatement ps = connection.prepareStatement(sql)) {
			ps.setInt(1, pizzaId);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) discounts.add(mapDiscount(rs));
			}
		}
		return discounts;
	}

	private static ArrayList<Topping> fetchPizzaToppings(Connection connection, int pizzaId) throws SQLException {
		ArrayList<Topping> tops = new ArrayList<>();
		String sql = "SELECT t.*, pt.pizza_topping_IsDouble FROM topping t " +
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
		if (size.equals(size_s)) return t.getSmallAMT();
		if (size.equals(size_m)) return t.getMedAMT();
		if (size.equals(size_l)) return t.getLgAMT();
		return t.getXLAMT();
	}

	private static Discount mapDiscount(ResultSet rs) throws SQLException {
		return new Discount(rs.getInt("discount_DiscountID"), rs.getString("discount_DiscountName"),
				rs.getDouble("discount_Amount"), rs.getBoolean("discount_IsPercent"));
	}

	private static Topping mapTopping(ResultSet rs) throws SQLException {
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
