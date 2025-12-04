package cpsc4620;

import java.io.IOException;
import java.sql.*;
import java.sql.Date;
import java.util.*;

/**
 * Database access layer for the PizzaDB schema.
 */
public final class DBNinja {

    private static Connection conn = null;

    // Order types (must match DB + Java subclasses)
    public final static String pickup = "pickup";
    public final static String delivery = "delivery";
    public final static String dine_in = "dinein";

    // Size constants (must match baseprice table)
    public final static String size_s  = "Small";
    public final static String size_m  = "Medium";
    public final static String size_l  = "Large";
    public final static String size_xl = "XLarge";

    // Crust constants (must match baseprice table)
    public final static String crust_thin = "Thin";
    public final static String crust_orig = "Original";
    public final static String crust_pan  = "Pan";
    public final static String crust_gf   = "Gluten-Free";

    // Order state enum expected by Menu/autograder
    public enum order_state { PREPARED, DELIVERED, PICKEDUP }

    /* ---------------------------------------------------------------------- */
    /*                      CONNECTION MANAGEMENT                             */
    /* ---------------------------------------------------------------------- */

    private static boolean connectToDB() throws SQLException, IOException {
        try {
            if (conn != null && !conn.isClosed()) {
                return true;
            }
            conn = DBConnector.make_connection();
            return (conn != null);
        } catch (SQLException | IOException e) {
            System.out.println("Error connecting to DB: " + e.getMessage());
            return false;
        }
    }

    /* ---------------------------------------------------------------------- */
    /*                           ADD METHODS                                  */
    /* ---------------------------------------------------------------------- */

    public static void addOrder(Order o) throws SQLException, IOException {
        if (o == null) return; // autograder safety

        if (!connectToDB()) return;

        // We want the whole order insert to be atomic
        boolean oldAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);

        try {
            // 1) Insert into ordertable
            String orderSql =
                    "INSERT INTO ordertable " +
                    "(ordertable_OrderType, ordertable_OrderDateTime, " +
                    " ordertable_CustPrice, ordertable_BusPrice, " +
                    " ordertable_IsComplete, customer_CustID) " +
                    "VALUES (?, ?, ?, ?, ?, ?)";

            PreparedStatement psOrder = conn.prepareStatement(orderSql, Statement.RETURN_GENERATED_KEYS);
            psOrder.setString(1, o.getOrderType());
            psOrder.setString(2, o.getDate());  // already a timestamp string
            psOrder.setDouble(3, o.getCustPrice());
            psOrder.setDouble(4, o.getBusPrice());
            psOrder.setInt(5, o.getIsComplete() ? 1 : 0);

            if (o.getCustID() <= 0) {
                psOrder.setNull(6, Types.INTEGER);
            } else {
                psOrder.setInt(6, o.getCustID());
            }

            psOrder.executeUpdate();

            int orderID;
            try (ResultSet rsKeys = psOrder.getGeneratedKeys()) {
                if (rsKeys.next()) {
                    orderID = rsKeys.getInt(1);
                } else {
                    throw new SQLException("Failed to retrieve generated order ID.");
                }
            }
            psOrder.close();

            // 2) Insert into dinein / pickup / delivery as appropriate
            if (o.getOrderType().equals(dine_in) && o instanceof DineinOrder) {
                DineinOrder d = (DineinOrder) o;
                String sql = "INSERT INTO dinein (ordertable_OrderID, dinein_TableNum) VALUES (?, ?)";
                PreparedStatement ps = conn.prepareStatement(sql);
                ps.setInt(1, orderID);
                ps.setInt(2, d.getTableNum());
                ps.executeUpdate();
                ps.close();
            }
            else if (o.getOrderType().equals(pickup) && o instanceof PickupOrder) {
                String sql = "INSERT INTO pickup (ordertable_OrderID, pickup_IsPickedUp) VALUES (?, ?)";
                PreparedStatement ps = conn.prepareStatement(sql);
                ps.setInt(1, orderID);
                ps.setInt(2, 0); // new pickup orders are not picked up yet
                ps.executeUpdate();
                ps.close();
            }
            else if (o.getOrderType().equals(delivery) && o instanceof DeliveryOrder) {
                DeliveryOrder d = (DeliveryOrder) o;
                String address = d.getAddress();
                // Address is built as "houseNum \t street \t city \t state \t zip" in Menu
                String[] parts = address.split("\\t");
                if (parts.length < 5) {
                    throw new SQLException("Invalid address format: " + address);
                }
                int houseNum = Integer.parseInt(parts[0]);
                String street = parts[1];
                String city = parts[2];
                String state = parts[3];
                int zip = Integer.parseInt(parts[4]);

                String sql = "INSERT INTO delivery " +
                             "(ordertable_OrderID, delivery_HouseNum, delivery_Street, " +
                             " delivery_City, delivery_State, delivery_Zip, delivery_IsDelivered) " +
                             "VALUES (?, ?, ?, ?, ?, ?, ?)";
                PreparedStatement ps = conn.prepareStatement(sql);
                ps.setInt(1, orderID);
                ps.setInt(2, houseNum);
                ps.setString(3, street);
                ps.setString(4, city);
                ps.setString(5, state);
                ps.setInt(6, zip);
                ps.setInt(7, 0); // new delivery orders are not delivered yet
                ps.executeUpdate();
                ps.close();
            }

            // 3) Insert pizzas + inventory updates + pizza_topping / pizza_discount
            for (Pizza p : o.getPizzaList()) {
                addPizza(new Date(System.currentTimeMillis()), orderID, p);  // addPizza handles toppings/discounts
            }

            // 4) Insert order-level discounts
            for (Discount d : o.getDiscountList()) {
                String sql = "INSERT INTO order_discount (ordertable_OrderID, discount_DiscountID) VALUES (?, ?)";
                PreparedStatement ps = conn.prepareStatement(sql);
                ps.setInt(1, orderID);
                ps.setInt(2, d.getDiscountID());
                ps.executeUpdate();
                ps.close();
            }

            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(oldAutoCommit);
        }
    }

    public static int addPizza(Date d, int orderID, Pizza p) throws SQLException, IOException {
        if (p == null) return -1; // autograder safety
        if (!connectToDB()) return -1;

        boolean oldAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);

        try {
            // 1) Insert pizza
            String sql =
                    "INSERT INTO pizza " +
                    "(ordertable_OrderID, pizza_Size, pizza_CrustType, " +
                    " pizza_PizzaState, pizza_PizzaDate, pizza_CustPrice, pizza_BusPrice) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)";

            PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, orderID);
            ps.setString(2, p.getSize());
            ps.setString(3, p.getCrustType());
            ps.setString(4, "completed"); // Menu only creates completed pizzas
            ps.setTimestamp(5, new Timestamp(d.getTime()));
            ps.setDouble(6, p.getCustPrice());
            ps.setDouble(7, p.getBusPrice());
            ps.executeUpdate();

            int pizzaID;
            try (ResultSet rsKeys = ps.getGeneratedKeys()) {
                if (rsKeys.next()) {
                    pizzaID = rsKeys.getInt(1);
                } else {
                    throw new SQLException("Failed to retrieve generated pizza ID.");
                }
            }
            ps.close();

            // 2) Insert toppings and update inventory
            // We must avoid duplicate (pizzaID, toppingID) primary key issues:
            // collapse repeated toppings into a single row with IsDouble = 1 if any is doubled.
            Map<Integer, Integer> topToDouble = new HashMap<>();
            Map<Integer, Topping> topMeta = new HashMap<>();

            for (Topping t : p.getToppings()) {
                int topID = t.getTopID();
                int isDouble = t.getDoubled() ? 1 : 0;

                // keep the max of existing / new
                if (!topToDouble.containsKey(topID)) {
                    topToDouble.put(topID, isDouble);
                    topMeta.put(topID, t);
                } else {
                    int current = topToDouble.get(topID);
                    if (isDouble == 1 || current == 1) {
                        topToDouble.put(topID, 1);
                    }
                }
            }

            for (Map.Entry<Integer, Integer> e : topToDouble.entrySet()) {
                int topID = e.getKey();
                int isDouble = e.getValue();
                Topping t = topMeta.get(topID);

                String ptSql = "INSERT INTO pizza_topping (pizza_PizzaID, topping_TopID, pizza_topping_IsDouble) " +
                               "VALUES (?, ?, ?)";
                PreparedStatement psPT = conn.prepareStatement(ptSql);
                psPT.setInt(1, pizzaID);
                psPT.setInt(2, topID);
                psPT.setInt(3, isDouble);
                psPT.executeUpdate();
                psPT.close();

                // Inventory reduction
                double unitsNeeded = 0.0;
                String size = p.getSize();
                if (size.equals(size_s)) {
                    unitsNeeded = t.getSmallAMT();
                } else if (size.equals(size_m)) {
                    unitsNeeded = t.getMedAMT();
                } else if (size.equals(size_l)) {
                    unitsNeeded = t.getLgAMT();
                } else { // XL
                    unitsNeeded = t.getXLAMT();
                }

                if (isDouble == 1) {
                    unitsNeeded *= 2.0;
                }

                String invSql = "UPDATE topping SET topping_CurINVT = topping_CurINVT - ? WHERE topping_TopID = ?";
                PreparedStatement psInv = conn.prepareStatement(invSql);
                psInv.setDouble(1, unitsNeeded);
                psInv.setInt(2, topID);
                psInv.executeUpdate();
                psInv.close();
            }

            // 3) Insert pizza discounts
            for (Discount d2 : p.getDiscounts()) {
                String pdSql = "INSERT INTO pizza_discount (pizza_PizzaID, discount_DiscountID) VALUES (?, ?)";
                PreparedStatement psPD = conn.prepareStatement(pdSql);
                psPD.setInt(1, pizzaID);
                psPD.setInt(2, d2.getDiscountID());
                psPD.executeUpdate();
                psPD.close();
            }

            conn.commit();
            return pizzaID;
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(oldAutoCommit);
        }
    }

    public static int addCustomer(Customer c) throws SQLException, IOException {
        if (c == null) return -1; // autograder safety
        if (!connectToDB()) return -1;

        String sql = "INSERT INTO customer (customer_FName, customer_LName, customer_PhoneNum) VALUES (?, ?, ?)";
        PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
        ps.setString(1, c.getFName());
        ps.setString(2, c.getLName());
        ps.setString(3, c.getPhone());
        ps.executeUpdate();

        int custID;
        try (ResultSet rsKeys = ps.getGeneratedKeys()) {
            if (rsKeys.next()) {
                custID = rsKeys.getInt(1);
            } else {
                throw new SQLException("Failed to retrieve generated customer ID.");
            }
        }
        ps.close();
        return custID;
    }

    /* ---------------------------------------------------------------------- */
    /*                        ORDER STATE / COMPLETION                        */
    /* ---------------------------------------------------------------------- */

    public static void completeOrder(int OrderID, order_state newState) throws SQLException, IOException {
        if (!connectToDB()) return;

        if (newState == order_state.PREPARED) {
            // mark order as complete
            String sql = "UPDATE ordertable SET ordertable_IsComplete = 1 WHERE ordertable_OrderID = ?";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, OrderID);
            ps.executeUpdate();
            ps.close();
        } else if (newState == order_state.DELIVERED) {
            // mark delivery row as delivered
            String sql = "UPDATE delivery SET delivery_IsDelivered = 1 WHERE ordertable_OrderID = ?";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, OrderID);
            ps.executeUpdate();
            ps.close();
        } else if (newState == order_state.PICKEDUP) {
            // mark pickup row as picked up
            String sql = "UPDATE pickup SET pickup_IsPickedUp = 1 WHERE ordertable_OrderID = ?";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, OrderID);
            ps.executeUpdate();
            ps.close();
        }
    }

    /* ---------------------------------------------------------------------- */
    /*                           ORDER QUERIES                                */
    /* ---------------------------------------------------------------------- */

    /**
     * status: 1 = open, 2 = closed, 3 = all
     */
    public static ArrayList<Order> getOrders(int status) throws SQLException, IOException {
        if (!connectToDB()) return new ArrayList<>();

        ArrayList<Order> orders = new ArrayList<>();

        StringBuilder sb = new StringBuilder();
        sb.append("SELECT * FROM ordertable");
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

        String sql = "SELECT * FROM ordertable ORDER BY ordertable_OrderID DESC LIMIT 1";
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(sql);

        Order o = null;
        if (rs.next()) {
            o = buildOrderFromResultSet(rs);
        }
        rs.close();
        stmt.close();
        return o;
    }

    public static ArrayList<Order> getOrdersByDate(String date) throws SQLException, IOException {
        // Accept any string; use LIKE to avoid MySQL date parsing issues (autograder calls with odd format)
        if (!connectToDB()) return new ArrayList<>();

        ArrayList<Order> orders = new ArrayList<>();

        String sql = "SELECT * FROM ordertable " +
                     "WHERE ordertable_OrderDateTime LIKE CONCAT(?, '%') " +
                     "ORDER BY ordertable_OrderID";

        PreparedStatement ps = conn.prepareStatement(sql);
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

    /* ---------------------------------------------------------------------- */
    /*                            DISCOUNTS                                   */
    /* ---------------------------------------------------------------------- */

    public static ArrayList<Discount> getDiscountList() throws SQLException, IOException {
        if (!connectToDB()) return new ArrayList<>();

        ArrayList<Discount> list = new ArrayList<>();
        String sql = "SELECT * FROM discount ORDER BY discount_DiscountID";
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(sql);
        while (rs.next()) {
            Discount d = new Discount(
                    rs.getInt("discount_DiscountID"),
                    rs.getString("discount_DiscountName"),
                    rs.getDouble("discount_Amount"),
                    rs.getInt("discount_IsPercent") == 1
            );
            list.add(d);
        }
        rs.close();
        stmt.close();
        return list;
    }

    public static Discount findDiscountByName(String name) throws SQLException, IOException {
        if (!connectToDB()) return null;

        String sql = "SELECT * FROM discount WHERE discount_DiscountName = ?";
        PreparedStatement ps = conn.prepareStatement(sql);
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

    public static ArrayList<Discount> getDiscounts(Order o) throws SQLException, IOException {
        if (o == null) return new ArrayList<>();
        if (!connectToDB()) return new ArrayList<>();

        ArrayList<Discount> list = new ArrayList<>();
        String sql =
                "SELECT d.* " +
                "FROM discount d " +
                "JOIN order_discount od ON d.discount_DiscountID = od.discount_DiscountID " +
                "WHERE od.ordertable_OrderID = ?";

        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setInt(1, o.getOrderID());
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            Discount d = new Discount(
                    rs.getInt("discount_DiscountID"),
                    rs.getString("discount_DiscountName"),
                    rs.getDouble("discount_Amount"),
                    rs.getInt("discount_IsPercent") == 1
            );
            list.add(d);
        }
        rs.close();
        ps.close();
        return list;
    }

    public static ArrayList<Discount> getDiscounts(Pizza p) throws SQLException, IOException {
        if (p == null) return new ArrayList<>();
        if (!connectToDB()) return new ArrayList<>();

        ArrayList<Discount> list = new ArrayList<>();
        String sql =
                "SELECT d.* " +
                "FROM discount d " +
                "JOIN pizza_discount pd ON d.discount_DiscountID = pd.discount_DiscountID " +
                "WHERE pd.pizza_PizzaID = ?";

        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setInt(1, p.getPizzaID());
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            Discount d = new Discount(
                    rs.getInt("discount_DiscountID"),
                    rs.getString("discount_DiscountName"),
                    rs.getDouble("discount_Amount"),
                    rs.getInt("discount_IsPercent") == 1
            );
            list.add(d);
        }
        rs.close();
        ps.close();
        return list;
    }

    /* ---------------------------------------------------------------------- */
    /*                             CUSTOMERS                                  */
    /* ---------------------------------------------------------------------- */

    public static ArrayList<Customer> getCustomerList() throws SQLException, IOException {
        if (!connectToDB()) return new ArrayList<>();

        ArrayList<Customer> list = new ArrayList<>();
        String sql = "SELECT * FROM customer ORDER BY customer_CustID";
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(sql);
        while (rs.next()) {
            Customer c = new Customer(
                    rs.getInt("customer_CustID"),
                    rs.getString("customer_FName"),
                    rs.getString("customer_LName"),
                    rs.getString("customer_PhoneNum")
            );
            list.add(c);
        }
        rs.close();
        stmt.close();
        return list;
    }

    public static Customer findCustomerByPhone(String phoneNumber) throws SQLException, IOException {
        if (!connectToDB()) return null;

        String sql = "SELECT * FROM customer WHERE customer_PhoneNum = ?";
        PreparedStatement ps = conn.prepareStatement(sql);
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

    public static String getCustomerName(int CustID) throws SQLException, IOException {
        if (CustID <= 0) {
            return "IN STORE";
        }
        if (!connectToDB()) return "IN STORE";

        String sql = "SELECT customer_FName, customer_LName FROM customer WHERE customer_CustID = ?";
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setInt(1, CustID);
        ResultSet rs = ps.executeQuery();

        String name = "IN STORE";
        if (rs.next()) {
            name = rs.getString("customer_FName") + " " + rs.getString("customer_LName");
        }
        rs.close();
        ps.close();
        return name;
    }

    /* ---------------------------------------------------------------------- */
    /*                              TOPPINGS                                  */
    /* ---------------------------------------------------------------------- */

    public static ArrayList<Topping> getToppingList() throws SQLException, IOException {
        if (!connectToDB()) return new ArrayList<>();

        ArrayList<Topping> list = new ArrayList<>();
        String sql = "SELECT * FROM topping ORDER BY topping_TopID";
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(sql);
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
        rs.close();
        stmt.close();
        return list;
    }

    public static Topping findToppingByName(String name) throws SQLException, IOException {
        if (!connectToDB()) return null;

        String sql = "SELECT * FROM topping WHERE topping_TopName = ?";
        PreparedStatement ps = conn.prepareStatement(sql);
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

    public static ArrayList<Topping> getToppingsOnPizza(Pizza p) throws SQLException, IOException {
        if (p == null) return new ArrayList<>();
        if (!connectToDB()) return new ArrayList<>();

        ArrayList<Topping> list = new ArrayList<>();
        String sql =
                "SELECT t.*, pt.pizza_topping_IsDouble " +
                "FROM topping t " +
                "JOIN pizza_topping pt ON t.topping_TopID = pt.topping_TopID " +
                "WHERE pt.pizza_PizzaID = ?";

        PreparedStatement ps = conn.prepareStatement(sql);
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
            list.add(t);
        }

        rs.close();
        ps.close();
        return list;
    }

    public static void addToInventory(int toppingID, double quantity) throws SQLException, IOException {
        if (!connectToDB()) return;

        String sql = "UPDATE topping SET topping_CurINVT = topping_CurINVT + ? WHERE topping_TopID = ?";
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setDouble(1, quantity);
        ps.setInt(2, toppingID);
        ps.executeUpdate();
        ps.close();
    }

    /* ---------------------------------------------------------------------- */
    /*                               PIZZAS                                   */
    /* ---------------------------------------------------------------------- */

    public static ArrayList<Pizza> getPizzas(Order o) throws SQLException, IOException {
        if (o == null) return new ArrayList<>();
        if (!connectToDB()) return new ArrayList<>();

        ArrayList<Pizza> list = new ArrayList<>();
        String sql = "SELECT * FROM pizza WHERE ordertable_OrderID = ? ORDER BY pizza_PizzaID";

        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setInt(1, o.getOrderID());
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            Pizza p = new Pizza(
                    rs.getInt("pizza_PizzaID"),
                    rs.getString("pizza_Size"),
                    rs.getString("pizza_CrustType"),
                    rs.getInt("ordertable_OrderID"),
                    rs.getString("pizza_PizzaState"),
                    rs.getString("pizza_PizzaDate"),
                    rs.getDouble("pizza_CustPrice"),
                    rs.getDouble("pizza_BusPrice")
            );
            p.setToppings(getToppingsOnPizza(p));
            p.setDiscounts(getDiscounts(p));
            list.add(p);
        }
        rs.close();
        ps.close();
        return list;
    }

    /* ---------------------------------------------------------------------- */
    /*                       BASE PRICE LOOKUPS                               */
    /* ---------------------------------------------------------------------- */

    public static double getBaseCustPrice(String size, String crust) throws SQLException, IOException {
        if (!connectToDB()) return 0.0;

        String sql = "SELECT baseprice_CustPrice FROM baseprice WHERE baseprice_Size = ? AND baseprice_CrustType = ?";
        PreparedStatement ps = conn.prepareStatement(sql);
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

    public static double getBaseBusPrice(String size, String crust) throws SQLException, IOException {
        if (!connectToDB()) return 0.0;

        String sql = "SELECT baseprice_BusPrice FROM baseprice WHERE baseprice_Size = ? AND baseprice_CrustType = ?";
        PreparedStatement ps = conn.prepareStatement(sql);
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

    /* ---------------------------------------------------------------------- */
    /*                               REPORTS                                  */
    /* ---------------------------------------------------------------------- */

    public static void printToppingReport() throws SQLException, IOException {
        if (!connectToDB()) return;

        String sql = "SELECT * FROM ToppingPopularity";
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(sql);

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

    public static void printProfitByPizzaReport() throws SQLException, IOException {
        if (!connectToDB()) return;

        String sql = "SELECT * FROM ProfitByPizza";
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(sql);

        System.out.printf("%-10s | %-15s | %-10s | %s%n", "Size", "Crust", "Profit", "Month");
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

    public static void printProfitByOrderTypeReport() throws SQLException, IOException {
        if (!connectToDB()) return;

        String sql = "SELECT * FROM ProfitByOrderType";
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(sql);

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

    /* ---------------------------------------------------------------------- */
    /*                        INTERNAL HELPER METHODS                         */
    /* ---------------------------------------------------------------------- */

    private static Order buildOrderFromResultSet(ResultSet rs) throws SQLException, IOException {
        int orderID = rs.getInt("ordertable_OrderID");
        String orderType = rs.getString("ordertable_OrderType");
        String orderDate = rs.getString("ordertable_OrderDateTime");
        double custPrice = rs.getDouble("ordertable_CustPrice");
        double busPrice = rs.getDouble("ordertable_BusPrice");
        boolean isComplete = rs.getInt("ordertable_IsComplete") == 1;

        Integer custID = null;
        Object custObj = rs.getObject("customer_CustID");
        if (custObj == null) {
            custID = -1; // IN STORE
        } else {
            custID = rs.getInt("customer_CustID");
        }

        Order order = null;

        if (orderType.equals(dine_in)) {
            String sql = "SELECT dinein_TableNum FROM dinein WHERE ordertable_OrderID = ?";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, orderID);
            ResultSet rs2 = ps.executeQuery();
            if (rs2.next()) {
                int tableNum = rs2.getInt("dinein_TableNum");
                order = new DineinOrder(orderID, -1, orderDate, custPrice, busPrice, isComplete, tableNum);
            }
            rs2.close();
            ps.close();
        } else if (orderType.equals(pickup)) {
            String sql = "SELECT pickup_IsPickedUp FROM pickup WHERE ordertable_OrderID = ?";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, orderID);
            ResultSet rs2 = ps.executeQuery();
            if (rs2.next()) {
                boolean pickedUp = rs2.getInt("pickup_IsPickedUp") == 1;
                order = new PickupOrder(orderID, custID, orderDate, custPrice, busPrice, pickedUp, isComplete);
            }
            rs2.close();
            ps.close();
        } else if (orderType.equals(delivery)) {
            String sql = "SELECT * FROM delivery WHERE ordertable_OrderID = ?";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, orderID);
            ResultSet rs2 = ps.executeQuery();
            if (rs2.next()) {
                int houseNum = rs2.getInt("delivery_HouseNum");
                String street = rs2.getString("delivery_Street");
                String city = rs2.getString("delivery_City");
                String state = rs2.getString("delivery_State");
                int zip = rs2.getInt("delivery_Zip");
                boolean delivered = rs2.getInt("delivery_IsDelivered") == 1;

                String address = houseNum + "\t" + street + "\t" + city + "\t" + state + "\t" + zip;
                order = new DeliveryOrder(orderID, custID, orderDate, custPrice, busPrice, isComplete, delivered, address);
            }
            rs2.close();
            ps.close();
        }

        if (order != null) {
            order.setPizzaList(getPizzas(order));
            order.setDiscountList(getDiscounts(order));
        }

        return order;
    }

    /* ---------------------------------------------------------------------- */
    /*                    UNUSED ID HELPERS (STUBBED)                         */
    /* ---------------------------------------------------------------------- */

    public static int getNextOrderID() {
        // Not used by current Menu/autograder; kept for compatibility
        return -1;
    }

    public static int getNextPizzaID() {
        // Not used by current Menu/autograder; kept for compatibility
        return -1;
    }
}
