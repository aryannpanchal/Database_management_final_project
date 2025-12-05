package cpsc4620;

import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;

public class DBNinja {

    // Constants for Order Types
    public static final String dine_in = "dinein";
    public static final String pickup = "pickup";
    public static final String delivery = "delivery";

    // Constants for Pizza Sizes
    public static final String size_s = "Small";
    public static final String size_m = "Medium";
    public static final String size_l = "Large";
    public static final String size_xl = "XLarge";

    // Constants for Crusts
    public static final String crust_thin = "Thin";
    public static final String crust_orig = "Original";
    public static final String crust_pan = "Pan";
    public static final String crust_gf = "Gluten-Free";

    public enum order_state {
        OPEN, CLOSED, PREPARED, PICKEDUP, DELIVERED, COMPLETED
    }

    public static Connection conn = null;

    public static void connectToDB() throws SQLException, IOException {
        conn = DBConnector.getConnection();
    }

    public static void addOrder(Order o) throws SQLException, IOException {
        connectToDB();

        // 1) Insert into ordertable
        String sql = "INSERT INTO ordertable (ordertable_OrderType, ordertable_OrderDateTime, ordertable_CustPrice, ordertable_BusPrice, ordertable_IsComplete, customer_CustID) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, o.getOrderType());
            ps.setString(2, o.getDate());
            ps.setDouble(3, o.getCustPrice());
            ps.setDouble(4, o.getBusPrice());
            ps.setBoolean(5, o.isComplete());
            ps.setInt(6, o.getCustID());
            ps.executeUpdate();

            // Get the generated OrderID
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    int orderID = rs.getInt(1);
                    o.setOrderID(orderID);

                    // 2) Insert pizzas associated with this order
                    for (Pizza p : o.getPizzaList()) {
                        addPizza(new java.util.Date(), orderID, p);
                    }

                    // 3) Insert order-level discounts
                    for (Discount d : o.getDiscountList()) {
                        String sql2 = "INSERT INTO order_discount (ordertable_OrderID, discount_DiscountID) VALUES (?, ?)";
                        try (PreparedStatement ps2 = conn.prepareStatement(sql2)) {
                            ps2.setInt(1, orderID);
                            ps2.setInt(2, d.getDiscountID());
                            ps2.executeUpdate();
                        }
                    }

                    // 4) Handle type-specific tables (dinein, pickup, delivery)
                    if (o.getOrderType().equals(dine_in)) {
                        if (o instanceof DineinOrder) {
                            DineinOrder dineIn = (DineinOrder) o;
                            String sql3 = "INSERT INTO dinein (ordertable_OrderID, dinein_TableNum) VALUES (?, ?)";
                            try (PreparedStatement ps3 = conn.prepareStatement(sql3)) {
                                ps3.setInt(1, orderID);
                                ps3.setInt(2, dineIn.getTableNum());
                                ps3.executeUpdate();
                            }
                        }
                    } else if (o.getOrderType().equals(pickup)) {
                        if (o instanceof PickupOrder) {
                            PickupOrder pickupOrder = (PickupOrder) o;
                            String sql3 = "INSERT INTO pickup (ordertable_OrderID, pickup_IsPickedUp) VALUES (?, ?)";
                            try (PreparedStatement ps3 = conn.prepareStatement(sql3)) {
                                ps3.setInt(1, orderID);
                                ps3.setBoolean(2, pickupOrder.isPickedUp());
                                ps3.executeUpdate();
                            }
                        }
                    } else if (o.getOrderType().equals(delivery)) {
                        if (o instanceof DeliveryOrder) {
                            DeliveryOrder deliveryOrder = (DeliveryOrder) o;
                            String sql3 = "INSERT INTO delivery (ordertable_OrderID, delivery_Address, delivery_IsDelivered) VALUES (?, ?, ?)";
                            try (PreparedStatement ps3 = conn.prepareStatement(sql3)) {
                                ps3.setInt(1, orderID);
                                ps3.setString(2, deliveryOrder.getAddress());
                                ps3.setBoolean(3, deliveryOrder.isDelivered());
                                ps3.executeUpdate();
                            }
                        }
                    }
                }
            }
        }
    }

    public static void addPizza(java.util.Date date, int orderID, Pizza p) throws SQLException, IOException {
        connectToDB();

        // Insert pizza into pizza table
        String sql = "INSERT INTO pizza (ordertable_OrderID, pizza_Size, pizza_CrustType, pizza_Status, pizza_DateAdded, pizza_CustPrice, pizza_BusPrice) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, orderID);
            ps.setString(2, p.getSize());
            ps.setString(3, p.getCrust());
            ps.setString(4, p.getStatus());
            ps.setString(5, new java.sql.Timestamp(date.getTime()).toString());
            ps.setDouble(6, p.getCustPrice());
            ps.setDouble(7, p.getBusPrice());
            ps.executeUpdate();

            // Get the generated PizzaID
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    int pizzaID = rs.getInt(1);
                    p.setPizzaID(pizzaID);

                    // Add toppings to this pizza
                    for (Topping t : p.getToppings()) {
                        // Add to pizza_topping junction table
                        String sql2 = "INSERT INTO pizza_topping (pizza_PizzaID, topping_TopID, pizza_topping_Doubled) VALUES (?, ?, ?)";
                        try (PreparedStatement ps2 = conn.prepareStatement(sql2)) {
                            ps2.setInt(1, pizzaID);
                            ps2.setInt(2, t.getTopID());
                            ps2.setBoolean(3, t.getDoubled());
                            ps2.executeUpdate();
                        }

                        // Update inventory
                        // compute units used for inventory based on size
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

                        // IMPORTANT: Use ceiling and subtract from inventory
                        int unitsToSubtract = (int) Math.ceil(unitsNeeded);
                        String updateInv = "UPDATE topping SET topping_CurINVT = topping_CurINVT - ? WHERE topping_TopID = ?";
                        try (PreparedStatement ps3 = conn.prepareStatement(updateInv)) {
                            ps3.setInt(1, unitsToSubtract);
                            ps3.setInt(2, t.getTopID());
                            ps3.executeUpdate();
                        }
                    }

                    // Add pizza-level discounts
                    for (Discount d : p.getDiscounts()) {
                        String sql3 = "INSERT INTO pizza_discount (pizza_PizzaID, discount_DiscountID) VALUES (?, ?)";
                        try (PreparedStatement ps3 = conn.prepareStatement(sql3)) {
                            ps3.setInt(1, pizzaID);
                            ps3.setInt(2, d.getDiscountID());
                            ps3.executeUpdate();
                        }
                    }
                }
            }
        }
    }

    public static void completeOrder(int orderID, order_state state) throws SQLException, IOException {
        connectToDB();

        // Always mark as complete in ordertable
        String updateOrder = "UPDATE ordertable SET ordertable_IsComplete = 1 WHERE ordertable_OrderID = ?";
        try (PreparedStatement ps = conn.prepareStatement(updateOrder)) {
            ps.setInt(1, orderID);
            ps.executeUpdate();
        }

        // Mark pickup/delivery based on STATE parameter
        String typeSql = "SELECT ordertable_OrderType FROM ordertable WHERE ordertable_OrderID = ?";
        try (PreparedStatement ps = conn.prepareStatement(typeSql)) {
            ps.setInt(1, orderID);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String type = rs.getString(1);

                    // pickup: mark picked up for PICKEDUP or COMPLETED states
                    if (pickup.equals(type) && (state == order_state.PICKEDUP || state == order_state.COMPLETED)) {
                        String updPickup = "UPDATE pickup SET pickup_IsPickedUp = 1 WHERE ordertable_OrderID = ?";
                        try (PreparedStatement ps2 = conn.prepareStatement(updPickup)) {
                            ps2.setInt(1, orderID);
                            ps2.executeUpdate();
                        }
                    }

                    // delivery: mark delivered for DELIVERED or COMPLETED states
                    if (delivery.equals(type) && (state == order_state.DELIVERED || state == order_state.COMPLETED)) {
                        String updDelivery = "UPDATE delivery SET delivery_IsDelivered = 1 WHERE ordertable_OrderID = ?";
                        try (PreparedStatement ps2 = conn.prepareStatement(updDelivery)) {
                            ps2.setInt(1, orderID);
                            ps2.executeUpdate();
                        }
                    }
                }
            }
        }
    }

    public static ArrayList<Order> getOrders(int flag) throws SQLException, IOException {
        connectToDB();
        ArrayList<Order> list = new ArrayList<>();

        String sql = "";
        if (flag == 1) {
            sql = "SELECT * FROM ordertable WHERE ordertable_IsComplete = 0";
        } else if (flag == 2) {
            sql = "SELECT * FROM ordertable WHERE ordertable_IsComplete = 1";
        } else if (flag == 3) {
            sql = "SELECT * FROM ordertable";
        }

        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                Order o = createOrderFromResultSet(rs);
                list.add(o);
            }
        }

        return list;
    }

    public static Order getLastOrder() throws SQLException, IOException {
        connectToDB();
        String sql = "SELECT * FROM ordertable ORDER BY ordertable_OrderID DESC LIMIT 1";

        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) {
                return createOrderFromResultSet(rs);
            }
        }
        return null;
    }

    public static ArrayList<Order> getOrdersByDate(String date) throws SQLException, IOException {
        connectToDB();
        ArrayList<Order> list = new ArrayList<>();

        String sql = "SELECT * FROM ordertable WHERE DATE(ordertable_OrderDateTime) = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, date);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Order o = createOrderFromResultSet(rs);
                    list.add(o);
                }
            }
        }

        return list;
    }

    private static Order createOrderFromResultSet(ResultSet rs) throws SQLException, IOException {
        int orderID = rs.getInt("ordertable_OrderID");
        int custID = rs.getInt("customer_CustID");
        String orderType = rs.getString("ordertable_OrderType");
        String date = rs.getString("ordertable_OrderDateTime");
        double custPrice = rs.getDouble("ordertable_CustPrice");
        double busPrice = rs.getDouble("ordertable_BusPrice");
        boolean isComplete = rs.getBoolean("ordertable_IsComplete");

        Order o = null;

        if (orderType.equals(dine_in)) {
            String sql = "SELECT dinein_TableNum FROM dinein WHERE ordertable_OrderID = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, orderID);
                try (ResultSet rs2 = ps.executeQuery()) {
                    if (rs2.next()) {
                        int tableNum = rs2.getInt("dinein_TableNum");
                        o = new DineinOrder(orderID, custID, date, custPrice, busPrice, isComplete, tableNum);
                    }
                }
            }
        } else if (orderType.equals(pickup)) {
            String sql = "SELECT pickup_IsPickedUp FROM pickup WHERE ordertable_OrderID = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, orderID);
                try (ResultSet rs2 = ps.executeQuery()) {
                    if (rs2.next()) {
                        boolean isPickedUp = rs2.getBoolean("pickup_IsPickedUp");
                        o = new PickupOrder(orderID, custID, date, custPrice, busPrice, isPickedUp, isComplete);
                    }
                }
            }
        } else if (orderType.equals(delivery)) {
            String sql = "SELECT delivery_Address, delivery_IsDelivered FROM delivery WHERE ordertable_OrderID = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, orderID);
                try (ResultSet rs2 = ps.executeQuery()) {
                    if (rs2.next()) {
                        String address = rs2.getString("delivery_Address");
                        boolean isDelivered = rs2.getBoolean("delivery_IsDelivered");
                        o = new DeliveryOrder(orderID, custID, date, custPrice, busPrice, isDelivered, address);
                    }
                }
            }
        }

        // Load pizzas and discounts
        if (o != null) {
            o.setPizzaList(getPizzas(o));
            o.setDiscountList(getDiscounts(o));
        }

        return o;
    }

    public static ArrayList<Pizza> getPizzas(Order o) throws SQLException, IOException {
        connectToDB();
        ArrayList<Pizza> list = new ArrayList<>();

        String sql = "SELECT * FROM pizza WHERE ordertable_OrderID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, o.getOrderID());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Pizza p = new Pizza(
                            rs.getInt("pizza_PizzaID"),
                            rs.getString("pizza_Size"),
                            rs.getString("pizza_CrustType"),
                            rs.getInt("ordertable_OrderID"),
                            rs.getString("pizza_Status"),
                            rs.getString("pizza_DateAdded"),
                            rs.getDouble("pizza_CustPrice"),
                            rs.getDouble("pizza_BusPrice")
                    );

                    // Get toppings for this pizza
                    p.setToppings(getToppingsOnPizza(p));

                    // Get discounts for this pizza
                    p.setDiscounts(getDiscounts(p));

                    list.add(p);
                }
            }
        }

        return list;
    }

    public static ArrayList<Topping> getToppingsOnPizza(Pizza p) throws SQLException, IOException {
        connectToDB();
        ArrayList<Topping> list = new ArrayList<>();

        String sql = "SELECT t.*, pt.pizza_topping_Doubled FROM topping t " +
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
                    t.setDoubled(rs.getBoolean("pizza_topping_Doubled"));
                    list.add(t);
                }
            }
        }

        return list;
    }

    public static ArrayList<Discount> getDiscounts(Order o) throws SQLException, IOException {
        connectToDB();
        ArrayList<Discount> list = new ArrayList<>();

        String sql = "SELECT d.* FROM discount d " +
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

    public static ArrayList<Discount> getDiscounts(Pizza p) throws SQLException, IOException {
        connectToDB();
        ArrayList<Discount> list = new ArrayList<>();

        String sql = "SELECT d.* FROM discount d " +
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

    public static ArrayList<Customer> getCustomerList() throws SQLException, IOException {
        connectToDB();
        ArrayList<Customer> list = new ArrayList<>();
        String sql = "SELECT customer_CustID, customer_FName, customer_LName, customer_PhoneNum " +
                "FROM customer " +
                "ORDER BY customer_LName ASC, customer_FName ASC";
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

    public static int addCustomer(Customer c) throws SQLException, IOException {
        connectToDB();
        String sql = "INSERT INTO customer (customer_FName, customer_LName, customer_PhoneNum) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, c.getFName());
            ps.setString(2, c.getLName());
            ps.setString(3, c.getPhone());
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return -1;
    }

    public static Customer findCustomerByPhone(String phone) throws SQLException, IOException {
        connectToDB();
        String sql = "SELECT * FROM customer WHERE customer_PhoneNum = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, phone);
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
        connectToDB();
        String sql = "SELECT customer_FName, customer_LName FROM customer WHERE customer_CustID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, custID);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("customer_FName") + " " + rs.getString("customer_LName");
                }
            }
        }
        return "";
    }

    // ✅ FIXED: getToppingList() - Changed ORDER BY to DESC
    public static ArrayList<Topping> getToppingList() throws SQLException, IOException {
        connectToDB();
        ArrayList<Topping> list = new ArrayList<>();
        String sql = "SELECT * FROM topping ORDER BY topping_TopID DESC";  // ✅ DESC (was ASC)
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
        String sql = "SELECT * FROM topping WHERE topping_TopName = ?";
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

    // ✅ FIXED: getDiscountList() - Changed ORDER BY column to discount_DiscountID
    public static ArrayList<Discount> getDiscountList() throws SQLException, IOException {
        connectToDB();
        ArrayList<Discount> list = new ArrayList<>();
        String sql = 
            "SELECT discount_DiscountID, discount_DiscountName, discount_Amount, discount_IsPercent " +
            "FROM discount " +
            "ORDER BY discount_DiscountID ASC";  // ✅ discount_DiscountID (was discount_DiscountName)
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
        String sql = "SELECT * FROM discount WHERE discount_DiscountName = ?";
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

    public static double getBaseCustPrice(String size, String crust) throws SQLException, IOException {
        connectToDB();
        String sql = "SELECT pizza_BasePrice FROM pizza_base WHERE pizza_Size = ? AND pizza_CrustType = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, size);
            ps.setString(2, crust);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble("pizza_BasePrice");
                }
            }
        }
        return 0.0;
    }

    public static double getBaseBusPrice(String size, String crust) throws SQLException, IOException {
        connectToDB();
        String sql = "SELECT pizza_BusPrice FROM pizza_base WHERE pizza_Size = ? AND pizza_CrustType = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, size);
            ps.setString(2, crust);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble("pizza_BusPrice");
                }
            }
        }
        return 0.0;
    }

    public static void addToInventory(int toppingID, double amount) throws SQLException, IOException {
        connectToDB();
        String sql = "UPDATE topping SET topping_CurINVT = topping_CurINVT + ? WHERE topping_TopID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, amount);
            ps.setInt(2, toppingID);
            ps.executeUpdate();
        }
    }

    public static void printToppingReport() throws SQLException, IOException {
        connectToDB();
        String sql = "SELECT * FROM ToppingPopularity";
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            System.out.printf("%-20s%-20s%n", "Topping Name", "Times Ordered");
            System.out.printf("%-20s%-20s%n", "-------------", "--------------");
            while (rs.next()) {
                System.out.printf("%-20s%-20s%n",
                        rs.getString("ToppingName"),
                        rs.getInt("TimesOrdered"));
            }
        }
    }

    public static void printProfitByPizzaReport() throws SQLException, IOException {
        connectToDB();
        String sql = "SELECT * FROM ProfitByPizza";
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            System.out.printf("%-15s%-15s%-15s%-15s%n", "Size", "Crust", "Profit", "Month");
            System.out.printf("%-15s%-15s%-15s%-15s%n", "----", "-----", "------", "-----");
            while (rs.next()) {
                System.out.printf("%-15s%-15s%-15.2f%-15s%n",
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
            System.out.printf("%-15s%-15s%-15s%n", "Customer Type", "Profit", "Month");
            System.out.printf("%-15s%-15s%-15s%n", "-------------", "------", "-----");
            while (rs.next()) {
                System.out.printf("%-15s%-15.2f%-15s%n",
                        rs.getString("CustomerType"),
                        rs.getDouble("Profit"),
                        rs.getString("OrderMonth"));
            }
        }
    }
}