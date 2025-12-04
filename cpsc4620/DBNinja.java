package cpsc4620;

import java.io.IOException;
import java.sql.*;
import java.util.*;

/**
 * DBNinja
 * All database access for the Pizza project goes through here.
 */
public final class DBNinja {

    // --- Connection handling -------------------------------------------------

    private static Connection connectToDB() throws SQLException, IOException {
        Connection conn = DBConnector.make_connection();
        if (conn == null) {
            throw new SQLException("Unable to connect to database");
        }
        return conn;
    }

    // --- Constants used throughout the project ------------------------------

    public static final String pickup   = "pickup";
    public static final String delivery = "delivery";
    public static final String dine_in  = "dinein";

    public static final String size_s  = "Small";
    public static final String size_m  = "Medium";
    public static final String size_l  = "Large";
    public static final String size_xl = "XLarge";

    public static final String crust_thin = "Thin";
    public static final String crust_orig = "Original";
    public static final String crust_pan  = "Pan";
    public static final String crust_gf   = "Gluten-Free";

    public enum order_state { PREPARED, DELIVERED, PICKEDUP }

    // --- INSERTS -------------------------------------------------------------

    public static void addOrder(Order o) throws SQLException, IOException {
        try (Connection conn = connectToDB()) {
            // insert into ordertable
            String sql = "INSERT INTO ordertable " +
                    "(ordertable_OrderType, ordertable_OrderDateTime, " +
                    " ordertable_CustPrice, ordertable_BusPrice, " +
                    " ordertable_IsComplete, customer_CustID) " +
                    "VALUES (?,?,?,?,?,?)";
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
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

                int orderID;
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    orderID = rs.next() ? rs.getInt(1) : -1;
                }
                o.setOrderID(orderID);

                // subtype tables
                if (o instanceof DineinOrder) {
                    DineinOrder d = (DineinOrder) o;
                    String q = "INSERT INTO dinein (ordertable_OrderID, dinein_TableNum) VALUES (?,?)";
                    try (PreparedStatement ps2 = conn.prepareStatement(q)) {
                        ps2.setInt(1, orderID);
                        ps2.setInt(2, d.getTableNum());
                        ps2.executeUpdate();
                    }
                } else if (o instanceof PickupOrder) {
                    PickupOrder p = (PickupOrder) o;
                    String q = "INSERT INTO pickup (ordertable_OrderID, pickup_IsPickedUp) VALUES (?,?)";
                    try (PreparedStatement ps2 = conn.prepareStatement(q)) {
                        ps2.setInt(1, orderID);
                        ps2.setInt(2, p.getIsPickedUp() ? 1 : 0);
                        ps2.executeUpdate();
                    }
                } else if (o instanceof DeliveryOrder) {
                    DeliveryOrder d = (DeliveryOrder) o;
                    String addr = d.getAddress();
                    // Menu builds address as "houseNum\tstreet\tcity\tstate\tzip"
                    String[] parts = addr.split("\\t");
                    int houseNum = (parts.length > 0 && !parts[0].isEmpty()) ? Integer.parseInt(parts[0]) : 0;
                    String street = parts.length > 1 ? parts[1] : "";
                    String city   = parts.length > 2 ? parts[2] : "";
                    String state  = parts.length > 3 ? parts[3] : "";
                    int zip       = (parts.length > 4 && !parts[4].isEmpty()) ? Integer.parseInt(parts[4]) : 0;

                    String q = "INSERT INTO delivery " +
                            "(ordertable_OrderID, delivery_HouseNum, delivery_Street, " +
                            " delivery_City, delivery_State, delivery_Zip, delivery_IsDelivered) " +
                            "VALUES (?,?,?,?,?,?,?)";
                    try (PreparedStatement ps2 = conn.prepareStatement(q)) {
                        ps2.setInt(1, orderID);
                        ps2.setInt(2, houseNum);
                        ps2.setString(3, street);
                        ps2.setString(4, city);
                        ps2.setString(5, state);
                        ps2.setInt(6, zip);
                        ps2.setInt(7, d.toString().contains("Yes") ? 1 : 0); // initial false in Menu
                        ps2.executeUpdate();
                    }
                }

                // pizzas
                for (Pizza p : o.getPizzaList()) {
                    addPizzaInternal(conn, new java.util.Date(), orderID, p);
                }

                // order-level discounts
                String dq = "INSERT INTO order_discount (ordertable_OrderID, discount_DiscountID) VALUES (?,?)";
                for (Discount d : o.getDiscountList()) {
                    try (PreparedStatement dps = conn.prepareStatement(dq)) {
                        dps.setInt(1, orderID);
                        dps.setInt(2, d.getDiscountID());
                        dps.executeUpdate();
                    }
                }
            }
        }
    }

    public static int addPizza(java.util.Date d, int orderID, Pizza p) throws SQLException, IOException {
        try (Connection conn = connectToDB()) {
            return addPizzaInternal(conn, d, orderID, p);
        }
    }

    // Helper that reuses a provided connection
    private static int addPizzaInternal(Connection conn, java.util.Date d, int orderID, Pizza p) throws SQLException {
        String sql = "INSERT INTO pizza " +
                "(ordertable_OrderID, pizza_Size, pizza_CrustType, " +
                " pizza_PizzaState, pizza_PizzaDate, pizza_CustPrice, pizza_BusPrice) " +
                "VALUES (?,?,?,?,?,?,?)";
        int pizzaID;

        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, orderID);
            ps.setString(2, p.getSize());
            ps.setString(3, p.getCrustType());
            // New pizzas start "In Progress"
            ps.setString(4, p.getPizzaState());
            ps.setTimestamp(5, new Timestamp(d.getTime()));
            ps.setDouble(6, p.getCustPrice());
            ps.setDouble(7, p.getBusPrice());
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                pizzaID = rs.next() ? rs.getInt(1) : -1;
            }
        }

        p.setPizzaID(pizzaID);
        p.setOrderID(orderID);

        // Merge duplicate toppings into one row per TopID, with double flag if any selection was doubled
        Map<Integer, Integer> topMap = new LinkedHashMap<>();
        for (Topping t : p.getToppings()) {
            int id = t.getTopID();
            int dbl = t.getDoubled() ? 1 : 0;
            Integer existing = topMap.get(id);
            if (existing == null) {
                topMap.put(id, dbl);
            } else {
                topMap.put(id, Math.max(existing, dbl));
            }
        }

        String topsql = "INSERT INTO pizza_topping (pizza_PizzaID, topping_TopID, pizza_topping_IsDouble) " +
                        "VALUES (?,?,?)";
        try (PreparedStatement tps = conn.prepareStatement(topsql)) {
            for (Map.Entry<Integer, Integer> e : topMap.entrySet()) {
                tps.setInt(1, pizzaID);
                tps.setInt(2, e.getKey());
                tps.setInt(3, e.getValue());
                tps.executeUpdate();
            }
        }

        String dsql = "INSERT INTO pizza_discount (pizza_PizzaID, discount_DiscountID) VALUES (?,?)";
        try (PreparedStatement dps = conn.prepareStatement(dsql)) {
            for (Discount disc : p.getDiscounts()) {
                dps.setInt(1, pizzaID);
                dps.setInt(2, disc.getDiscountID());
                dps.executeUpdate();
            }
        }

        return pizzaID;
    }

    public static int addCustomer(Customer c) throws SQLException, IOException {
        String sql = "INSERT INTO customer (customer_FName, customer_LName, customer_PhoneNum) " +
                     "VALUES (?,?,?)";
        try (Connection conn = connectToDB();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, c.getFName());
            ps.setString(2, c.getLName());
            ps.setString(3, c.getPhone());
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                int id = rs.next() ? rs.getInt(1) : -1;
                c.setCustID(id);
                return id;
            }
        }
    }

    // --- ORDER COMPLETION ----------------------------------------------------

    public static void completeOrder(int orderID, order_state newState) throws SQLException, IOException {
        try (Connection conn = connectToDB()) {

            if (newState == order_state.PREPARED) {
                // mark order complete
                String q = "UPDATE ordertable SET ordertable_IsComplete = 1 WHERE ordertable_OrderID = ?";
                try (PreparedStatement ps = conn.prepareStatement(q)) {
                    ps.setInt(1, orderID);
                    ps.executeUpdate();
                }
                // and all pizzas as completed
                String q2 = "UPDATE pizza SET pizza_PizzaState = 'completed' WHERE ordertable_OrderID = ?";
                try (PreparedStatement ps2 = conn.prepareStatement(q2)) {
                    ps2.setInt(1, orderID);
                    ps2.executeUpdate();
                }
            } else if (newState == order_state.DELIVERED) {
                String q = "UPDATE delivery SET delivery_IsDelivered = 1 WHERE ordertable_OrderID = ?";
                try (PreparedStatement ps = conn.prepareStatement(q)) {
                    ps.setInt(1, orderID);
                    ps.executeUpdate();
                }
            } else if (newState == order_state.PICKEDUP) {
                String q = "UPDATE pickup SET pickup_IsPickedUp = 1 WHERE ordertable_OrderID = ?";
                try (PreparedStatement ps = conn.prepareStatement(q)) {
                    ps.setInt(1, orderID);
                    ps.executeUpdate();
                }
            }
        }
    }

    // --- SELECTS: Orders -----------------------------------------------------

    public static ArrayList<Order> getOrders(int status) throws SQLException, IOException {
        ArrayList<Order> list = new ArrayList<>();
        StringBuilder sb = new StringBuilder("SELECT * FROM ordertable");
        if (status == 1) {           // open
            sb.append(" WHERE ordertable_IsComplete = 0");
        } else if (status == 2) {    // complete
            sb.append(" WHERE ordertable_IsComplete = 1");
        }
        sb.append(" ORDER BY ordertable_OrderID");

        try (Connection conn = connectToDB();
             PreparedStatement ps = conn.prepareStatement(sb.toString());
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Order o = buildOrderFromRow(rs);
                if (o != null) {
                    list.add(o);
                }
            }
        }
        return list;
    }

    public static Order getLastOrder() throws SQLException, IOException {
        String sql = "SELECT * FROM ordertable ORDER BY ordertable_OrderID DESC LIMIT 1";
        try (Connection conn = connectToDB();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                return buildOrderFromRow(rs);
            }
        }
        return null;
    }

    public static ArrayList<Order> getOrdersByDate(String date) throws SQLException, IOException {
        ArrayList<Order> list = new ArrayList<>();
        String sql = "SELECT * FROM ordertable " +
                     "WHERE DATE(ordertable_OrderDateTime) = ? " +
                     "ORDER BY ordertable_OrderID";
        try (Connection conn = connectToDB();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, date);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Order o = buildOrderFromRow(rs);
                    if (o != null) list.add(o);
                }
            }
        }
        return list;
    }

    private static Order buildOrderFromRow(ResultSet rs) throws SQLException, IOException {
        int orderID   = rs.getInt("ordertable_OrderID");
        String type   = rs.getString("ordertable_OrderType");
        String date   = rs.getString("ordertable_OrderDateTime");
        double cPrice = rs.getDouble("ordertable_CustPrice");
        double bPrice = rs.getDouble("ordertable_BusPrice");
        boolean complete = rs.getInt("ordertable_IsComplete") == 1;
        int custID   = rs.getInt("customer_CustID");

        Order o = null;

        if (dine_in.equals(type)) {
            String q = "SELECT dinein_TableNum FROM dinein WHERE ordertable_OrderID = ?";
            try (Connection conn = connectToDB();
                 PreparedStatement ps = conn.prepareStatement(q)) {
                ps.setInt(1, orderID);
                try (ResultSet r2 = ps.executeQuery()) {
                    if (r2.next()) {
                        int tableNum = r2.getInt("dinein_TableNum");
                        o = new DineinOrder(orderID, -1, date, cPrice, bPrice, complete, tableNum);
                    }
                }
            }
        } else if (pickup.equals(type)) {
            String q = "SELECT pickup_IsPickedUp FROM pickup WHERE ordertable_OrderID = ?";
            try (Connection conn = connectToDB();
                 PreparedStatement ps = conn.prepareStatement(q)) {
                ps.setInt(1, orderID);
                try (ResultSet r2 = ps.executeQuery()) {
                    if (r2.next()) {
                        boolean picked = r2.getInt("pickup_IsPickedUp") == 1;
                        o = new PickupOrder(orderID, custID, date, cPrice, bPrice, picked, complete);
                    }
                }
            }
        } else if (delivery.equals(type)) {
            String q = "SELECT * FROM delivery WHERE ordertable_OrderID = ?";
            try (Connection conn = connectToDB();
                 PreparedStatement ps = conn.prepareStatement(q)) {
                ps.setInt(1, orderID);
                try (ResultSet r2 = ps.executeQuery()) {
                    if (r2.next()) {
                        String addr = r2.getInt("delivery_HouseNum") + "\t" +
                                      r2.getString("delivery_Street") + "\t" +
                                      r2.getString("delivery_City") + "\t" +
                                      r2.getString("delivery_State") + "\t" +
                                      r2.getInt("delivery_Zip");
                        boolean delivered = r2.getInt("delivery_IsDelivered") == 1;
                        o = new DeliveryOrder(orderID, custID, date, cPrice, bPrice, complete, delivered, addr);
                    }
                }
            }
        }

        if (o != null) {
            o.setPizzaList(getPizzas(o));
            o.setDiscountList(getDiscounts(o));
        }
        return o;
    }

    // --- SELECTS: Discounts --------------------------------------------------

    public static ArrayList<Discount> getDiscountList() throws SQLException, IOException {
        ArrayList<Discount> list = new ArrayList<>();
        String sql = "SELECT * FROM discount ORDER BY discount_DiscountName";
        try (Connection conn = connectToDB();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                list.add(new Discount(
                        rs.getInt("discount_DiscountID"),
                        rs.getString("discount_DiscountName"),
                        rs.getDouble("discount_Amount"),
                        rs.getInt("discount_IsPercent") == 1
                ));
            }
        }
        return list;
    }

    public static Discount findDiscountByName(String name) throws SQLException, IOException {
        String sql = "SELECT * FROM discount WHERE discount_DiscountName = ?";
        try (Connection conn = connectToDB();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Discount(
                            rs.getInt("discount_DiscountID"),
                            rs.getString("discount_DiscountName"),
                            rs.getDouble("discount_Amount"),
                            rs.getInt("discount_IsPercent") == 1
                    );
                }
            }
        }
        return null;
    }

    public static ArrayList<Discount> getDiscounts(Order o) throws SQLException, IOException {
        ArrayList<Discount> list = new ArrayList<>();
        String sql = "SELECT d.* FROM discount d " +
                     "JOIN order_discount od ON d.discount_DiscountID = od.discount_DiscountID " +
                     "WHERE od.ordertable_OrderID = ?";
        try (Connection conn = connectToDB();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, o.getOrderID());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Discount(
                            rs.getInt("discount_DiscountID"),
                            rs.getString("discount_DiscountName"),
                            rs.getDouble("discount_Amount"),
                            rs.getInt("discount_IsPercent") == 1
                    ));
                }
            }
        }
        return list;
    }

    // Overload used by autograder: discounts for a specific pizza
    public static ArrayList<Discount> getDiscounts(Pizza p) throws SQLException, IOException {
        ArrayList<Discount> list = new ArrayList<>();
        String sql = "SELECT d.* FROM discount d " +
                     "JOIN pizza_discount pd ON d.discount_DiscountID = pd.discount_DiscountID " +
                     "WHERE pd.pizza_PizzaID = ?";
        try (Connection conn = connectToDB();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, p.getPizzaID());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Discount(
                            rs.getInt("discount_DiscountID"),
                            rs.getString("discount_DiscountName"),
                            rs.getDouble("discount_Amount"),
                            rs.getInt("discount_IsPercent") == 1
                    ));
                }
            }
        }
        return list;
    }

    // --- SELECTS: Customers --------------------------------------------------

    public static ArrayList<Customer> getCustomerList() throws SQLException, IOException {
        ArrayList<Customer> list = new ArrayList<>();
        String sql = "SELECT * FROM customer " +
                     "ORDER BY customer_LName, customer_FName, customer_CustID";
        try (Connection conn = connectToDB();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                list.add(new Customer(
                        rs.getInt("customer_CustID"),
                        rs.getString("customer_FName"),
                        rs.getString("customer_LName"),
                        rs.getString("customer_PhoneNum")
                ));
            }
        }
        return list;
    }

    public static Customer findCustomerByPhone(String phoneNumber) throws SQLException, IOException {
        String sql = "SELECT * FROM customer WHERE customer_PhoneNum = ?";
        try (Connection conn = connectToDB();
             PreparedStatement ps = conn.prepareStatement(sql)) {

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
        if (custID <= 0) {
            return "IN STORE";
        }
        String sql = "SELECT customer_FName, customer_LName FROM customer WHERE customer_CustID = ?";
        try (Connection conn = connectToDB();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, custID);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("customer_FName") + " " + rs.getString("customer_LName");
                }
            }
        }
        return "IN STORE";
    }

    // --- SELECTS: Toppings & Inventory --------------------------------------

    public static ArrayList<Topping> getToppingList() throws SQLException, IOException {
        ArrayList<Topping> list = new ArrayList<>();
        String sql = "SELECT * FROM topping ORDER BY topping_TopID";
        try (Connection conn = connectToDB();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

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
        String sql = "SELECT * FROM topping WHERE topping_TopName = ?";
        try (Connection conn = connectToDB();
             PreparedStatement ps = conn.prepareStatement(sql)) {

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

    public static ArrayList<Topping> getToppingsOnPizza(Pizza p) throws SQLException, IOException {
        ArrayList<Topping> list = new ArrayList<>();
        String sql = "SELECT t.*, pt.pizza_topping_IsDouble " +
                     "FROM topping t " +
                     "JOIN pizza_topping pt ON t.topping_TopID = pt.topping_TopID " +
                     "WHERE pt.pizza_PizzaID = ? " +
                     "ORDER BY t.topping_TopID";
        try (Connection conn = connectToDB();
             PreparedStatement ps = conn.prepareStatement(sql)) {

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
                    t.setDoubled(rs.getInt("pizza_topping_IsDouble") == 1);
                    list.add(t);
                }
            }
        }
        return list;
    }

    public static void addToInventory(int toppingID, double quantity) throws SQLException, IOException {
        String sql = "UPDATE topping SET topping_CurINVT = topping_CurINVT + ? WHERE topping_TopID = ?";
        try (Connection conn = connectToDB();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setDouble(1, quantity);
            ps.setInt(2, toppingID);
            ps.executeUpdate();
        }
    }

    // --- SELECTS: Pizzas -----------------------------------------------------

    public static ArrayList<Pizza> getPizzas(Order o) throws SQLException, IOException {
        ArrayList<Pizza> list = new ArrayList<>();
        String sql = "SELECT * FROM pizza WHERE ordertable_OrderID = ? ORDER BY pizza_PizzaID";
        try (Connection conn = connectToDB();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, o.getOrderID());
            try (ResultSet rs = ps.executeQuery()) {
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
                    p.setDiscounts(getDiscounts(p));
                    list.add(p);
                }
            }
        }
        return list;
    }

    // --- Base prices ---------------------------------------------------------

    public static double getBaseCustPrice(String size, String crust) throws SQLException, IOException {
        String sql = "SELECT baseprice_CustPrice FROM baseprice " +
                     "WHERE baseprice_Size = ? AND baseprice_CrustType = ?";
        try (Connection conn = connectToDB();
             PreparedStatement ps = conn.prepareStatement(sql)) {

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
        String sql = "SELECT baseprice_BusPrice FROM baseprice " +
                     "WHERE baseprice_Size = ? AND baseprice_CrustType = ?";
        try (Connection conn = connectToDB();
             PreparedStatement ps = conn.prepareStatement(sql)) {

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

    // --- Reports -------------------------------------------------------------

    public static void printToppingReport() throws SQLException, IOException {
        String sql = "SELECT * FROM ToppingPopularity";
        try (Connection conn = connectToDB();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            System.out.println("Topping              | Count");
            System.out.println("-------------------- | -----");
            while (rs.next()) {
                String name = rs.getString("Topping");
                int count   = rs.getInt("ToppingCount");
                System.out.printf("%-20s | %d%n", name, count);
            }
        }
    }

    public static void printProfitByPizzaReport() throws SQLException, IOException {
        String sql = "SELECT * FROM ProfitByPizza";
        try (Connection conn = connectToDB();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            System.out.println("Pizza Size          Pizza Crust         Profit              Last Order Date     ");
            System.out.println("----------          -----------         ------              ---------------     ");
            while (rs.next()) {
                String size  = rs.getString("Size");
                String crust = rs.getString("Crust");
                double profit = rs.getDouble("Profit");
                String month = rs.getString("OrderMonth");
                System.out.printf("%-20s%-20s%-20.2f%-20s%n", size, crust, profit, month);
            }
        }
    }

    public static void printProfitByOrderTypeReport() throws SQLException, IOException {
        String sql = "SELECT * FROM ProfitByOrderType";
        try (Connection conn = connectToDB();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            System.out.println("Customer Type       Order Month         Total Order Price   Total Order Cost    Profit              ");
            System.out.println("-------------       -----------         -----------------   ----------------    ------              ");
            while (rs.next()) {
                String type = rs.getString("CustomerType");
                String month = rs.getString("OrderMonth");
                double totalPrice = rs.getDouble("TotalOrderPrice");
                double totalCost  = rs.getDouble("TotalOrderCost");
                double profit     = rs.getDouble("Profit");

                if (type == null || type.isEmpty()) {
                    type = "GRAND TOTAL";
                }

                System.out.printf("%-20s%-20s%-20.2f%-20.2f%-20.2f%n",
                        type, month, totalPrice, totalCost, profit);
            }
        }
    }

    // --- Date helpers (used by autograder) ----------------------------------

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
        return getYear(dateOfOrder) == year &&
               getMonth(dateOfOrder) == month &&
               getDay(dateOfOrder) == day;
    }

    // --- Next IDs (used only if needed) -------------------------------------

    public static int getNextOrderID() throws SQLException, IOException {
        String sql = "SELECT COALESCE(MAX(ordertable_OrderID),0) + 1 AS nextID FROM ordertable";
        try (Connection conn = connectToDB();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt("nextID") : 1;
        }
    }

    public static int getNextPizzaID() throws SQLException, IOException {
        String sql = "SELECT COALESCE(MAX(pizza_PizzaID),0) + 1 AS nextID FROM pizza";
        try (Connection conn = connectToDB();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt("nextID") : 1;
        }
    }
}
