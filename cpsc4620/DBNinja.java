package cpsc4620;

import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.Date;

/**
 * DBNinja.java
 *
 * Core DB helper class for the pizza project.
 */

public class DBNinja {

    // -------------------------
    // Constants used by Menu / domain classes
    // -------------------------

    // Pizza sizes
    public static final String size_s = "Small";
    public static final String size_m = "Medium";
    public static final String size_l = "Large";
    public static final String size_xl = "XLarge";

    // Crust types
    public static final String crust_thin = "Thin";
    public static final String crust_orig = "Original";
    public static final String crust_pan = "Pan";
    public static final String crust_gf = "Gluten-Free";

    // Order types (must match DB strings)
    public static final String dine_in = "dinein";
    public static final String pickup = "pickup";
    public static final String delivery = "delivery";

    // Order state enum used by Menu/autograder
    public enum order_state {
        PREPARED,
        PICKEDUP,
        DELIVERED,
        COMPLETED
    }

    // Single shared connection handle
    private static Connection conn = null;

    // -------------------------
    // Connection helper
    // -------------------------

    private static void connectToDB() throws SQLException, IOException {
        if (conn == null || conn.isClosed()) {
            conn = DBConnector.make_connection();
            if (conn == null) {
                throw new SQLException("Failed to establish database connection");
            }
        }
    }

    // -------------------------
    // Timestamp helper (strip trailing ".0")
    // -------------------------

    private static String normalizeTimestamp(Timestamp ts) {
        if (ts == null) return "";
        String raw = ts.toString(); // e.g. 2025-01-03 21:30:00.0
        if (raw.endsWith(".0")) {
            raw = raw.substring(0, raw.length() - 2);
        }
        return raw;
    }

    // -------------------------
    // addOrder
    // -------------------------

    public static void addOrder(Order o) throws SQLException, IOException {
        if (o == null) return;
        connectToDB();

        try {
            conn.setAutoCommit(false);

            // 1) Insert into ordertable
            String insertOrder =
                "INSERT INTO ordertable " +
                "(ordertable_OrderType, ordertable_OrderDateTime, " +
                " ordertable_CustPrice, ordertable_BusPrice, ordertable_IsComplete, customer_CustID) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(insertOrder, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, o.getOrderType());
                Timestamp ts;
                try {
                    ts = Timestamp.valueOf(o.getDate());
                } catch (IllegalArgumentException e) {
                    ts = new Timestamp(System.currentTimeMillis());
                }
                ps.setTimestamp(2, ts);
                ps.setDouble(3, o.getCustPrice());
                ps.setDouble(4, o.getBusPrice());
                ps.setBoolean(5, o.getIsComplete());
                if (o.getCustID() <= 0) {
                    ps.setNull(6, Types.INTEGER);
                } else {
                    ps.setInt(6, o.getCustID());
                }
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        int newOrderID = keys.getInt(1);
                        o.setOrderID(newOrderID);
                    }
                }
            }

            int orderID = o.getOrderID();

            // 2) Insert subtype row
            if (o.getOrderType().equals(dine_in) && o instanceof DineinOrder) {
                DineinOrder d = (DineinOrder) o;
                String sql = "INSERT INTO dinein (ordertable_OrderID, dinein_TableNum) VALUES (?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, orderID);
                    ps.setInt(2, d.getTableNum());
                    ps.executeUpdate();
                }
            } else if (o.getOrderType().equals(pickup)) {
                boolean pickedUp = (o instanceof PickupOrder) && ((PickupOrder) o).getIsPickedUp();
                String sql = "INSERT INTO pickup (ordertable_OrderID, pickup_IsPickedUp) VALUES (?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, orderID);
                    ps.setBoolean(2, pickedUp);
                    ps.executeUpdate();
                }
            } else if (o.getOrderType().equals(delivery) && o instanceof DeliveryOrder) {
                DeliveryOrder d = (DeliveryOrder) o;
                String addr = d.getAddress();
                int houseNum = 0;
                String street = "";
                String city = "";
                String state = "";
                int zip = 0;
                boolean delivered = d.getIsDelivered();
                if (addr != null) {
                    // Address format used in Menu: "house\tstreet\tcity\tstate\tzip"
                    String[] parts = addr.split("\\t");
                    if (parts.length >= 5) {
                        try {
                            houseNum = Integer.parseInt(parts[0].trim());
                        } catch (NumberFormatException ignored) {}
                        street = parts[1];
                        city = parts[2];
                        state = parts[3];
                        try {
                            zip = Integer.parseInt(parts[4].trim());
                        } catch (NumberFormatException ignored) {}
                    }
                }
                String sql =
                    "INSERT INTO delivery " +
                    "(ordertable_OrderID, delivery_HouseNum, delivery_Street, delivery_City, " +
                    " delivery_State, delivery_Zip, delivery_IsDelivered) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, orderID);
                    ps.setInt(2, houseNum);
                    ps.setString(3, street);
                    ps.setString(4, city);
                    ps.setString(5, state);
                    ps.setInt(6, zip);
                    ps.setBoolean(7, delivered);
                    ps.executeUpdate();
                }
            }

            // 3) Insert pizzas
            for (Pizza p : o.getPizzaList()) {
                Timestamp pts;
                try {
                    pts = Timestamp.valueOf(p.getPizzaDate());
                } catch (IllegalArgumentException e) {
                    pts = new Timestamp(System.currentTimeMillis());
                }
                Date d = new Date(pts.getTime());
                addPizza(d, orderID, p);
            }

            // 4) Order-level discounts
            for (Discount d : o.getDiscountList()) {
                String sql = "INSERT INTO order_discount (ordertable_OrderID, discount_DiscountID) VALUES (?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, orderID);
                    ps.setInt(2, d.getDiscountID());
                    ps.executeUpdate();
                }
            }

            conn.commit();
        } catch (SQLException e) {
            if (conn != null) conn.rollback();
            throw e;
        } finally {
            if (conn != null) conn.setAutoCommit(true);
        }
    }

    // -------------------------
    // addPizza
    // -------------------------

    public static int addPizza(Date d, int orderID, Pizza p) throws SQLException, IOException {
        if (p == null) return -1;
        connectToDB();

        int pizzaID = -1;

        // 1) Insert pizza row
        String insertPizza =
            "INSERT INTO pizza " +
            "(ordertable_OrderID, pizza_Size, pizza_CrustType, pizza_PizzaState, " +
            " pizza_PizzaDate, pizza_CustPrice, pizza_BusPrice) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(insertPizza, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, orderID);
            ps.setString(2, p.getSize());
            ps.setString(3, p.getCrustType());
            ps.setString(4, p.getPizzaState());
            Timestamp ts = new Timestamp(d.getTime());
            ps.setTimestamp(5, ts);
            ps.setDouble(6, p.getCustPrice());
            ps.setDouble(7, p.getBusPrice());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    pizzaID = keys.getInt(1);
                    p.setPizzaID(pizzaID);
                }
            }
        }

        // 2) Toppings (and inventory update)
        for (Topping t : p.getToppings()) {
            int isDouble = t.getDoubled() ? 1 : 0;
            String insertTop =
                "INSERT INTO pizza_topping " +
                "(pizza_PizzaID, topping_TopID, pizza_topping_IsDouble) " +
                "VALUES (?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(insertTop)) {
                ps.setInt(1, pizzaID);
                ps.setInt(2, t.getTopID());
                ps.setInt(3, isDouble);
                ps.executeUpdate();
            }

            // compute units used for inventory
            double unitsNeeded;
            String size = p.getSize();
            if (size.equals(size_s)) {
                unitsNeeded = t.getSmallAMT();
            } else if (size.equals(size_m)) {
                unitsNeeded = t.getMedAMT();
            } else if (size.equals(size_l)) {
                unitsNeeded = t.getLgAMT();
            } else {
                unitsNeeded = t.getXLAMT();
            }

            if (t.getDoubled()) {
                unitsNeeded *= 2.0;
            }

            String updateInv =
                "UPDATE topping SET topping_CurINVT = topping_CurINVT - ? WHERE topping_TopID = ?";
            try (PreparedStatement ps = conn.prepareStatement(updateInv)) {
                ps.setDouble(1, unitsNeeded);
                ps.setInt(2, t.getTopID());
                ps.executeUpdate();
            }
        }

        // 3) Pizza discounts
        for (Discount dsc : p.getDiscounts()) {
            String insertDisc =
                "INSERT INTO pizza_discount (pizza_PizzaID, discount_DiscountID) VALUES (?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(insertDisc)) {
                ps.setInt(1, pizzaID);
                ps.setInt(2, dsc.getDiscountID());
                ps.executeUpdate();
            }
        }

        return pizzaID;
    }

    // -------------------------
    // addCustomer
    // -------------------------

    public static int addCustomer(Customer c) throws SQLException, IOException {
        if (c == null) return -1;
        connectToDB();

        String sql =
            "INSERT INTO customer (customer_FName, customer_LName, customer_PhoneNum) " +
            "VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, c.getFName());
            ps.setString(2, c.getLName());
            ps.setString(3, c.getPhone());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    int id = keys.getInt(1);
                    c.setCustID(id);
                    return id;
                }
            }
        }

        return -1;
    }

    // -------------------------
    // completeOrder
    // -------------------------

    public static void completeOrder(int orderID, order_state state) throws SQLException, IOException {
        connectToDB();

        // Always mark as complete
        String updateOrder =
            "UPDATE ordertable SET ordertable_IsComplete = 1 WHERE ordertable_OrderID = ?";
        try (PreparedStatement ps = conn.prepareStatement(updateOrder)) {
            ps.setInt(1, orderID);
            ps.executeUpdate();
        }

        // Mark pickup/delivery based on STATE parameter
        String typeSql =
            "SELECT ordertable_OrderType FROM ordertable WHERE ordertable_OrderID = ?";
        try (PreparedStatement ps = conn.prepareStatement(typeSql)) {
            ps.setInt(1, orderID);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String type = rs.getString(1);

                    // Only mark pickup_IsPickedUp if state is PICKEDUP or COMPLETED
                    if (pickup.equals(type) && (state == order_state.PICKEDUP || state == order_state.COMPLETED)) {
                        String updPickup =
                            "UPDATE pickup SET pickup_IsPickedUp = 1 WHERE ordertable_OrderID = ?";
                        try (PreparedStatement ps2 = conn.prepareStatement(updPickup)) {
                            ps2.setInt(1, orderID);
                            ps2.executeUpdate();
                        }
                    }

                    // Only mark delivery_IsDelivered if state is DELIVERED or COMPLETED
                    if (delivery.equals(type) && (state == order_state.DELIVERED || state == order_state.COMPLETED)) {
                        String updDelivery =
                            "UPDATE delivery SET delivery_IsDelivered = 1 WHERE ordertable_OrderID = ?";
                        try (PreparedStatement ps2 = conn.prepareStatement(updDelivery)) {
                            ps2.setInt(1, orderID);
                            ps2.executeUpdate();
                        }
                    }
                }
            }
        }
    }

    // -------------------------
    // getOrders / getLastOrder / getOrdersByDate
    // -------------------------

    // status: 1=open, 2=closed, 3=all
    public static ArrayList getOrders(int status) throws SQLException, IOException {
        connectToDB();
        ArrayList orders = new ArrayList<>();
        String sql = "SELECT * FROM ordertable";

        if (status == 1) {
            sql += " WHERE ordertable_IsComplete = 0";
        } else if (status == 2) {
            sql += " WHERE ordertable_IsComplete = 1";
        }

        // ensure deterministic ordering for autograder
        sql += " ORDER BY ordertable_OrderDateTime ASC, ordertable_OrderID ASC";

        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                Order o = buildOrderFromResultSet(rs);
                orders.add(o);
            }
        }

        return orders;
    }

    public static Order getLastOrder() throws SQLException, IOException {
        connectToDB();
        String sql =
            "SELECT * FROM ordertable " +
            "ORDER BY ordertable_OrderDateTime DESC, ordertable_OrderID DESC " +
            "LIMIT 1";
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) {
                return buildOrderFromResultSet(rs);
            }
        }

        return null;
    }

    public static ArrayList getOrdersByDate(String date) throws SQLException, IOException {
        connectToDB();
        ArrayList orders = new ArrayList<>();
        String sql =
            "SELECT * FROM ordertable " +
            "WHERE DATE(ordertable_OrderDateTime) = ? " +
            "ORDER BY ordertable_OrderDateTime ASC, ordertable_OrderID ASC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, date);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    orders.add(buildOrderFromResultSet(rs));
                }
            }
        }

        return orders;
    }

    // -------------------------
    // Discounts
    // -------------------------

    public static ArrayList getDiscountList() throws SQLException, IOException {
        connectToDB();
        ArrayList list = new ArrayList<>();
        String sql =
            "SELECT discount_DiscountID, discount_DiscountName, discount_Amount, discount_IsPercent " +
            "FROM discount " +
            "ORDER BY discount_DiscountName ASC";   // order by name
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                Discount d = new Discount(
                    rs.getInt("discount_DiscountID"),
                    rs.getString("discount_DiscountName"),
                    rs.getDouble("discount_Amount"),
                    rs.getBoolean("discount_IsPercent")
                );
                list.add(d);
            }
        }

        return list;
    }

    public static Discount findDiscountByName(String name) throws SQLException, IOException {
        connectToDB();
        String sql =
            "SELECT discount_DiscountID, discount_DiscountName, discount_Amount, discount_IsPercent " +
            "FROM discount WHERE discount_DiscountName = ? LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Discount(
                        rs.getInt("discount_DiscountID"),
                        rs.getString("discount_DiscountName"),
                        rs.getDouble("discount_Amount"),
                        rs.getBoolean("discount_IsPercent")
                    );
                }
            }
        }

        return null;
    }

    public static ArrayList getDiscounts(Order o) throws SQLException, IOException {
        connectToDB();
        ArrayList list = new ArrayList<>();
        String sql =
            "SELECT d.discount_DiscountID, d.discount_DiscountName, d.discount_Amount, d.discount_IsPercent " +
            "FROM discount d " +
            "JOIN order_discount od ON d.discount_DiscountID = od.discount_DiscountID " +
            "WHERE od.ordertable_OrderID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, o.getOrderID());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Discount d = new Discount(
                        rs.getInt("discount_DiscountID"),
                        rs.getString("discount_DiscountName"),
                        rs.getDouble("discount_Amount"),
                        rs.getBoolean("discount_IsPercent")
                    );
                    list.add(d);
                }
            }
        }

        return list;
    }

    public static ArrayList getDiscounts(Pizza p) throws SQLException, IOException {
        connectToDB();
        ArrayList list = new ArrayList<>();
        String sql =
            "SELECT d.discount_DiscountID, d.discount_DiscountName, d.discount_Amount, d.discount_IsPercent " +
            "FROM discount d " +
            "JOIN pizza_discount pd ON d.discount_DiscountID = pd.discount_DiscountID " +
            "WHERE pd.pizza_PizzaID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, p.getPizzaID());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Discount d = new Discount(
                        rs.getInt("discount_DiscountID"),
                        rs.getString("discount_DiscountName"),
                        rs.getDouble("discount_Amount"),
                        rs.getBoolean("discount_IsPercent")
                    );
                    list.add(d);
                }
            }
        }

        return list;
    }

    // -------------------------
    // Customers
    // -------------------------

    public static ArrayList getCustomerList() throws SQLException, IOException {
        connectToDB();
        ArrayList list = new ArrayList<>();
        String sql =
            "SELECT customer_CustID, customer_FName, customer_LName, customer_PhoneNum " +
            "FROM customer " +
            "ORDER BY customer_LName ASC, customer_FName ASC";  // order by last, first
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                Customer c = new Customer(
                    rs.getInt("customer_CustID"),
                    rs.getString("customer_FName"),
                    rs.getString("customer_LName"),
                    rs.getString("customer_PhoneNum")
                );
                list.add(c);
            }
        }

        return list;
    }

    public static Customer findCustomerByPhone(String phoneNumber) throws SQLException, IOException {
        connectToDB();
        String sql =
            "SELECT customer_CustID, customer_FName, customer_LName, customer_PhoneNum " +
            "FROM customer WHERE customer_PhoneNum = ? LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, phoneNumber);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Customer(
                        rs.getInt("customer_CustID"),
                        rs.getString("customer_FName"),
                        rs.getString("customer_LName"),
                        rs.getString("customer_PhoneNum")
                    );
                }
            }
        }

        return null;
    }

    public static String getCustomerName(int custID) throws SQLException, IOException {
        // In-store / dine-in orders
        if (custID <= 0) {
            return "IN STORE";
        }

        connectToDB();
        String name = "IN STORE";
        String sql = "SELECT customer_FName, customer_LName FROM customer WHERE customer_CustID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, custID);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    name = rs.getString("customer_FName") + " " + rs.getString("customer_LName");
                }
            }
        }

        return name;
    }

    // -------------------------
    // Toppings / Inventory
    // -------------------------

    public static ArrayList getToppingList() throws SQLException, IOException {
        connectToDB();
        ArrayList list = new ArrayList<>();
        String sql = "SELECT * FROM topping ORDER BY topping_TopID ASC";
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
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
                list.add(t);
            }
        }

        return list;
    }

    public static Topping findToppingByName(String name) throws SQLException, IOException {
        connectToDB();
        String sql = "SELECT * FROM topping WHERE topping_TopName = ? LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
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
                        rs.getInt("topping_CurINVT")
                    );
                }
            }
        }

        return null;
    }

    public static ArrayList getToppingsOnPizza(Pizza p) throws SQLException, IOException {
        connectToDB();
        ArrayList list = new ArrayList<>();
        String sql =
            "SELECT t.*, pt.pizza_topping_IsDouble " +
            "FROM topping t " +
            "JOIN pizza_topping pt ON t.topping_TopID = pt.topping_TopID " +
            "WHERE pt.pizza_PizzaID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, p.getPizzaID());
            try (ResultSet rs = ps.executeQuery()) {
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
                    int isDouble = rs.getInt("pizza_topping_IsDouble");
                    t.setDoubled(isDouble == 1);
                    list.add(t);
                }
            }
        }

        return list;
    }

    public static void addToInventory(int toppingID, double amount) throws SQLException, IOException {
        connectToDB();
        String sql =
            "UPDATE topping SET topping_CurINVT = topping_CurINVT + ? WHERE topping_TopID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, amount);
            ps.setInt(2, toppingID);
            ps.executeUpdate();
        }
    }

    // -------------------------
    // getPizzas
    // -------------------------

    public static ArrayList getPizzas(Order o) throws SQLException, IOException {
        connectToDB();
        ArrayList list = new ArrayList<>();
        String sql =
            "SELECT * FROM pizza WHERE ordertable_OrderID = ? ORDER BY pizza_PizzaID ASC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, o.getOrderID());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int pizzaID = rs.getInt("pizza_PizzaID");
                    String size = rs.getString("pizza_Size");
                    String crust = rs.getString("pizza_CrustType");
                    int orderID = rs.getInt("ordertable_OrderID");
                    String state = rs.getString("pizza_PizzaState");
                    Timestamp ts = rs.getTimestamp("pizza_PizzaDate");
                    String dateStr = normalizeTimestamp(ts);
                    double custPrice = rs.getDouble("pizza_CustPrice");
                    double busPrice = rs.getDouble("pizza_BusPrice");

                    // pass size then crust to match Pizza constructor
                    Pizza p = new Pizza(pizzaID, size, crust, orderID, state, dateStr, custPrice, busPrice);
                    p.setToppings(getToppingsOnPizza(p));
                    p.setDiscounts(getDiscounts(p));
                    list.add(p);
                }
            }
        }

        return list;
    }

    // -------------------------
    // Base prices
    // -------------------------

    public static double getBaseCustPrice(String size, String crust) throws SQLException, IOException {
        connectToDB();
        String sql =
            "SELECT baseprice_CustPrice FROM baseprice " +
            "WHERE baseprice_Size = ? AND baseprice_CrustType = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, size);
            ps.setString(2, crust);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble("baseprice_CustPrice");
                }
            }
        }

        return 0.0;
    }

    public static double getBaseBusPrice(String size, String crust) throws SQLException, IOException {
        connectToDB();
        String sql =
            "SELECT baseprice_BusPrice FROM baseprice " +
            "WHERE baseprice_Size = ? AND baseprice_CrustType = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, size);
            ps.setString(2, crust);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble("baseprice_BusPrice");
                }
            }
        }

        return 0.0;
    }

    // -------------------------
    // Reports (views)
    // -------------------------

    public static void printToppingReport() throws SQLException, IOException {
        connectToDB();
        String sql = "SELECT Topping, ToppingCount FROM ToppingPopularity";
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            System.out.printf("%-20s%-15s%n", "Topping", "Topping Count");
            System.out.printf("%-20s%-15s%n", "-------", "-------------");
            while (rs.next()) {
                String name = rs.getString("Topping");
                int count = rs.getInt("ToppingCount");
                System.out.printf("%-20s%-15d%n", name, count);
            }
        }
    }

    public static void printProfitByPizzaReport() throws SQLException, IOException {
        connectToDB();
        String sql = "SELECT Size, Crust, Profit, OrderMonth FROM ProfitByPizza";
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            System.out.printf("%-10s%-15s%-15s%-15s%n", "Size", "Crust", "Profit", "OrderMonth");
            System.out.printf("%-10s%-15s%-15s%-15s%n", "----", "-----", "------", "----------");
            while (rs.next()) {
                String size = rs.getString("Size");
                String crust = rs.getString("Crust");
                double profit = rs.getDouble("Profit");
                String month = rs.getString("OrderMonth");
                System.out.printf("%-10s%-15s%-15.2f%-15s%n", size, crust, profit, month);
            }
        }
    }

    public static void printProfitByOrderTypeReport() throws SQLException, IOException {
        connectToDB();
        String sql =
            "SELECT CustomerType, OrderMonth, TotalOrderPrice, TotalOrderCost, Profit " +
            "FROM ProfitByOrderType";
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            System.out.printf("%-12s%-15s%-18s%-18s%-15s%n",
                "Type", "Month", "TotalOrderPrice", "TotalOrderCost", "Profit");
            System.out.printf("%-12s%-15s%-18s%-18s%-15s%n",
                "----", "-----", "---------------", "--------------", "------");
            while (rs.next()) {
                String type = rs.getString("CustomerType");
                String month = rs.getString("OrderMonth");
                double top = rs.getDouble("TotalOrderPrice");
                double toc = rs.getDouble("TotalOrderCost");
                double profit = rs.getDouble("Profit");
                System.out.printf("%-12s%-15s%-18.2f%-18.2f%-15.2f%n",
                    type, month, top, toc, profit);
            }
        }
    }

    // -------------------------
    // Helper to rebuild an Order from a ResultSet row
    // -------------------------

    private static Order buildOrderFromResultSet(ResultSet rs) throws SQLException, IOException {
        int orderID = rs.getInt("ordertable_OrderID");
        String orderType = rs.getString("ordertable_OrderType");
        Timestamp ts = rs.getTimestamp("ordertable_OrderDateTime");
        String dateStr = normalizeTimestamp(ts);
        double custPrice = rs.getDouble("ordertable_CustPrice");
        double busPrice = rs.getDouble("ordertable_BusPrice");
        boolean isComplete = rs.getBoolean("ordertable_IsComplete");
        int custID = rs.getInt("customer_CustID");
        if (rs.wasNull()) {
            custID = -1;
        }

        Order order;
        if (orderType.equals(dine_in)) {
            int tableNum = 0;
            String sql = "SELECT dinein_TableNum FROM dinein WHERE ordertable_OrderID = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, orderID);
                try (ResultSet rs2 = ps.executeQuery()) {
                    if (rs2.next()) {
                        tableNum = rs2.getInt("dinein_TableNum");
                    }
                }
            }
            order = new DineinOrder(orderID, custID, dateStr, custPrice, busPrice, isComplete, tableNum);
        } else if (orderType.equals(pickup)) {
            boolean pickedUp = false;
            String sql = "SELECT pickup_IsPickedUp FROM pickup WHERE ordertable_OrderID = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, orderID);
                try (ResultSet rs2 = ps.executeQuery()) {
                    if (rs2.next()) {
                        pickedUp = rs2.getBoolean("pickup_IsPickedUp");
                    }
                }
            }
            order = new PickupOrder(orderID, custID, dateStr, custPrice, busPrice, pickedUp, isComplete);
        } else if (orderType.equals(delivery)) {
            int houseNum = 0;
            String street = "";
            String city = "";
            String state = "";
            int zip = 0;
            boolean delivered = false;
            String sql =
                "SELECT delivery_HouseNum, delivery_Street, delivery_City, " +
                "delivery_State, delivery_Zip, delivery_IsDelivered " +
                "FROM delivery WHERE ordertable_OrderID = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, orderID);
                try (ResultSet rs2 = ps.executeQuery()) {
                    if (rs2.next()) {
                        houseNum = rs2.getInt("delivery_HouseNum");
                        street = rs2.getString("delivery_Street");
                        city = rs2.getString("delivery_City");
                        state = rs2.getString("delivery_State");
                        zip = rs2.getInt("delivery_Zip");
                        delivered = rs2.getBoolean("delivery_IsDelivered");
                    }
                }
            }
            String addr = houseNum + "\t" + street + "\t" + city + "\t" + state + "\t" + zip;
            DeliveryOrder d = new DeliveryOrder(orderID, custID, dateStr, custPrice, busPrice, isComplete, addr);
            d.setIsDelivered(delivered);
            order = d;
        } else {
            // fallback generic order if type is weird
            order = new Order(orderID, custID, orderType, dateStr, custPrice, busPrice, isComplete);
        }

        // Attach pizzas & discounts
        order.setPizzaList(getPizzas(order));
        order.setDiscountList(getDiscounts(order));
        return order;
    }

    // -------------------------
    // Optional helpers (not used by Menu but safe to have)
    // -------------------------

    public static int getNextOrderID() {
        return -1;
    }

    public static int getNextPizzaID() {
        return -1;
    }

    /*
     * autograder controls....do not modify!
     */
    public final static String autograder_seed = "6f1b7ea9aac470402c48f7916ea6a010";

    private static void autograder_compilation_check() {
        try {
            Order o = null;
            Pizza p = null;
            Topping t = null;
            Discount d = null;
            Customer c = null;
            ArrayList alo = null;
            ArrayList ald = null;
            ArrayList alc = null;
            ArrayList alt = null;
            ArrayList alp = null;
            double v = 0.0;
            String s = "";
            int id = -1;
            Date dts = new Date();
            DBNinja.addOrder(o);
            DBNinja.addPizza(dts, id, p);
            id = DBNinja.addCustomer(c);
            DBNinja.completeOrder(o.getOrderID(), DBNinja.order_state.PREPARED);
            alo = DBNinja.getOrders(1);
            o = DBNinja.getLastOrder();
            alo = DBNinja.getOrdersByDate("01/01/1999");
            ald = DBNinja.getDiscountList();
            d = DBNinja.findDiscountByName("Discount");
            alc = DBNinja.getCustomerList();
            c = DBNinja.findCustomerByPhone("0000000000");
            s = DBNinja.getCustomerName(0);
            alt = DBNinja.getToppingList();
            t = DBNinja.findToppingByName("Topping");
            alt = DBNinja.getToppingsOnPizza(p);
            DBNinja.addToInventory(id, 1000.0);
            alp = DBNinja.getPizzas(o);
            ald = DBNinja.getDiscounts(o);
            ald = DBNinja.getDiscounts(p);
            v = DBNinja.getBaseCustPrice("size", "crust");
            v = DBNinja.getBaseBusPrice("size", "crust");
            DBNinja.printToppingReport();
            DBNinja.printProfitByPizzaReport();
            DBNinja.printProfitByOrderTypeReport();
        } catch (SQLException | IOException e) {
            e.printStackTrace();
        }
    }
}
