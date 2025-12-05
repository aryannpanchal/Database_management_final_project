package cpsc4620;

import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.Date;

public class DBNinja {

    // Pizza sizes
    public static final String size_s  = "Small";
    public static final String size_m  = "Medium";
    public static final String size_l  = "Large";
    public static final String size_xl = "XLarge";

    // Crust types
    public static final String crust_thin = "Thin";
    public static final String crust_orig = "Original";
    public static final String crust_pan  = "Pan";
    public static final String crust_gf   = "Gluten-Free";

    // Order types
    public static final String dine_in  = "dinein";
    public static final String pickup   = "pickup";
    public static final String delivery = "delivery";

    public enum order_state {
        PREPARED, PICKEDUP, DELIVERED, COMPLETED
    }

    private static Connection conn = null;

    private static void connectToDB() throws SQLException, IOException {
        if (conn == null || conn.isClosed()) {
            conn = DBConnector.make_connection();
            if (conn == null) throw new SQLException("Cannot connect to DB");
        }
    }

    private static String normalizeTimestamp(Timestamp ts) {
        if (ts == null) return "";
        String raw = ts.toString();
        if (raw.endsWith(".0")) raw = raw.substring(0, raw.length() - 2);
        return raw;
    }

    // -------------------------------------------------------------------------------------
    // ADD ORDER
    // -------------------------------------------------------------------------------------

    public static void addOrder(Order o) throws SQLException, IOException {
        connectToDB();

        try {
            conn.setAutoCommit(false);

            // Insert order
            String sql = "INSERT INTO ordertable " +
                    "(ordertable_OrderType, ordertable_OrderDateTime, ordertable_CustPrice," +
                    " ordertable_BusPrice, ordertable_IsComplete, customer_CustID) VALUES (?, ?, ?, ?, ?, ?)";

            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

                ps.setString(1, o.getOrderType());

                Timestamp ts;
                try {
                    ts = Timestamp.valueOf(o.getDate());
                } catch (Exception e) {
                    ts = new Timestamp(System.currentTimeMillis());
                }
                ps.setTimestamp(2, ts);

                ps.setDouble(3, o.getCustPrice());
                ps.setDouble(4, o.getBusPrice());
                ps.setBoolean(5, o.getIsComplete());

                if (o.getCustID() > 0) ps.setInt(6, o.getCustID());
                else ps.setNull(6, Types.INTEGER);

                ps.executeUpdate();

                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) o.setOrderID(keys.getInt(1));
                }
            }

            int oid = o.getOrderID();

            // Insert subtype
            if (o.getOrderType().equals(dine_in)) {
                DineinOrder d = (DineinOrder) o;
                String q = "INSERT INTO dinein (ordertable_OrderID, dinein_TableNum) VALUES (?,?)";
                try (PreparedStatement ps = conn.prepareStatement(q)) {
                    ps.setInt(1, oid);
                    ps.setInt(2, d.getTableNum());
                    ps.executeUpdate();
                }
            }
            else if (o.getOrderType().equals(pickup)) {
                PickupOrder p = (PickupOrder) o;
                String q = "INSERT INTO pickup (ordertable_OrderID, pickup_IsPickedUp) VALUES (?,?)";
                try (PreparedStatement ps = conn.prepareStatement(q)) {
                    ps.setInt(1, oid);
                    ps.setBoolean(2, p.getIsPickedUp());
                    ps.executeUpdate();
                }
            }
            else if (o.getOrderType().equals(delivery)) {
                DeliveryOrder d = (DeliveryOrder) o;
                String[] parts = d.getAddress().split("\\t");

                int houseNum = 0, zip = 0;
                String street = "", city = "", state = "";

                if (parts.length >= 5) {
                    try { houseNum = Integer.parseInt(parts[0].trim()); } catch (Exception ignored) {}
                    street = parts[1];
                    city = parts[2];
                    state = parts[3];
                    try { zip = Integer.parseInt(parts[4].trim()); } catch (Exception ignored) {}
                }

                String q = "INSERT INTO delivery " +
                        "(ordertable_OrderID, delivery_HouseNum, delivery_Street, delivery_City," +
                        " delivery_State, delivery_Zip, delivery_IsDelivered) VALUES (?,?,?,?,?,?,?)";

                try (PreparedStatement ps = conn.prepareStatement(q)) {
                    ps.setInt(1, oid);
                    ps.setInt(2, houseNum);
                    ps.setString(3, street);
                    ps.setString(4, city);
                    ps.setString(5, state);
                    ps.setInt(6, zip);
                    ps.setBoolean(7, d.getIsDelivered());
                    ps.executeUpdate();
                }
            }

            // Insert pizzas
            for (Pizza p : o.getPizzaList()) {
                Timestamp pt;
                try { pt = Timestamp.valueOf(p.getPizzaDate()); }
                catch (Exception e) { pt = new Timestamp(System.currentTimeMillis()); }

                addPizza(new Date(pt.getTime()), oid, p);
            }

            // Order-level discounts
            for (Discount d : o.getDiscountList()) {
                String q = "INSERT INTO order_discount (ordertable_OrderID, discount_DiscountID) VALUES (?,?)";
                try (PreparedStatement ps = conn.prepareStatement(q)) {
                    ps.setInt(1, oid);
                    ps.setInt(2, d.getDiscountID());
                    ps.executeUpdate();
                }
            }

            conn.commit();
        }
        catch (SQLException e) {
            if (conn != null) conn.rollback();
            throw e;
        }
        finally {
            if (conn != null) conn.setAutoCommit(true);
        }
    }

    // -------------------------------------------------------------------------------------
    // ADD PIZZA
    // -------------------------------------------------------------------------------------

    public static int addPizza(Date d, int oid, Pizza p) throws SQLException, IOException {
        connectToDB();

        int pid = -1;

        String sql = "INSERT INTO pizza (ordertable_OrderID, pizza_Size, pizza_CrustType," +
                " pizza_PizzaState, pizza_PizzaDate, pizza_CustPrice, pizza_BusPrice)" +
                " VALUES (?,?,?,?,?,?,?)";

        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, oid);
            ps.setString(2, p.getSize());
            ps.setString(3, p.getCrustType());
            ps.setString(4, p.getPizzaState());

            ps.setTimestamp(5, new Timestamp(d.getTime()));
            ps.setDouble(6, p.getCustPrice());
            ps.setDouble(7, p.getBusPrice());

            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    pid = keys.getInt(1);
                    p.setPizzaID(pid);
                }
            }
        }

        // toppings
        for (Topping t : p.getToppings()) {
            int dbl = t.getDoubled() ? 1 : 0;

            String q = "INSERT INTO pizza_topping (pizza_PizzaID, topping_TopID, pizza_topping_IsDouble) VALUES (?,?,?)";
            try (PreparedStatement ps = conn.prepareStatement(q)) {
                ps.setInt(1, pid);
                ps.setInt(2, t.getTopID());
                ps.setInt(3, dbl);
                ps.executeUpdate();
            }

            double units;
            String s = p.getSize();
            if (s.equals(size_s)) units = t.getSmallAMT();
            else if (s.equals(size_m)) units = t.getMedAMT();
            else if (s.equals(size_l)) units = t.getLgAMT();
            else units = t.getXLAMT();

            if (dbl == 1) units *= 2;

            String q2 = "UPDATE topping SET topping_CurINVT = topping_CurINVT - ? WHERE topping_TopID = ?";
            try (PreparedStatement ps = conn.prepareStatement(q2)) {
                ps.setDouble(1, units);
                ps.setInt(2, t.getTopID());
                ps.executeUpdate();
            }
        }

        // discounts
        for (Discount disc : p.getDiscounts()) {
            String q = "INSERT INTO pizza_discount (pizza_PizzaID, discount_DiscountID) VALUES (?,?)";
            try (PreparedStatement ps = conn.prepareStatement(q)) {
                ps.setInt(1, pid);
                ps.setInt(2, disc.getDiscountID());
                ps.executeUpdate();
            }
        }

        return pid;
    }

    // -------------------------------------------------------------------------------------
    // CUSTOMERS
    // -------------------------------------------------------------------------------------

    public static int addCustomer(Customer c) throws SQLException, IOException {
        connectToDB();

        String sql = "INSERT INTO customer (customer_FName, customer_LName, customer_PhoneNum) VALUES (?,?,?)";
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

    public static ArrayList<Customer> getCustomerList() throws SQLException, IOException {
        connectToDB();

        ArrayList<Customer> list = new ArrayList<>();
        String sql = "SELECT * FROM customer ORDER BY customer_CustID DESC";

        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            while (rs.next()) {
                list.add(
                    new Customer(
                        rs.getInt("customer_CustID"),
                        rs.getString("customer_FName"),
                        rs.getString("customer_LName"),
                        rs.getString("customer_PhoneNum")
                    )
                );
            }
        }

        return list;
    }

    public static Customer findCustomerByPhone(String phone) throws SQLException, IOException {
        connectToDB();

        String sql = "SELECT * FROM customer WHERE customer_PhoneNum = ? LIMIT 1";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, phone);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return new Customer(
                            rs.getInt("customer_CustID"),
                            rs.getString("customer_FName"),
                            rs.getString("customer_LName"),
                            rs.getString("customer_PhoneNum"));
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------------------
    // DISCOUNTS
    // -------------------------------------------------------------------------------------

    public static ArrayList<Discount> getDiscountList() throws SQLException, IOException {
        connectToDB();

        ArrayList<Discount> list = new ArrayList<>();
        String sql = "SELECT * FROM discount";

        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            while (rs.next()) {
                list.add(new Discount(
                        rs.getInt("discount_DiscountID"),
                        rs.getString("discount_DiscountName"),
                        rs.getDouble("discount_Amount"),
                        rs.getBoolean("discount_IsPercent")));
            }
        }

        return list;
    }

    public static Discount findDiscountByName(String name) throws SQLException, IOException {
        connectToDB();

        String sql = "SELECT * FROM discount WHERE discount_DiscountName = ? LIMIT 1";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return new Discount(
                            rs.getInt("discount_DiscountID"),
                            rs.getString("discount_DiscountName"),
                            rs.getDouble("discount_Amount"),
                            rs.getBoolean("discount_IsPercent"));
            }
        }

        return null;
    }

    public static ArrayList<Discount> getDiscounts(Order o) throws SQLException, IOException {
        connectToDB();

        ArrayList<Discount> list = new ArrayList<>();
        String sql =
                "SELECT d.* FROM discount d " +
                "JOIN order_discount od ON d.discount_DiscountID = od.discount_DiscountID " +
                "WHERE od.ordertable_OrderID = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, o.getOrderID());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Discount(
                            rs.getInt("discount_DiscountID"),
                            rs.getString("discount_DiscountName"),
                            rs.getDouble("discount_Amount"),
                            rs.getBoolean("discount_IsPercent")));
                }
            }
        }

        return list;
    }

    public static ArrayList<Discount> getDiscounts(Pizza p) throws SQLException, IOException {
        connectToDB();

        ArrayList<Discount> list = new ArrayList<>();
        String sql =
                "SELECT d.* FROM discount d " +
                "JOIN pizza_discount pd ON d.discount_DiscountID = pd.discount_DiscountID " +
                "WHERE pd.pizza_PizzaID = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, p.getPizzaID());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Discount(
                            rs.getInt("discount_DiscountID"),
                            rs.getString("discount_DiscountName"),
                            rs.getDouble("discount_Amount"),
                            rs.getBoolean("discount_IsPercent")));
                }
            }
        }

        return list;
    }

    // -------------------------------------------------------------------------------------
    // TOPPINGS / INVENTORY
    // -------------------------------------------------------------------------------------

    public static ArrayList<Topping> getToppingList() throws SQLException, IOException {
        connectToDB();

        ArrayList<Topping> list = new ArrayList<>();
        String sql = "SELECT * FROM topping";

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

    public static ArrayList<Topping> getToppingsOnPizza(Pizza p) throws SQLException, IOException {
        connectToDB();

        ArrayList<Topping> list = new ArrayList<>();
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
                    t.setDoubled(rs.getInt("pizza_topping_IsDouble") == 1);
                    list.add(t);
                }
            }
        }

        return list;
    }

    public static void addToInventory(int toppingID, double amt) throws SQLException, IOException {
        connectToDB();

        String sql = "UPDATE topping SET topping_CurINVT = topping_CurINVT + ? WHERE topping_TopID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, amt);
            ps.setInt(2, toppingID);
            ps.executeUpdate();
        }
    }

    // -------------------------------------------------------------------------------------
    // PIZZA RETRIEVAL
    // -------------------------------------------------------------------------------------

    public static ArrayList<Pizza> getPizzas(Order o) throws SQLException, IOException {
        connectToDB();

        ArrayList<Pizza> list = new ArrayList<>();
        String sql = "SELECT * FROM pizza WHERE ordertable_OrderID = ? ORDER BY pizza_PizzaID ASC";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, o.getOrderID());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int pid = rs.getInt("pizza_PizzaID");
                    String size = rs.getString("pizza_Size");
                    String crust = rs.getString("pizza_CrustType");
                    String state = rs.getString("pizza_PizzaState");
                    String date = normalizeTimestamp(rs.getTimestamp("pizza_PizzaDate"));
                    double cp = rs.getDouble("pizza_CustPrice");
                    double bp = rs.getDouble("pizza_BusPrice");

                    Pizza p = new Pizza(pid, size, crust, o.getOrderID(), state, date, cp, bp);
                    p.setToppings(getToppingsOnPizza(p));
                    p.setDiscounts(getDiscounts(p));

                    list.add(p);
                }
            }
        }

        return list;
    }

    // -------------------------------------------------------------------------------------
    // BASE PRICES
    // -------------------------------------------------------------------------------------

    public static double getBaseCustPrice(String size, String crust) throws SQLException, IOException {
        connectToDB();

        String sql = "SELECT baseprice_CustPrice FROM baseprice WHERE baseprice_Size=? AND baseprice_CrustType=?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, size);
            ps.setString(2, crust);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getDouble(1);
            }
        }

        return 0;
    }

    public static double getBaseBusPrice(String size, String crust) throws SQLException, IOException {
        connectToDB();

        String sql = "SELECT baseprice_BusPrice FROM baseprice WHERE baseprice_Size=? AND baseprice_CrustType=?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, size);
            ps.setString(2, crust);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getDouble(1);
            }
        }

        return 0;
    }

    // -------------------------------------------------------------------------------------
    // COMPLETE ORDER
    // -------------------------------------------------------------------------------------

    public static void completeOrder(int oid, order_state st) throws SQLException, IOException {
        connectToDB();

        String up = "UPDATE ordertable SET ordertable_IsComplete = 1 WHERE ordertable_OrderID=?";
        try (PreparedStatement ps = conn.prepareStatement(up)) {
            ps.setInt(1, oid);
            ps.executeUpdate();
        }

        String q = "SELECT ordertable_OrderType FROM ordertable WHERE ordertable_OrderID=?";
        String type = "";

        try (PreparedStatement ps = conn.prepareStatement(q)) {
            ps.setInt(1, oid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) type = rs.getString(1);
            }
        }

        if (type.equals(pickup)) {
            String up2 = "UPDATE pickup SET pickup_IsPickedUp = 1 WHERE ordertable_OrderID=?";
            try (PreparedStatement ps = conn.prepareStatement(up2)) {
                ps.setInt(1, oid);
                ps.executeUpdate();
            }
        }
        else if (type.equals(delivery)) {
            String up2 = "UPDATE delivery SET delivery_IsDelivered = 1 WHERE ordertable_OrderID=?";
            try (PreparedStatement ps = conn.prepareStatement(up2)) {
                ps.setInt(1, oid);
                ps.executeUpdate();
            }
        }
    }

    // -------------------------------------------------------------------------------------
    // ORDER RETRIEVAL
    // -------------------------------------------------------------------------------------

    public static ArrayList<Order> getOrders(int status) throws SQLException, IOException {
        connectToDB();

        ArrayList<Order> list = new ArrayList<>();
        String sql = "SELECT * FROM ordertable";

        if (status == 1) sql += " WHERE ordertable_IsComplete=0";
        if (status == 2) sql += " WHERE ordertable_IsComplete=1";

        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            while (rs.next()) list.add(buildOrderFromResultSet(rs));
        }

        return list;
    }

    public static Order getLastOrder() throws SQLException, IOException {
        connectToDB();

        String sql = "SELECT * FROM ordertable ORDER BY ordertable_OrderID DESC LIMIT 1";

        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            if (rs.next()) return buildOrderFromResultSet(rs);
        }

        return null;
    }

    public static ArrayList<Order> getOrdersByDate(String date) throws SQLException, IOException {
        connectToDB();

        ArrayList<Order> list = new ArrayList<>();
        String sql = "SELECT * FROM ordertable WHERE DATE(ordertable_OrderDateTime)=?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, date);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(buildOrderFromResultSet(rs));
            }
        }

        return list;
    }

    // -------------------------------------------------------------------------------------
    // REPORTS
    // -------------------------------------------------------------------------------------

    public static void printToppingReport() throws SQLException, IOException {
        connectToDB();

        String sql = "SELECT * FROM ToppingPopularity";

        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            System.out.printf("%-20s%-15s%n","Topping","Topping Count");
            System.out.printf("%-20s%-15s%n","-------","-------------");

            while (rs.next()) {
                System.out.printf("%-20s%-15d%n",
                        rs.getString("Topping"),
                        rs.getInt("ToppingCount"));
            }
        }
    }

    public static void printProfitByPizzaReport() throws SQLException, IOException {
        connectToDB();

        String sql = "SELECT * FROM ProfitByPizza";

        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            System.out.printf("%-10s%-12s%-12s%-15s%n","Size","Crust","Profit","OrderMonth");
            System.out.printf("%-10s%-12s%-12s%-15s%n","----","-----","------","----------");

            while (rs.next()) {
                System.out.printf("%-10s%-12s%-12.2f%-15s%n",
                        rs.getString("Size"),
                        rs.getString("Crust"),
                        rs.getDouble("Profit"),
                        rs.getString("OrderMonth"));
            }
        }
    }

    public static void printProfitByOrderTypeReport() throws SQLException, IOException {
        connectToDB();

        String sql = "SELECT * FROM ProfitByOrderType";

        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            System.out.printf(
                "%-12s%-15s%-18s%-18s%-15s%n",
                "Type","Month","TotalOrderPrice","TotalOrderCost","Profit"
            );
            System.out.printf(
                "%-12s%-15s%-18s%-18s%-15s%n",
                "----","-----","---------------","--------------","------"
            );

            while (rs.next()) {
                System.out.printf(
                        "%-12s%-15s%-18.2f%-18.2f%-15.2f%n",
                        rs.getString("CustomerType"),
                        rs.getString("OrderMonth"),
                        rs.getDouble("TotalOrderPrice"),
                        rs.getDouble("TotalOrderCost"),
                        rs.getDouble("Profit")
                );
            }
        }
    }

    // -------------------------------------------------------------------------------------
    // BUILD ORDER
    // -------------------------------------------------------------------------------------

    private static Order buildOrderFromResultSet(ResultSet rs) throws SQLException, IOException {
        int oid = rs.getInt("ordertable_OrderID");
        String type = rs.getString("ordertable_OrderType");
        String date = normalizeTimestamp(rs.getTimestamp("ordertable_OrderDateTime"));
        double cp = rs.getDouble("ordertable_CustPrice");
        double bp = rs.getDouble("ordertable_BusPrice");
        boolean complete = rs.getBoolean("ordertable_IsComplete");

        int custID = rs.getInt("customer_CustID");
        if (rs.wasNull()) custID = -1;

        Order o;

        if (type.equals(dine_in)) {
            int tbl = 0;
            String q = "SELECT dinein_TableNum FROM dinein WHERE ordertable_OrderID=?";
            try (PreparedStatement ps = conn.prepareStatement(q)) {
                ps.setInt(1, oid);
                try (ResultSet r2 = ps.executeQuery()) {
                    if (r2.next()) tbl = r2.getInt(1);
                }
            }
            o = new DineinOrder(oid, custID, date, cp, bp, complete, tbl);
        }
        else if (type.equals(pickup)) {
            boolean pu = false;
            String q = "SELECT pickup_IsPickedUp FROM pickup WHERE ordertable_OrderID=?";
            try (PreparedStatement ps = conn.prepareStatement(q)) {
                ps.setInt(1, oid);
                try (ResultSet r2 = ps.executeQuery()) {
                    if (r2.next()) pu = r2.getBoolean(1);
                }
            }
            o = new PickupOrder(oid, custID, date, cp, bp, pu, complete);
        }
        else if (type.equals(delivery)) {
            int house = 0, zip = 0;
            String street = "", city = "", state = "";
            boolean deliv = false;

            String q = "SELECT delivery_HouseNum, delivery_Street, delivery_City," +
                       " delivery_State, delivery_Zip, delivery_IsDelivered " +
                       "FROM delivery WHERE ordertable_OrderID=?";

            try (PreparedStatement ps = conn.prepareStatement(q)) {
                ps.setInt(1, oid);
                try (ResultSet r2 = ps.executeQuery()) {
                    if (r2.next()) {
                        house = r2.getInt(1);
                        street = r2.getString(2);
                        city = r2.getString(3);
                        state = r2.getString(4);
                        zip = r2.getInt(5);
                        deliv = r2.getBoolean(6);
                    }
                }
            }

            String addr = house + "\t" + street + "\t" + city + "\t" + state + "\t" + zip;

            // USE OPTION A (full constructor)
            o = new DeliveryOrder(oid, custID, date, cp, bp, complete, deliv, addr);
        }
        else {
            o = new Order(oid, custID, type, date, cp, bp, complete);
        }

        o.setPizzaList(getPizzas(o));
        o.setDiscountList(getDiscounts(o));

        return o;
    }
}
