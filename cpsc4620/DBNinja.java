package cpsc4620;

import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public final class DBNinja {

    private static Connection conn = null;

    // Order type constants
    public final static String pickup = "pickup";
    public final static String delivery = "delivery";
    public final static String dinein = "dinein";

    // Size constants (what Menu.java uses)
    public final static String size_s = "Small";
    public final static String size_m = "Medium";
    public final static String size_l = "Large";
    public final static String size_xl = "XLarge";

    // Crust constants (what Menu.java uses)
    public final static String crust_thin = "Thin";
    public final static String crust_orig = "Original";
    public final static String crust_pan = "Pan";
    public final static String crust_gf = "Gluten-Free";

    public enum order_state { PREPARED, DELIVERED, PICKEDUP }

    // -----------------------
    // Connection management
    // -----------------------

    private static boolean connectToDB() throws SQLException, IOException {
        if (conn != null && !conn.isClosed()) {
            return true;
        }
        conn = DBConnector.make_connection();
        return conn != null;
    }

    // -----------------------
    // Core write operations
    // -----------------------

    public static void addOrder(Order o) throws SQLException, IOException {
        // Autograder passes null here in its compilation check
        if (o == null) return;
        if (!connectToDB()) return;

        // Insert into ordertable
        String query = "INSERT INTO ordertable " +
                "(ordertable_OrderType, ordertable_OrderDateTime, " +
                "ordertable_CustPrice, ordertable_BusPrice, " +
                "ordertable_IsComplete, customer_CustID) " +
                "VALUES (?, ?, ?, ?, ?, ?)";

        PreparedStatement ps = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);
        ps.setString(1, o.getOrderType());
        ps.setString(2, o.getDate());
        ps.setDouble(3, o.getCustPrice());
        ps.setDouble(4, o.getBusPrice());
        ps.setInt(5, o.getIsComplete() ? 1 : 0);

        if (o.getCustID() <= 0) {
            ps.setNull(6, Types.INTEGER);
        } else {
            ps.setInt(6, o.getCustID());
        }

        ps.executeUpdate();
        ResultSet keys = ps.getGeneratedKeys();
        int orderID = -1;
        if (keys.next()) {
            orderID = keys.getInt(1);
        }
        keys.close();
        ps.close();

        // Insert into subtype table
        if (o instanceof DineinOrder) {
            DineinOrder d = (DineinOrder) o;
            String q = "INSERT INTO dinein (ordertable_OrderID, dinein_TableNum) VALUES (?, ?)";
            PreparedStatement ps2 = conn.prepareStatement(q);
            ps2.setInt(1, orderID);
            ps2.setInt(2, d.getTableNum());
            ps2.executeUpdate();
            ps2.close();
        } else if (o instanceof PickupOrder) {
            PickupOrder p = (PickupOrder) o;
            String q = "INSERT INTO pickup (ordertable_OrderID, pickup_IsPickedUp) VALUES (?, ?)";
            PreparedStatement ps2 = conn.prepareStatement(q);
            ps2.setInt(1, orderID);
            ps2.setInt(2, p.getIsPickedUp() ? 1 : 0);
            ps2.executeUpdate();
            ps2.close();
        } else if (o instanceof DeliveryOrder) {
            DeliveryOrder d = (DeliveryOrder) o;
            String addr = d.getAddress();
            // Format from Menu: "house\tstreet\tcity\tstate\tzip"
            String[] parts = addr.split("\\t");
            int houseNum = 0;
            int zip = 0;
            String street = "";
            String city = "";
            String state = "";

            if (parts.length > 0 && !parts[0].isEmpty()) {
                try { houseNum = Integer.parseInt(parts[0]); } catch (NumberFormatException ignore) {}
            }
            if (parts.length > 1) street = parts[1];
            if (parts.length > 2) city = parts[2];
            if (parts.length > 3) state = parts[3];
            if (parts.length > 4 && !parts[4].isEmpty()) {
                try { zip = Integer.parseInt(parts[4]); } catch (NumberFormatException ignore) {}
            }

            String q = "INSERT INTO delivery " +
                    "(ordertable_OrderID, delivery_HouseNum, delivery_Street, " +
                    " delivery_City, delivery_State, delivery_Zip, delivery_IsDelivered) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)";
            PreparedStatement ps2 = conn.prepareStatement(q);
            ps2.setInt(1, orderID);
            ps2.setInt(2, houseNum);
            ps2.setString(3, street);
            ps2.setString(4, city);
            ps2.setString(5, state);
            ps2.setInt(6, zip);
            ps2.setInt(7, 0); // new order, not delivered yet
            ps2.executeUpdate();
            ps2.close();
        }

        // Insert pizzas for this order
        Date now = new Date();
        for (Pizza p : o.getPizzaList()) {
            addPizza(now, orderID, p);
        }

        // Insert order-level discounts
        for (Discount d : o.getDiscountList()) {
            String qOD = "INSERT INTO order_discount " +
                    "(ordertable_OrderID, discount_DiscountID) VALUES (?, ?)";
            PreparedStatement psOD = conn.prepareStatement(qOD);
            psOD.setInt(1, orderID);
            psOD.setInt(2, d.getDiscountID());
            psOD.executeUpdate();
            psOD.close();
        }
    }

    public static int addPizza(Date d, int orderID, Pizza p) throws SQLException, IOException {
        if (p == null) return -1;
        if (!connectToDB()) return -1;

        // Insert pizza row
        String query = "INSERT INTO pizza " +
                "(ordertable_OrderID, pizza_Size, pizza_CrustType, pizza_PizzaState, " +
                " pizza_PizzaDate, pizza_CustPrice, pizza_BusPrice) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
        PreparedStatement ps = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);
        ps.setInt(1, orderID);
        ps.setString(2, p.getSize());
        ps.setString(3, p.getCrustType());
        ps.setString(4, "completed");  // store as completed when it hits the DB
        ps.setString(5, new Timestamp(d.getTime()).toString());
        ps.setDouble(6, p.getCustPrice());
        ps.setDouble(7, p.getBusPrice());
        ps.executeUpdate();

        ResultSet keys = ps.getGeneratedKeys();
        int pizzaID = -1;
        if (keys.next()) {
            pizzaID = keys.getInt(1);
        }
        keys.close();
        ps.close();

        // --- Deduplicate toppings by TopID before inserting ---
        // If topping appears multiple times, insert only once.
        // If any copy is "extra", store as Double = 1.
        Map<Integer, Integer> toppingMap = new HashMap<>();

        for (Topping t : p.getToppings()) {
            int topId = t.getTopID();
            int isDouble = t.getDoubled() ? 1 : 0;

            if (!toppingMap.containsKey(topId)) {
                toppingMap.put(topId, isDouble);
            } else if (isDouble == 1) {
                toppingMap.put(topId, 1);
            }
        }

        String toppingQuery = "INSERT INTO pizza_topping " +
                "(pizza_PizzaID, topping_TopID, pizza_topping_IsDouble) VALUES (?, ?, ?)";
        PreparedStatement toppingPs = conn.prepareStatement(toppingQuery);
        for (Map.Entry<Integer, Integer> e : toppingMap.entrySet()) {
            toppingPs.setInt(1, pizzaID);
            toppingPs.setInt(2, e.getKey());
            toppingPs.setInt(3, e.getValue());
            toppingPs.executeUpdate();
        }
        toppingPs.close();

        // Pizza-level discounts
        for (Discount d2 : p.getDiscounts()) {
            String discountQuery = "INSERT INTO pizza_discount " +
                    "(pizza_PizzaID, discount_DiscountID) VALUES (?, ?)";
            PreparedStatement discountPs = conn.prepareStatement(discountQuery);
            discountPs.setInt(1, pizzaID);
            discountPs.setInt(2, d2.getDiscountID());
            discountPs.executeUpdate();
            discountPs.close();
        }

        return pizzaID;
    }

    public static int addCustomer(Customer c) throws SQLException, IOException {
        if (c == null) return -1;
        if (!connectToDB()) return -1;

        String query = "INSERT INTO customer " +
                "(customer_FName, customer_LName, customer_PhoneNum) VALUES (?, ?, ?)";
        PreparedStatement ps = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);
        ps.setString(1, c.getFName());
        ps.setString(2, c.getLName());
        ps.setString(3, c.getPhone());
        ps.executeUpdate();

        ResultSet keys = ps.getGeneratedKeys();
        int custID = -1;
        if (keys.next()) {
            custID = keys.getInt(1);
        }
        keys.close();
        ps.close();
        return custID;
    }

    public static void completeOrder(int orderID, order_state newState)
            throws SQLException, IOException {
        if (!connectToDB()) return;

        if (newState == order_state.PREPARED) {
            String q = "UPDATE ordertable SET ordertable_IsComplete = 1 " +
                    "WHERE ordertable_OrderID = ?";
            PreparedStatement ps = conn.prepareStatement(q);
            ps.setInt(1, orderID);
            ps.executeUpdate();
            ps.close();
        } else if (newState == order_state.DELIVERED) {
            String q = "UPDATE delivery SET delivery_IsDelivered = 1 " +
                    "WHERE ordertable_OrderID = ?";
            PreparedStatement ps = conn.prepareStatement(q);
            ps.setInt(1, orderID);
            ps.executeUpdate();
            ps.close();
        } else if (newState == order_state.PICKEDUP) {
            String q = "UPDATE pickup SET pickup_IsPickedUp = 1 " +
                    "WHERE ordertable_OrderID = ?";
            PreparedStatement ps = conn.prepareStatement(q);
            ps.setInt(1, orderID);
            ps.executeUpdate();
            ps.close();
        }
    }

    // -----------------------
    // Order querying
    // -----------------------

    public static ArrayList<Order> getOrders(int status) throws SQLException, IOException {
        if (!connectToDB()) return new ArrayList<>();

        ArrayList<Order> orders = new ArrayList<>();
        StringBuilder sb = new StringBuilder("SELECT * FROM ordertable");
        if (status == 1) {
            sb.append(" WHERE ordertable_IsComplete = 0");
        } else if (status == 2) {
            sb.append(" WHERE ordertable_IsComplete = 1");
        }
        sb.append(" ORDER BY ordertable_OrderID");

        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(sb.toString());
        while (rs.next()) {
            Order o = buildOrderFromResultSet(rs);
            if (o != null) {
                orders.add(o);
            }
        }
        rs.close();
        stmt.close();
        return orders;
    }

    public static Order getLastOrder() throws SQLException, IOException {
        if (!connectToDB()) return null;

        String query = "SELECT * FROM ordertable " +
                "ORDER BY ordertable_OrderID DESC LIMIT 1";
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(query);
        Order o = null;
        if (rs.next()) {
            o = buildOrderFromResultSet(rs);
        }
        rs.close();
        stmt.close();
        return o;
    }

    public static ArrayList<Order> getOrdersByDate(String date)
            throws SQLException, IOException {
        if (!connectToDB()) return new ArrayList<>();

        ArrayList<Order> orders = new ArrayList<>();
        String query = "SELECT * FROM ordertable " +
                "WHERE DATE(ordertable_OrderDateTime) = ? " +
                "ORDER BY ordertable_OrderID";
        PreparedStatement ps = conn.prepareStatement(query);
        ps.setString(1, date);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            Order o = buildOrderFromResultSet(rs);
            if (o != null) {
                orders.add(o);
            }
        }
        rs.close();
        ps.close();
        return orders;
    }

    // -----------------------
    // Discount helpers
    // -----------------------

    public static ArrayList<Discount> getDiscountList() throws SQLException, IOException {
        if (!connectToDB()) return new ArrayList<>();

        ArrayList<Discount> discounts = new ArrayList<>();
        String query = "SELECT * FROM discount ORDER BY discount_DiscountName";
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(query);
        while (rs.next()) {
            Discount d = new Discount(
                    rs.getInt("discount_DiscountID"),
                    rs.getString("discount_DiscountName"),
                    rs.getDouble("discount_Amount"),
                    rs.getInt("discount_IsPercent") == 1
            );
            discounts.add(d);
        }
        rs.close();
        stmt.close();
        return discounts;
    }

    public static Discount findDiscountByName(String name)
            throws SQLException, IOException {
        if (!connectToDB()) return null;

        String query = "SELECT * FROM discount " +
                "WHERE discount_DiscountName = ?";
        PreparedStatement ps = conn.prepareStatement(query);
        ps.setString(1, name);
        ResultSet rs = ps.executeQuery();
        Discount d = null;
        if (rs.next()) {
            d = new Discount(
                    rs.getInt("discount_DiscountID"),
                    rs.getString("discount_DiscountName"),
                    rs.getDouble("discount_Amount"),
                    rs.getInt("discount_IsPercent") == 1
            );
        }
        rs.close();
        ps.close();
        return d;
    }

    public static ArrayList<Discount> getDiscounts(Order o)
            throws SQLException, IOException {
        if (o == null) return new ArrayList<>();
        if (!connectToDB()) return new ArrayList<>();

        ArrayList<Discount> discounts = new ArrayList<>();
        String query = "SELECT d.* FROM discount d " +
                "JOIN order_discount od ON d.discount_DiscountID = od.discount_DiscountID " +
                "WHERE od.ordertable_OrderID = ?";
        PreparedStatement ps = conn.prepareStatement(query);
        ps.setInt(1, o.getOrderID());
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            Discount d = new Discount(
                    rs.getInt("discount_DiscountID"),
                    rs.getString("discount_DiscountName"),
                    rs.getDouble("discount_Amount"),
                    rs.getInt("discount_IsPercent") == 1
            );
            discounts.add(d);
        }
        rs.close();
        ps.close();
        return discounts;
    }

    public static ArrayList<Discount> getDiscountsPizza(Pizza p)
            throws SQLException, IOException {
        if (p == null) return new ArrayList<>();
        if (!connectToDB()) return new ArrayList<>();

        ArrayList<Discount> discounts = new ArrayList<>();
        String query = "SELECT d.* FROM discount d " +
                "JOIN pizza_discount pd ON d.discount_DiscountID = pd.discount_DiscountID " +
                "WHERE pd.pizza_PizzaID = ?";
        PreparedStatement ps = conn.prepareStatement(query);
        ps.setInt(1, p.getPizzaID());
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            Discount d = new Discount(
                    rs.getInt("discount_DiscountID"),
                    rs.getString("discount_DiscountName"),
                    rs.getDouble("discount_Amount"),
                    rs.getInt("discount_IsPercent") == 1
            );
            discounts.add(d);
        }
        rs.close();
        ps.close();
        return discounts;
    }

    // Overload required by Menu.autograder_compilation_check:
    // DBNinja.getDiscounts(pizza) must compile.
    public static ArrayList<Discount> getDiscounts(Pizza p)
            throws SQLException, IOException {
        return getDiscountsPizza(p);
    }

    // -----------------------
    // Customer helpers
    // -----------------------

    public static ArrayList<Customer> getCustomerList()
            throws SQLException, IOException {
        if (!connectToDB()) return new ArrayList<>();

        ArrayList<Customer> customers = new ArrayList<>();
        String query = "SELECT * FROM customer ORDER BY customer_CustID";
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(query);
        while (rs.next()) {
            Customer c = new Customer(
                    rs.getInt("customer_CustID"),
                    rs.getString("customer_FName"),
                    rs.getString("customer_LName"),
                    rs.getString("customer_PhoneNum")
            );
            customers.add(c);
        }
        rs.close();
        stmt.close();
        return customers;
    }

    public static Customer findCustomerByPhone(String phoneNumber)
            throws SQLException, IOException {
        if (!connectToDB()) return null;

        String query = "SELECT * FROM customer WHERE customer_PhoneNum = ?";
        PreparedStatement ps = conn.prepareStatement(query);
        ps.setString(1, phoneNumber);
        ResultSet rs = ps.executeQuery();
        Customer c = null;
        if (rs.next()) {
            c = new Customer(
                    rs.getInt("customer_CustID"),
                    rs.getString("customer_FName"),
                    rs.getString("customer_LName"),
                    rs.getString("customer_PhoneNum")
            );
        }
        rs.close();
        ps.close();
        return c;
    }

    public static String getCustomerName(int CustID)
            throws SQLException, IOException {
        if (CustID <= 0) return "IN STORE";
        if (!connectToDB()) return "IN STORE";

        String query = "SELECT customer_FName, customer_LName " +
                "FROM customer WHERE customer_CustID = ?";
        PreparedStatement ps = conn.prepareStatement(query);
        ps.setInt(1, CustID);
        ResultSet rs = ps.executeQuery();
        String name = "IN STORE";
        if (rs.next()) {
            name = rs.getString("customer_FName") + " " +
                    rs.getString("customer_LName");
        }
        rs.close();
        ps.close();
        return name;
    }

    // -----------------------
    // Topping / inventory
    // -----------------------

    public static ArrayList<Topping> getToppingList()
            throws SQLException, IOException {
        if (!connectToDB()) return new ArrayList<>();

        ArrayList<Topping> toppings = new ArrayList<>();
        String query = "SELECT * FROM topping ORDER BY topping_TopName";
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(query);
        while (rs.next()) {
            Topping t = new Topping(
                    rs.getInt("topping_TopID"),
                    rs.getString("topping_TopName"),
                    rs.getDouble("topping_SmallAMT"),
                    rs.getDouble("topping_MedAMT"),
                    rs.getDouble("topping_LgAMT"),
                    rs.getDouble("topping_XLAMT"),
                    rs.getDouble("topping_CustPrice"),
                    rs.getDouble("topping_BusPrice"),
                    rs.getInt("topping_MinINVT"),
                    rs.getInt("topping_CurINVT")
            );
            toppings.add(t);
        }
        rs.close();
        stmt.close();
        return toppings;
    }

    public static Topping findToppingByName(String name)
            throws SQLException, IOException {
        if (!connectToDB()) return null;

        String query = "SELECT * FROM topping WHERE topping_TopName = ?";
        PreparedStatement ps = conn.prepareStatement(query);
        ps.setString(1, name);
        ResultSet rs = ps.executeQuery();
        Topping t = null;
        if (rs.next()) {
            t = new Topping(
                    rs.getInt("topping_TopID"),
                    rs.getString("topping_TopName"),
                    rs.getDouble("topping_SmallAMT"),
                    rs.getDouble("topping_MedAMT"),
                    rs.getDouble("topping_LgAMT"),
                    rs.getDouble("topping_XLAMT"),
                    rs.getDouble("topping_CustPrice"),
                    rs.getDouble("topping_BusPrice"),
                    rs.getInt("topping_MinINVT"),
                    rs.getInt("topping_CurINVT")
            );
        }
        rs.close();
        ps.close();
        return t;
    }

    public static ArrayList<Topping> getToppingsOnPizza(Pizza p)
            throws SQLException, IOException {
        if (p == null) return new ArrayList<>();
        if (!connectToDB()) return new ArrayList<>();

        ArrayList<Topping> toppings = new ArrayList<>();
        String query = "SELECT t.*, pt.pizza_topping_IsDouble " +
                "FROM topping t " +
                "JOIN pizza_topping pt ON t.topping_TopID = pt.topping_TopID " +
                "WHERE pt.pizza_PizzaID = ?";
        PreparedStatement ps = conn.prepareStatement(query);
        ps.setInt(1, p.getPizzaID());
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            Topping t = new Topping(
                    rs.getInt("topping_TopID"),
                    rs.getString("topping_TopName"),
                    rs.getDouble("topping_SmallAMT"),
                    rs.getDouble("topping_MedAMT"),
                    rs.getDouble("topping_LgAMT"),
                    rs.getDouble("topping_XLAMT"),
                    rs.getDouble("topping_CustPrice"),
                    rs.getDouble("topping_BusPrice"),
                    rs.getInt("topping_MinINVT"),
                    rs.getInt("topping_CurINVT")
            );
            t.setDoubled(rs.getInt("pizza_topping_IsDouble") == 1);
            toppings.add(t);
        }
        rs.close();
        ps.close();
        return toppings;
    }

    public static void addToInventory(int toppingID, double quantity)
            throws SQLException, IOException {
        if (!connectToDB()) return;

        String query = "UPDATE topping " +
                "SET topping_CurINVT = topping_CurINVT + ? " +
                "WHERE topping_TopID = ?";
        PreparedStatement ps = conn.prepareStatement(query);
        ps.setDouble(1, quantity);
        ps.setInt(2, toppingID);
        ps.executeUpdate();
        ps.close();
    }

    // -----------------------
    // Pizza retrieval
    // -----------------------

    public static ArrayList<Pizza> getPizzas(Order o)
            throws SQLException, IOException {
        if (o == null) return new ArrayList<>();
        if (!connectToDB()) return new ArrayList<>();

        ArrayList<Pizza> pizzas = new ArrayList<>();
        String query = "SELECT * FROM pizza " +
                "WHERE ordertable_OrderID = ? ORDER BY pizza_PizzaID";
        PreparedStatement ps = conn.prepareStatement(query);
        ps.setInt(1, o.getOrderID());
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            Pizza p = new Pizza(
                    rs.getInt("pizza_PizzaID"),
                    rs.getString("pizza_Size"),
                    rs.getString("pizza_CrustType"),
                    o.getOrderID(),
                    rs.getString("pizza_PizzaState"),
                    rs.getString("pizza_PizzaDate"),
                    rs.getDouble("pizza_CustPrice"),
                    rs.getDouble("pizza_BusPrice")
            );
            p.setToppings(getToppingsOnPizza(p));
            p.setDiscounts(getDiscountsPizza(p));
            pizzas.add(p);
        }
        rs.close();
        ps.close();
        return pizzas;
    }

    // -----------------------
    // Base price helpers
    // -----------------------

    public static double getBaseCustPrice(String size, String crust)
            throws SQLException, IOException {
        if (!connectToDB()) return 0.0;

        String query = "SELECT baseprice_CustPrice FROM baseprice " +
                "WHERE baseprice_Size = ? AND baseprice_CrustType = ?";
        PreparedStatement ps = conn.prepareStatement(query);
        ps.setString(1, size);
        ps.setString(2, crust);
        ResultSet rs = ps.executeQuery();
        double price = 0.0;
        if (rs.next()) {
            price = rs.getDouble("baseprice_CustPrice");
        }
        rs.close();
        ps.close();
        return price;
    }

    public static double getBaseBusPrice(String size, String crust)
            throws SQLException, IOException {
        if (!connectToDB()) return 0.0;

        String query = "SELECT baseprice_BusPrice FROM baseprice " +
                "WHERE baseprice_Size = ? AND baseprice_CrustType = ?";
        PreparedStatement ps = conn.prepareStatement(query);
        ps.setString(1, size);
        ps.setString(2, crust);
        ResultSet rs = ps.executeQuery();
        double price = 0.0;
        if (rs.next()) {
            price = rs.getDouble("baseprice_BusPrice");
        }
        rs.close();
        ps.close();
        return price;
    }

    // -----------------------
    // Reports (views)
    // -----------------------

    public static void printToppingReport() throws SQLException, IOException {
        if (!connectToDB()) return;

        String query = "SELECT * FROM ToppingPopularity";
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(query);

        System.out.printf("%-20s | %s%n", "Topping", "Count");
        System.out.printf("%-20s | %s%n", "--------------------", "-----");
        while (rs.next()) {
            System.out.printf("%-20s | %d%n",
                    rs.getString("Topping"),
                    rs.getInt("ToppingCount"));
        }
        rs.close();
        stmt.close();
    }

    public static void printProfitByPizzaReport()
            throws SQLException, IOException {
        if (!connectToDB()) return;

        String query = "SELECT * FROM ProfitByPizza";
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(query);

        System.out.printf("%-10s | %-15s | %-10s | %s%n",
                "Size", "Crust", "Profit", "Month");
        System.out.printf("%-10s | %-15s | %-10s | %s%n",
                "----------", "---------------", "----------", "----------");
        while (rs.next()) {
            System.out.printf("%-10s | %-15s | $%-9.2f | %s%n",
                    rs.getString("Size"),
                    rs.getString("Crust"),
                    rs.getDouble("Profit"),
                    rs.getString("OrderMonth"));
        }
        rs.close();
        stmt.close();
    }

    public static void printProfitByOrderTypeReport()
            throws SQLException, IOException {
        if (!connectToDB()) return;

        String query = "SELECT * FROM ProfitByOrderType";
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(query);

        System.out.printf("%-12s | %-12s | %-15s | %-15s | %s%n",
                "Order Type", "Month", "Total Price", "Total Cost", "Profit");
        System.out.printf("%-12s | %-12s | %-15s | %-15s | %s%n",
                "------------", "------------", "---------------", "---------------", "----------");
        while (rs.next()) {
            String type = rs.getString("CustomerType");
            if (type == null || type.isEmpty()) {
                type = "GRAND TOTAL";
            }
            System.out.printf("%-12s | %-12s | $%-14.2f | $%-14.2f | $%.2f%n",
                    type,
                    rs.getString("OrderMonth"),
                    rs.getDouble("TotalOrderPrice"),
                    rs.getDouble("TotalOrderCost"),
                    rs.getDouble("Profit"));
        }
        rs.close();
        stmt.close();
    }

    // -----------------------
    // Date helper + builder
    // -----------------------

    private static int getYear(String date) {
        return Integer.parseInt(date.substring(0, 4));
    }
    private static int getMonth(String date) {
        return Integer.parseInt(date.substring(5, 7));
    }
    private static int getDay(String date) {
        return Integer.parseInt(date.substring(8, 10));
    }

    public static boolean checkDate(int year, int month, int day, String dateOfOrder) {
        if (getYear(dateOfOrder) != year) return false;
        if (getMonth(dateOfOrder) != month) return false;
        return getDay(dateOfOrder) == day;
    }

    private static Order buildOrderFromResultSet(ResultSet rs)
            throws SQLException, IOException {
        int orderID = rs.getInt("ordertable_OrderID");
        String orderType = rs.getString("ordertable_OrderType");
        String orderDate = rs.getString("ordertable_OrderDateTime");
        double custPrice = rs.getDouble("ordertable_CustPrice");
        double busPrice = rs.getDouble("ordertable_BusPrice");
        boolean isComplete = rs.getInt("ordertable_IsComplete") == 1;
        int custID = rs.getInt("customer_CustID");
        if (rs.wasNull()) custID = -1;

        Order order = null;

        if (orderType.equals(dinein)) {
            String q = "SELECT dinein_TableNum FROM dinein WHERE ordertable_OrderID = ?";
            PreparedStatement ps = conn.prepareStatement(q);
            ps.setInt(1, orderID);
            ResultSet r2 = ps.executeQuery();
            if (r2.next()) {
                int tableNum = r2.getInt("dinein_TableNum");
                order = new DineinOrder(orderID, custID, orderDate,
                        custPrice, busPrice, isComplete, tableNum);
            }
            r2.close();
            ps.close();
        } else if (orderType.equals(pickup)) {
            String q = "SELECT pickup_IsPickedUp FROM pickup WHERE ordertable_OrderID = ?";
            PreparedStatement ps = conn.prepareStatement(q);
            ps.setInt(1, orderID);
            ResultSet r2 = ps.executeQuery();
            if (r2.next()) {
                boolean isPickedUp = r2.getInt("pickup_IsPickedUp") == 1;
                order = new PickupOrder(orderID, custID, orderDate,
                        custPrice, busPrice, isPickedUp, isComplete);
            }
            r2.close();
            ps.close();
        } else if (orderType.equals(delivery)) {
            String q = "SELECT * FROM delivery WHERE ordertable_OrderID = ?";
            PreparedStatement ps = conn.prepareStatement(q);
            ps.setInt(1, orderID);
            ResultSet r2 = ps.executeQuery();
            if (r2.next()) {
                String address = r2.getInt("delivery_HouseNum") + "\t" +
                        r2.getString("delivery_Street") + "\t" +
                        r2.getString("delivery_City") + "\t" +
                        r2.getString("delivery_State") + "\t" +
                        r2.getInt("delivery_Zip");
                boolean isDelivered = r2.getInt("delivery_IsDelivered") == 1;
                order = new DeliveryOrder(orderID, custID, orderDate,
                        custPrice, busPrice, isComplete, isDelivered, address);
            }
            r2.close();
            ps.close();
        }

        if (order != null) {
            order.setPizzaList(getPizzas(order));
            order.setDiscountList(getDiscounts(order));
        }

        return order;
    }

    // -----------------------
    // Stubbed ID helpers
    // -----------------------

    public static int getNextOrderID() { return -1; }
    public static int getNextPizzaID() { return -1; }
}
