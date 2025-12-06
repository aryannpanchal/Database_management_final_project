package cpsc4620;

import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.Comparator;

/*
 * This file is where you will implement the methods needed to support this application.
 * You will write the code to retrieve and save information to the database and use that
 * information to build the various objects required by the application.
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
    // =========================================================
    // CONSTANTS
    // =========================================================
    public static final String PICKUP = "pickup";
    public static final String DELIVERY = "delivery";
    public static final String DINE_IN = "dinein";
    
    public static final String SIZE_SMALL = "Small";
    public static final String SIZE_MEDIUM = "Medium";
    public static final String SIZE_LARGE = "Large";
    public static final String SIZE_XLARGE = "XLarge";
    
    public static final String CRUST_THIN = "Thin";
    public static final String CRUST_ORIGINAL = "Original";
    public static final String CRUST_PAN = "Pan";
    public static final String CRUST_GLUTEN_FREE = "Gluten-Free";

    static class order_state {

        static OrderState PREPARED;

        public order_state() {
        }
    }
    
    public enum OrderState {
        PREPARED,
        DELIVERED,
        PICKEDUP
    }
    
    // =========================================================
    // DATABASE CONNECTION
    // =========================================================
    private static Connection databaseConnection;
    
    // =========================================================
    // CONNECTION MANAGEMENT
    // =========================================================
    
    private static boolean connect_to_db() throws SQLException, IOException {
        if (databaseConnection == null || databaseConnection.isClosed()) {
            databaseConnection = DBConnector.make_connection();
        }
        return databaseConnection != null && !databaseConnection.isClosed();
    }
    
    private static void closeConnection() throws SQLException {
        if (databaseConnection != null && !databaseConnection.isClosed()) {
            databaseConnection.close();
        }
    }
    
    private static DatabaseSession startSession() throws SQLException, IOException {
        boolean connectionEstablishedHere = connect_to_db();
        return new DatabaseSession(databaseConnection, connectionEstablishedHere);
    }
    
    // =========================================================
    // PUBLIC API: WRITE OPERATIONS
    // =========================================================
    
    public static void addOrder(Order order) throws SQLException, IOException {
        try (DatabaseSession databaseSession = startSession()) {
            databaseSession.beginTransaction();
            
            int generatedOrderId = insertOrderRecord(order);
            order.setOrderID(generatedOrderId);
            
            insertOrderTypeDetails(order, generatedOrderId);
            insertPizzasForOrder(order, generatedOrderId);
            insertOrderDiscounts(order, generatedOrderId);
            
            databaseSession.commit();
        }
    }
    
    public static int addPizza(java.util.Date pizzaDate, int orderIdentifier, Pizza pizzaItem) throws SQLException, IOException {
        try (DatabaseSession databaseSession = startSession()) {
            databaseSession.beginTransaction();
            
            int generatedPizzaId = insertPizzaWithDetails(pizzaDate, orderIdentifier, pizzaItem);
            
            databaseSession.commit();
            return generatedPizzaId;
        }
    }
    
    public static int addCustomer(Customer customerData) throws SQLException, IOException {
        try (DatabaseSession databaseSession = startSession()) {
            String insertCustomerQuery = "INSERT INTO customer (customer_FName, customer_LName, customer_PhoneNum) VALUES (?,?,?)";
            
            try (PreparedStatement customerStatement = databaseSession.prepareStatement(insertCustomerQuery, Statement.RETURN_GENERATED_KEYS)) {
                customerStatement.setString(1, customerData.getFName());
                customerStatement.setString(2, customerData.getLName());
                customerStatement.setString(3, customerData.getPhone());
                customerStatement.executeUpdate();
                
                ResultSet generatedCustomerKeys = customerStatement.getGeneratedKeys();
                if (generatedCustomerKeys.next()) {
                    return generatedCustomerKeys.getInt(1);
                }
            }
            return -1;
        }
    }
    
    public static void completeOrder(int targetOrderId, OrderState newOrderState) throws SQLException, IOException {
        try (DatabaseSession databaseSession = startSession()) {
            switch (newOrderState) {
                case PREPARED:
                    markOrderAsComplete(targetOrderId, databaseSession);
                    markPizzasAsComplete(targetOrderId, databaseSession);
                    break;
                    
                case DELIVERED:
                    markDeliveryAsDelivered(targetOrderId, databaseSession);
                    break;
                    
                case PICKEDUP:
                    markPickupAsPickedUp(targetOrderId, databaseSession);
                    break;
            }
        }
    }
    
    // =========================================================
    // PUBLIC API: READ OPERATIONS
    // =========================================================
    
    public static ArrayList<Order> getOrders(int orderStatus) throws SQLException, IOException {
        try (DatabaseSession databaseSession = startSession()) {
            String orderQuery = buildOrderQuery(orderStatus);
            
            try (PreparedStatement orderStatement = databaseSession.prepareStatement(orderQuery)) {
                ResultSet orderResultSet = orderStatement.executeQuery();
                
                ArrayList<Order> orderCollection = new ArrayList<>();
                while (orderResultSet.next()) {
                    Order currentOrder = buildOrderFromResultSet(orderResultSet, databaseSession.getConnection());
                    orderCollection.add(currentOrder);
                }
                
                orderCollection.sort(Comparator.comparingInt(Order::getOrderID));
                return orderCollection;
            }
        }
    }
    
    public static Order getLastOrder() throws SQLException, IOException {
        try (DatabaseSession databaseSession = startSession()) {
            String lastOrderQuery = "SELECT * FROM ordertable ORDER BY ordertable_OrderID DESC LIMIT 1";
            
            try (PreparedStatement lastOrderStatement = databaseSession.prepareStatement(lastOrderQuery)) {
                ResultSet lastOrderResultSet = lastOrderStatement.executeQuery();
                
                if (lastOrderResultSet.next()) {
                    return buildOrderFromResultSet(lastOrderResultSet, databaseSession.getConnection());
                }
                return null;
            }
        }
    }
    
    public static ArrayList<Order> getOrdersByDate(String targetDate) throws SQLException, IOException {
        try (DatabaseSession databaseSession = startSession()) {
            String dateQuery = "SELECT * FROM ordertable WHERE DATE(ordertable_OrderDateTime)=? ORDER BY ordertable_OrderDateTime ASC";
            
            try (PreparedStatement dateStatement = databaseSession.prepareStatement(dateQuery)) {
                dateStatement.setString(1, targetDate);
                ResultSet dateResultSet = dateStatement.executeQuery();
                
                ArrayList<Order> ordersByDate = new ArrayList<>();
                while (dateResultSet.next()) {
                    Order currentOrder = buildOrderFromResultSet(dateResultSet, databaseSession.getConnection());
                    ordersByDate.add(currentOrder);
                }
                return ordersByDate;
            }
        }
    }
    
    public static ArrayList<Discount> getDiscountList() throws SQLException, IOException {
        try (DatabaseSession databaseSession = startSession()) {
            String discountQuery = "SELECT * FROM discount ORDER BY discount_DiscountName ASC";
            
            try (PreparedStatement discountStatement = databaseSession.prepareStatement(discountQuery)) {
                ResultSet discountResultSet = discountStatement.executeQuery();
                
                ArrayList<Discount> discountCollection = new ArrayList<>();
                while (discountResultSet.next()) {
                    discountCollection.add(createDiscountFromResultSet(discountResultSet));
                }
                return discountCollection;
            }
        }
    }
    
    public static Discount findDiscountByName(String discountName) throws SQLException, IOException {
        try (DatabaseSession databaseSession = startSession()) {
            String discountSearchQuery = "SELECT * FROM discount WHERE discount_DiscountName=?";
            
            try (PreparedStatement discountSearchStatement = databaseSession.prepareStatement(discountSearchQuery)) {
                discountSearchStatement.setString(1, discountName);
                ResultSet discountResultSet = discountSearchStatement.executeQuery();
                
                if (discountResultSet.next()) {
                    return createDiscountFromResultSet(discountResultSet);
                }
                return null;
            }
        }
    }
    
    public static ArrayList<Customer> getCustomerList() throws SQLException, IOException {
        try (DatabaseSession databaseSession = startSession()) {
            String customerQuery = "SELECT * FROM customer ORDER BY customer_LName, customer_FName, customer_PhoneNum";
            
            try (PreparedStatement customerStatement = databaseSession.prepareStatement(customerQuery)) {
                ResultSet customerResultSet = customerStatement.executeQuery();
                
                ArrayList<Customer> customerCollection = new ArrayList<>();
                while (customerResultSet.next()) {
                    customerCollection.add(createCustomerFromResultSet(customerResultSet));
                }
                return customerCollection;
            }
        }
    }
    
    public static Customer findCustomerByPhone(String phoneNumberString) throws SQLException, IOException {
        try (DatabaseSession databaseSession = startSession()) {
            String phoneQuery = "SELECT * FROM customer WHERE customer_PhoneNum=?";
            
            try (PreparedStatement phoneStatement = databaseSession.prepareStatement(phoneQuery)) {
                phoneStatement.setString(1, phoneNumberString);
                ResultSet phoneResultSet = phoneStatement.executeQuery();
                
                if (phoneResultSet.next()) {
                    return createCustomerFromResultSet(phoneResultSet);
                }
                return null;
            }
        }
    }
    
    public static String getCustomerName(int customerIdentifier) throws SQLException, IOException {
        try (DatabaseSession databaseSession = startSession()) {
            String nameQuery = "SELECT customer_FName, customer_LName FROM customer WHERE customer_CustID=?";
            
            try (PreparedStatement nameStatement = databaseSession.prepareStatement(nameQuery)) {
                nameStatement.setInt(1, customerIdentifier);
                ResultSet nameResultSet = nameStatement.executeQuery();
                
                if (nameResultSet.next()) {
                    return nameResultSet.getString("customer_FName") + " " + nameResultSet.getString("customer_LName");
                }
                return "";
            }
        }
    }
    
    public static ArrayList<Topping> getToppingList() throws SQLException, IOException {
        try (DatabaseSession databaseSession = startSession()) {
            String toppingQuery = "SELECT * FROM topping ORDER BY topping_TopName";
            
            try (PreparedStatement toppingStatement = databaseSession.prepareStatement(toppingQuery)) {
                ResultSet toppingResultSet = toppingStatement.executeQuery();
                
                ArrayList<Topping> toppingCollection = new ArrayList<>();
                while (toppingResultSet.next()) {
                    toppingCollection.add(createToppingFromResultSet(toppingResultSet));
                }
                return toppingCollection;
            }
        }
    }
    
    public static Topping findToppingByName(String toppingName) throws SQLException, IOException {
        try (DatabaseSession databaseSession = startSession()) {
            String toppingSearchQuery = "SELECT * FROM topping WHERE topping_TopName=?";
            
            try (PreparedStatement toppingSearchStatement = databaseSession.prepareStatement(toppingSearchQuery)) {
                toppingSearchStatement.setString(1, toppingName);
                ResultSet toppingResultSet = toppingSearchStatement.executeQuery();
                
                if (toppingResultSet.next()) {
                    return createToppingFromResultSet(toppingResultSet);
                }
                return null;
            }
        }
    }
    
    public static ArrayList<Topping> getToppingsOnPizza(Pizza pizzaItem) throws SQLException, IOException {
        try (DatabaseSession databaseSession = startSession()) {
            return fetchPizzaToppings(pizzaItem.getPizzaID(), databaseSession.getConnection());
        }
    }
    
    public static void addToInventory(int toppingIdentifier, double quantityToAdd) throws SQLException, IOException {
        try (DatabaseSession databaseSession = startSession()) {
            String inventoryUpdateQuery = "UPDATE topping SET topping_CurINVT = topping_CurINVT + ? WHERE topping_TopID=?";
            
            try (PreparedStatement inventoryStatement = databaseSession.prepareStatement(inventoryUpdateQuery)) {
                inventoryStatement.setDouble(1, quantityToAdd);
                inventoryStatement.setInt(2, toppingIdentifier);
                inventoryStatement.executeUpdate();
            }
        }
    }
    
    public static ArrayList<Pizza> getPizzas(Order targetOrder) throws SQLException, IOException {
        try (DatabaseSession databaseSession = startSession()) {
            return fetchPizzasForOrder(targetOrder.getOrderID(), databaseSession.getConnection());
        }
    }
    
    public static ArrayList<Discount> getDiscounts(Order targetOrder) throws SQLException, IOException {
        try (DatabaseSession databaseSession = startSession()) {
            return fetchOrderDiscounts(targetOrder.getOrderID(), databaseSession.getConnection());
        }
    }
    
    public static ArrayList<Discount> getDiscounts(Pizza pizzaItem) throws SQLException, IOException {
        try (DatabaseSession databaseSession = startSession()) {
            return fetchPizzaDiscounts(pizzaItem.getPizzaID(), databaseSession.getConnection());
        }
    }
    
    public static double getBaseCustPrice(String pizzaSize, String crustType) throws SQLException, IOException {
        try (DatabaseSession databaseSession = startSession()) {
            String priceQuery = "SELECT baseprice_CustPrice FROM baseprice WHERE baseprice_Size=? AND baseprice_CrustType=?";
            
            try (PreparedStatement priceStatement = databaseSession.prepareStatement(priceQuery)) {
                priceStatement.setString(1, pizzaSize);
                priceStatement.setString(2, crustType);
                ResultSet priceResultSet = priceStatement.executeQuery();
                
                if (priceResultSet.next()) {
                    return priceResultSet.getDouble(1);
                }
                return 0.0;
            }
        }
    }
    
    public static double getBaseBusPrice(String pizzaSize, String crustType) throws SQLException, IOException {
        try (DatabaseSession databaseSession = startSession()) {
            String costQuery = "SELECT baseprice_BusPrice FROM baseprice WHERE baseprice_Size=? AND baseprice_CrustType=?";
            
            try (PreparedStatement costStatement = databaseSession.prepareStatement(costQuery)) {
                costStatement.setString(1, pizzaSize);
                costStatement.setString(2, crustType);
                ResultSet costResultSet = costStatement.executeQuery();
                
                if (costResultSet.next()) {
                    return costResultSet.getDouble(1);
                }
                return 0.0;
            }
        }
    }
    
    // =========================================================
    // REPORT METHODS
    // =========================================================
    
    public static void printToppingReport() throws SQLException, IOException {
        try (DatabaseSession databaseSession = startSession()) {
            String toppingReportQuery = "SELECT * FROM ToppingPopularity";
            
            try (PreparedStatement reportStatement = databaseSession.prepareStatement(toppingReportQuery)) {
                ResultSet reportResultSet = reportStatement.executeQuery();
                
                System.out.printf("%-15s%-15s%n", "Topping", "Topping Count");
                System.out.printf("%-15s%-15s%n", "-------", "-------------");
                
                while (reportResultSet.next()) {
                    System.out.printf("%-15s%-15s%n", 
                        reportResultSet.getString(1), 
                        reportResultSet.getInt(2));
                }
            }
        }
    }
    
    public static void printProfitByPizzaReport() throws SQLException, IOException {
        try (DatabaseSession databaseSession = startSession()) {
            String pizzaProfitQuery = "SELECT * FROM ProfitByPizza";
            
            try (PreparedStatement profitStatement = databaseSession.prepareStatement(pizzaProfitQuery)) {
                ResultSet profitResultSet = profitStatement.executeQuery();
                
                System.out.printf("%-20s%-20s%-20s%-20s%n", 
                    "Pizza Size", "Pizza Crust", "Profit", "Last Order Date");
                System.out.printf("%-20s%-20s%-20s%-20s%n", 
                    "----------", "-----------", "------", "---------------");
                
                while (profitResultSet.next()) {
                    System.out.printf("%-20s%-20s%-20s%-20s%n",
                        profitResultSet.getString(1),
                        profitResultSet.getString(2),
                        profitResultSet.getString(3),
                        profitResultSet.getString(4));
                }
            }
        }
    }
    
    public static void printProfitByOrderTypeReport() throws SQLException, IOException {
        try (DatabaseSession databaseSession = startSession()) {
            String orderProfitQuery = "SELECT * FROM ProfitByOrderType";
            
            try (PreparedStatement profitStatement = databaseSession.prepareStatement(orderProfitQuery)) {
                ResultSet profitResultSet = profitStatement.executeQuery();
                
                System.out.printf("%-20s%-20s%-20s%-20s%-20s%n",
                    "Customer Type", "Order Month", "Total Order Price", 
                    "Total Order Cost", "Profit");
                System.out.printf("%-20s%-20s%-20s%-20s%-20s%n",
                    "-------------", "-----------", "-----------------", 
                    "----------------", "------");
                
                while (profitResultSet.next()) {
                    System.out.printf("%-20s%-20s%-20s%-20s%-20s%n",
                        profitResultSet.getString(1),
                        profitResultSet.getString(2),
                        profitResultSet.getString(3),
                        profitResultSet.getString(4),
                        profitResultSet.getString(5));
                }
            }
        }
    }
    
    // =========================================================
    // DATE HELPER METHODS
    // =========================================================
    
    private static int extractYearFromDate(String dateString) {
        return Integer.parseInt(dateString.substring(0, 4));
    }
    
    private static int extractMonthFromDate(String dateString) {
        return Integer.parseInt(dateString.substring(5, 7));
    }
    
    private static int extractDayFromDate(String dateString) {
        return Integer.parseInt(dateString.substring(8, 10));
    }
    
    public static boolean checkDate(int targetYear, int targetMonth, int targetDay, String orderDateString) {
        int orderYear = extractYearFromDate(orderDateString);
        int orderMonth = extractMonthFromDate(orderDateString);
        int orderDay = extractDayFromDate(orderDateString);
        
        if (orderYear > targetYear) return true;
        if (orderYear < targetYear) return false;
        
        if (orderMonth > targetMonth) return true;
        if (orderMonth < targetMonth) return false;
        
        return orderDay >= targetDay;
    }
    
    // =========================================================
    // PRIVATE HELPER METHODS: ORDER OPERATIONS
    // =========================================================
    
    private static int insertOrderRecord(Order orderData) throws SQLException {
        String orderInsertQuery = "INSERT INTO ordertable (customer_CustID, ordertable_OrderType, " +
                    "ordertable_OrderDateTime, ordertable_CustPrice, " +
                    "ordertable_BusPrice, ordertable_IsComplete) " +
                    "VALUES (?,?,?,?,?,?)";
        
        try (PreparedStatement orderStatement = databaseConnection.prepareStatement(orderInsertQuery, Statement.RETURN_GENERATED_KEYS)) {
            if (orderData.getCustID() <= 0) {
                orderStatement.setNull(1, Types.INTEGER);
            } else {
                orderStatement.setInt(1, orderData.getCustID());
            }
            
            orderStatement.setString(2, orderData.getOrderType());
            orderStatement.setTimestamp(3, parseTimestampString(orderData.getDate()));
            orderStatement.setDouble(4, orderData.getCustPrice());
            orderStatement.setDouble(5, orderData.getBusPrice());
            orderStatement.setBoolean(6, orderData.getIsComplete());
            orderStatement.executeUpdate();
            
            ResultSet generatedOrderKeys = orderStatement.getGeneratedKeys();
            if (generatedOrderKeys.next()) {
                return generatedOrderKeys.getInt(1);
            }
            throw new SQLException("Failed to retrieve generated order identifier");
        }
    }
    
    private static void insertOrderTypeDetails(Order orderData, int orderIdentifier) throws SQLException {
        if (orderData instanceof DineinOrder) {
            insertDineinOrderDetails((DineinOrder) orderData, orderIdentifier);
        } else if (orderData instanceof PickupOrder) {
            insertPickupOrderDetails((PickupOrder) orderData, orderIdentifier);
        } else if (orderData instanceof DeliveryOrder) {
            insertDeliveryOrderDetails((DeliveryOrder) orderData, orderIdentifier);
        }
    }
    
    private static void insertDineinOrderDetails(DineinOrder dineinOrder, int orderIdentifier) throws SQLException {
        String dineinInsertQuery = "INSERT INTO dinein (ordertable_OrderID, dinein_TableNum) VALUES (?,?)";
        
        try (PreparedStatement dineinStatement = databaseConnection.prepareStatement(dineinInsertQuery)) {
            dineinStatement.setInt(1, orderIdentifier);
            dineinStatement.setInt(2, dineinOrder.getTableNum());
            dineinStatement.executeUpdate();
        }
    }
    
    private static void insertPickupOrderDetails(PickupOrder pickupOrder, int orderIdentifier) throws SQLException {
        String pickupInsertQuery = "INSERT INTO pickup (ordertable_OrderID, pickup_IsPickedUp) VALUES (?,?)";
        
        try (PreparedStatement pickupStatement = databaseConnection.prepareStatement(pickupInsertQuery)) {
            pickupStatement.setInt(1, orderIdentifier);
            pickupStatement.setBoolean(2, pickupOrder.getIsPickedUp());
            pickupStatement.executeUpdate();
        }
    }
    
    private static void insertDeliveryOrderDetails(DeliveryOrder deliveryOrder, int orderIdentifier) throws SQLException {
        String deliveryInsertQuery = "INSERT INTO delivery (ordertable_OrderID, delivery_HouseNum, " +
                    "delivery_Street, delivery_City, delivery_State, " +
                    "delivery_Zip, delivery_IsDelivered) VALUES (?,?,?,?,?,?,?)";
        
        String[] addressComponents = parseAddressString(deliveryOrder.getAddress());
        
        try (PreparedStatement deliveryStatement = databaseConnection.prepareStatement(deliveryInsertQuery)) {
            deliveryStatement.setInt(1, orderIdentifier);
            deliveryStatement.setInt(2, Integer.parseInt(addressComponents[0]));
            deliveryStatement.setString(3, addressComponents[1]);
            deliveryStatement.setString(4, addressComponents[2]);
            deliveryStatement.setString(5, addressComponents[3]);
            deliveryStatement.setInt(6, Integer.parseInt(addressComponents[4]));
            deliveryStatement.setBoolean(7, deliveryOrder.getIsDelivered());
            deliveryStatement.executeUpdate();
        }
    }
    
    private static void insertPizzasForOrder(Order orderData, int orderIdentifier) throws SQLException, IOException {
        Timestamp orderTimestamp = parseTimestampString(orderData.getDate());
        
        for (Pizza currentPizza : orderData.getPizzaList()) {
            currentPizza.setOrderID(orderIdentifier);
            insertPizzaWithDetails(orderTimestamp, orderIdentifier, currentPizza);
        }
    }
    
    private static void insertOrderDiscounts(Order orderData, int orderIdentifier) throws SQLException {
        if (orderData.getDiscountList() == null || orderData.getDiscountList().isEmpty()) {
            return;
        }
        
        String discountInsertQuery = "INSERT INTO order_discount (ordertable_OrderID, discount_DiscountID) VALUES (?,?)";
        
        for (Discount currentDiscount : orderData.getDiscountList()) {
            try (PreparedStatement discountStatement = databaseConnection.prepareStatement(discountInsertQuery)) {
                discountStatement.setInt(1, orderIdentifier);
                discountStatement.setInt(2, currentDiscount.getDiscountID());
                discountStatement.executeUpdate();
            }
        }
    }
    
    // =========================================================
    // PRIVATE HELPER METHODS: PIZZA OPERATIONS
    // =========================================================
    
    private static int insertPizzaWithDetails(java.util.Date pizzaDate, int orderIdentifier, Pizza pizzaData) 
            throws SQLException, IOException {
        Timestamp pizzaTimestamp = new Timestamp(pizzaDate.getTime());
        return insertPizzaWithDetails(pizzaTimestamp, orderIdentifier, pizzaData);
    }
    
    private static int insertPizzaWithDetails(Timestamp pizzaTimestamp, int orderIdentifier, Pizza pizzaData) 
            throws SQLException, IOException {
        int generatedPizzaId = insertPizzaRecord(pizzaTimestamp, orderIdentifier, pizzaData);
        pizzaData.setPizzaID(generatedPizzaId);
        
        insertPizzaDiscountRecords(generatedPizzaId, pizzaData.getDiscounts());
        insertPizzaToppingRecords(generatedPizzaId, pizzaData);
        
        return generatedPizzaId;
    }
    
    private static int insertPizzaRecord(Timestamp pizzaTimestamp, int orderIdentifier, Pizza pizzaData) throws SQLException {
        String pizzaInsertQuery = "INSERT INTO pizza (pizza_Size, pizza_CrustType, " +
                    "ordertable_OrderID, pizza_PizzaState, pizza_PizzaDate, " +
                    "pizza_CustPrice, pizza_BusPrice) VALUES (?,?,?,?,?,?,?)";
        
        try (PreparedStatement pizzaStatement = databaseConnection.prepareStatement(pizzaInsertQuery, Statement.RETURN_GENERATED_KEYS)) {
            pizzaStatement.setString(1, pizzaData.getSize());
            pizzaStatement.setString(2, pizzaData.getCrustType());
            pizzaStatement.setInt(3, orderIdentifier);
            pizzaStatement.setString(4, pizzaData.getPizzaState());
            pizzaStatement.setTimestamp(5, pizzaTimestamp);
            pizzaStatement.setDouble(6, pizzaData.getCustPrice());
            pizzaStatement.setDouble(7, pizzaData.getBusPrice());
            pizzaStatement.executeUpdate();
            
            ResultSet generatedPizzaKeys = pizzaStatement.getGeneratedKeys();
            if (generatedPizzaKeys.next()) {
                return generatedPizzaKeys.getInt(1);
            }
            throw new SQLException("Failed to retrieve generated pizza identifier");
        }
    }
    
    private static void insertPizzaDiscountRecords(int pizzaIdentifier, ArrayList<Discount> pizzaDiscounts) throws SQLException {
        if (pizzaDiscounts == null || pizzaDiscounts.isEmpty()) {
            return;
        }
        
        String pizzaDiscountQuery = "INSERT INTO pizza_discount (pizza_PizzaID, discount_DiscountID) VALUES (?,?)";
        
        for (Discount currentDiscount : pizzaDiscounts) {
            try (PreparedStatement discountStatement = databaseConnection.prepareStatement(pizzaDiscountQuery)) {
                discountStatement.setInt(1, pizzaIdentifier);
                discountStatement.setInt(2, currentDiscount.getDiscountID());
                discountStatement.executeUpdate();
            }
        }
    }
    
    private static void insertPizzaToppingRecords(int pizzaIdentifier, Pizza pizzaData) throws SQLException, IOException {
        if (pizzaData.getToppings() == null || pizzaData.getToppings().isEmpty()) {
            return;
        }
        
        reconcileToppingDoubleFlags(pizzaData);
        
        String toppingInsertQuery = "INSERT INTO pizza_topping (pizza_PizzaID, topping_TopID, pizza_topping_IsDouble) " +
                    "VALUES (?,?,?)";
        
        for (Topping currentTopping : pizzaData.getToppings()) {
            try (PreparedStatement toppingStatement = databaseConnection.prepareStatement(toppingInsertQuery)) {
                toppingStatement.setInt(1, pizzaIdentifier);
                toppingStatement.setInt(2, currentTopping.getTopID());
                toppingStatement.setInt(3, currentTopping.getDoubled() ? 1 : 0);
                toppingStatement.executeUpdate();
            }
            
            updateToppingInventory(currentTopping, pizzaData.getSize(), currentTopping.getDoubled());
        }
    }
    
    // =========================================================
    // PRIVATE HELPER METHODS: COMPLETE ORDER OPERATIONS
    // =========================================================
    
    private static void markOrderAsComplete(int orderIdentifier, DatabaseSession databaseSession) throws SQLException {
        String completeOrderQuery = "UPDATE ordertable SET ordertable_IsComplete=1 WHERE ordertable_OrderID=?";
        
        try (PreparedStatement completeStatement = databaseSession.prepareStatement(completeOrderQuery)) {
            completeStatement.setInt(1, orderIdentifier);
            completeStatement.executeUpdate();
        }
    }
    
    private static void markPizzasAsComplete(int orderIdentifier, DatabaseSession databaseSession) throws SQLException {
        String completePizzaQuery = "UPDATE pizza SET pizza_PizzaState='completed' WHERE ordertable_OrderID=?";
        
        try (PreparedStatement pizzaCompleteStatement = databaseSession.prepareStatement(completePizzaQuery)) {
            pizzaCompleteStatement.setInt(1, orderIdentifier);
            pizzaCompleteStatement.executeUpdate();
        }
    }
    
    private static void markDeliveryAsDelivered(int orderIdentifier, DatabaseSession databaseSession) throws SQLException {
        String deliverOrderQuery = "UPDATE delivery SET delivery_IsDelivered=1 WHERE ordertable_OrderID=?";
        
        try (PreparedStatement deliverStatement = databaseSession.prepareStatement(deliverOrderQuery)) {
            deliverStatement.setInt(1, orderIdentifier);
            deliverStatement.executeUpdate();
        }
    }
    
    private static void markPickupAsPickedUp(int orderIdentifier, DatabaseSession databaseSession) throws SQLException {
        String pickupOrderQuery = "UPDATE pickup SET pickup_IsPickedUp=1 WHERE ordertable_OrderID=?";
        
        try (PreparedStatement pickupStatement = databaseSession.prepareStatement(pickupOrderQuery)) {
            pickupStatement.setInt(1, orderIdentifier);
            pickupStatement.executeUpdate();
        }
    }
    
    // =========================================================
    // PRIVATE HELPER METHODS: QUERY BUILDING
    // =========================================================
    
    private static String buildOrderQuery(int orderStatusFilter) {
        StringBuilder queryBuilder = new StringBuilder("SELECT * FROM ordertable");
        
        switch (orderStatusFilter) {
            case 1:
                queryBuilder.append(" WHERE ordertable_IsComplete=0");
                break;
            case 2:
                queryBuilder.append(" WHERE ordertable_IsComplete=1");
                break;
        }
        
        queryBuilder.append(" ORDER BY ordertable_OrderID ASC");
        return queryBuilder.toString();
    }
    
    // =========================================================
    // PRIVATE HELPER METHODS: OBJECT BUILDING
    // =========================================================
    
    private static Order buildOrderFromResultSet(ResultSet orderRow, Connection databaseConnection) throws SQLException {
        int orderIdentifier = orderRow.getInt("ordertable_OrderID");
        int customerIdentifier = orderRow.getInt("customer_CustID");
        if (orderRow.wasNull()) {
            customerIdentifier = -1;
        }
        String orderTypeString = orderRow.getString("ordertable_OrderType");
        String orderDateTimeString = orderRow.getString("ordertable_OrderDateTime");
        double customerPrice = orderRow.getDouble("ordertable_CustPrice");
        double businessPrice = orderRow.getDouble("ordertable_BusPrice");
        boolean isOrderComplete = orderRow.getBoolean("ordertable_IsComplete");
        
        Order constructedOrder;
        
        if (DINE_IN.equals(orderTypeString)) {
            constructedOrder = buildDineinOrder(orderIdentifier, customerIdentifier, orderDateTimeString, 
                                               customerPrice, businessPrice, isOrderComplete, databaseConnection);
        } else if (PICKUP.equals(orderTypeString)) {
            constructedOrder = buildPickupOrder(orderIdentifier, customerIdentifier, orderDateTimeString, 
                                               customerPrice, businessPrice, isOrderComplete, databaseConnection);
        } else {
            constructedOrder = buildDeliveryOrder(orderIdentifier, customerIdentifier, orderDateTimeString, 
                                                 customerPrice, businessPrice, isOrderComplete, databaseConnection);
        }
        
        constructedOrder.setPizzaList(fetchPizzasForOrder(orderIdentifier, databaseConnection));
        constructedOrder.setDiscountList(fetchOrderDiscounts(orderIdentifier, databaseConnection));
        
        return constructedOrder;
    }
    
    private static DineinOrder buildDineinOrder(int orderIdentifier, int customerIdentifier, String orderDateTime, 
                                               double customerPrice, double businessPrice, 
                                               boolean isOrderComplete, Connection databaseConnection) throws SQLException {
        String tableQuery = "SELECT dinein_TableNum FROM dinein WHERE ordertable_OrderID=?";
        
        try (PreparedStatement tableStatement = databaseConnection.prepareStatement(tableQuery)) {
            tableStatement.setInt(1, orderIdentifier);
            ResultSet tableResultSet = tableStatement.executeQuery();
            
            int tableNumber = 0;
            if (tableResultSet.next()) {
                tableNumber = tableResultSet.getInt(1);
            }
            
            return new DineinOrder(orderIdentifier, customerIdentifier, orderDateTime, customerPrice, 
                                  businessPrice, isOrderComplete, tableNumber);
        }
    }
    
    private static PickupOrder buildPickupOrder(int orderIdentifier, int customerIdentifier, String orderDateTime, 
                                               double customerPrice, double businessPrice, 
                                               boolean isOrderComplete, Connection databaseConnection) throws SQLException {
        String pickupQuery = "SELECT pickup_IsPickedUp FROM pickup WHERE ordertable_OrderID=?";
        
        try (PreparedStatement pickupStatement = databaseConnection.prepareStatement(pickupQuery)) {
            pickupStatement.setInt(1, orderIdentifier);
            ResultSet pickupResultSet = pickupStatement.executeQuery();
            
            boolean isPickedUp = false;
            if (pickupResultSet.next()) {
                isPickedUp = pickupResultSet.getBoolean(1);
            }
            
            return new PickupOrder(orderIdentifier, customerIdentifier, orderDateTime, customerPrice, 
                                  businessPrice, isPickedUp, isOrderComplete);
        }
    }
    
    private static DeliveryOrder buildDeliveryOrder(int orderIdentifier, int customerIdentifier, String orderDateTime, 
                                                   double customerPrice, double businessPrice, 
                                                   boolean isOrderComplete, Connection databaseConnection) throws SQLException {
        String deliveryQuery = "SELECT delivery_HouseNum, delivery_Street, delivery_City, " +
                    "delivery_State, delivery_Zip, delivery_IsDelivered " +
                    "FROM delivery WHERE ordertable_OrderID=?";
        
        try (PreparedStatement deliveryStatement = databaseConnection.prepareStatement(deliveryQuery)) {
            deliveryStatement.setInt(1, orderIdentifier);
            ResultSet deliveryResultSet = deliveryStatement.executeQuery();
            
            String deliveryAddress = "";
            boolean isDelivered = false;
            
            if (deliveryResultSet.next()) {
                deliveryAddress = String.format("%d\t%s\t%s\t%s\t%d",
                    deliveryResultSet.getInt(1),
                    deliveryResultSet.getString(2),
                    deliveryResultSet.getString(3),
                    deliveryResultSet.getString(4),
                    deliveryResultSet.getInt(5));
                isDelivered = deliveryResultSet.getBoolean(6);
            }
            
            return new DeliveryOrder(orderIdentifier, customerIdentifier, orderDateTime, customerPrice, 
                                    businessPrice, isOrderComplete, isDelivered, deliveryAddress);
        }
    }
    
    private static ArrayList<Pizza> fetchPizzasForOrder(int orderIdentifier, Connection databaseConnection) throws SQLException {
        String pizzaQuery = "SELECT * FROM pizza WHERE ordertable_OrderID=? ORDER BY pizza_PizzaID";
        
        try (PreparedStatement pizzaStatement = databaseConnection.prepareStatement(pizzaQuery)) {
            pizzaStatement.setInt(1, orderIdentifier);
            ResultSet pizzaResultSet = pizzaStatement.executeQuery();
            
            ArrayList<Pizza> pizzaCollection = new ArrayList<>();
            while (pizzaResultSet.next()) {
                Pizza currentPizza = createPizzaFromResultSet(pizzaResultSet);
                currentPizza.setToppings(fetchPizzaToppings(currentPizza.getPizzaID(), databaseConnection));
                currentPizza.setDiscounts(fetchPizzaDiscounts(currentPizza.getPizzaID(), databaseConnection));
                pizzaCollection.add(currentPizza);
            }
            return pizzaCollection;
        }
    }
    
    private static Pizza createPizzaFromResultSet(ResultSet pizzaRow) throws SQLException {
        return new Pizza(
            pizzaRow.getInt("pizza_PizzaID"),
            pizzaRow.getString("pizza_Size"),
            pizzaRow.getString("pizza_CrustType"),
            pizzaRow.getInt("ordertable_OrderID"),
            pizzaRow.getString("pizza_PizzaState"),
            pizzaRow.getString("pizza_PizzaDate"),
            pizzaRow.getDouble("pizza_CustPrice"),
            pizzaRow.getDouble("pizza_BusPrice")
        );
    }
    
    private static ArrayList<Topping> fetchPizzaToppings(int pizzaIdentifier, Connection databaseConnection) throws SQLException {
        String toppingQuery = "SELECT t.*, pt.pizza_topping_IsDouble " +
                    "FROM topping t JOIN pizza_topping pt ON t.topping_TopID = pt.topping_TopID " +
                    "WHERE pt.pizza_PizzaID=? ORDER BY t.topping_TopName";
        
        try (PreparedStatement toppingStatement = databaseConnection.prepareStatement(toppingQuery)) {
            toppingStatement.setInt(1, pizzaIdentifier);
            ResultSet toppingResultSet = toppingStatement.executeQuery();
            
            ArrayList<Topping> toppingCollection = new ArrayList<>();
            while (toppingResultSet.next()) {
                Topping currentTopping = createToppingFromResultSet(toppingResultSet);
                currentTopping.setDoubled(toppingResultSet.getInt("pizza_topping_IsDouble") == 1);
                toppingCollection.add(currentTopping);
            }
            return toppingCollection;
        }
    }
    
    private static ArrayList<Discount> fetchOrderDiscounts(int orderIdentifier, Connection databaseConnection) throws SQLException {
        String discountQuery = "SELECT d.* FROM discount d " +
                    "JOIN order_discount od ON d.discount_DiscountID = od.discount_DiscountID " +
                    "WHERE od.ordertable_OrderID=? ORDER BY d.discount_DiscountID";
        
        try (PreparedStatement discountStatement = databaseConnection.prepareStatement(discountQuery)) {
            discountStatement.setInt(1, orderIdentifier);
            ResultSet discountResultSet = discountStatement.executeQuery();
            
            ArrayList<Discount> discountCollection = new ArrayList<>();
            while (discountResultSet.next()) {
                discountCollection.add(createDiscountFromResultSet(discountResultSet));
            }
            return discountCollection;
        }
    }
    
    private static ArrayList<Discount> fetchPizzaDiscounts(int pizzaIdentifier, Connection databaseConnection) throws SQLException {
        String pizzaDiscountQuery = "SELECT d.* FROM discount d " +
                    "JOIN pizza_discount pd ON d.discount_DiscountID = pd.discount_DiscountID " +
                    "WHERE pd.pizza_PizzaID=? ORDER BY d.discount_DiscountID";
        
        try (PreparedStatement discountStatement = databaseConnection.prepareStatement(pizzaDiscountQuery)) {
            discountStatement.setInt(1, pizzaIdentifier);
            ResultSet discountResultSet = discountStatement.executeQuery();
            
            ArrayList<Discount> discountCollection = new ArrayList<>();
            while (discountResultSet.next()) {
                discountCollection.add(createDiscountFromResultSet(discountResultSet));
            }
            return discountCollection;
        }
    }
    
    private static Customer createCustomerFromResultSet(ResultSet customerRow) throws SQLException {
        return new Customer(
            customerRow.getInt("customer_CustID"),
            customerRow.getString("customer_FName"),
            customerRow.getString("customer_LName"),
            customerRow.getString("customer_PhoneNum")
        );
    }
    
    private static Discount createDiscountFromResultSet(ResultSet discountRow) throws SQLException {
        return new Discount(
            discountRow.getInt("discount_DiscountID"),
            discountRow.getString("discount_DiscountName"),
            discountRow.getDouble("discount_Amount"),
            discountRow.getBoolean("discount_IsPercent")
        );
    }
    
    private static Topping createToppingFromResultSet(ResultSet toppingRow) throws SQLException {
        return new Topping(
            toppingRow.getInt("topping_TopID"),
            toppingRow.getString("topping_TopName"),
            toppingRow.getDouble("topping_SmallAMT"),
            toppingRow.getDouble("topping_MedAMT"),
            toppingRow.getDouble("topping_LgAMT"),
            toppingRow.getDouble("topping_XLAMT"),
            toppingRow.getDouble("topping_CustPrice"),
            toppingRow.getDouble("topping_BusPrice"),
            toppingRow.getInt("topping_MinINVT"),
            toppingRow.getInt("topping_CurINVT")
        );
    }
    
    // =========================================================
    // PRIVATE HELPER METHODS: TOPPING LOGIC
    // =========================================================
    
    private static void reconcileToppingDoubleFlags(Pizza pizzaItem) throws SQLException, IOException {
        if (pizzaItem.getToppings() == null || pizzaItem.getToppings().isEmpty()) {
            return;
        }
        
        double baseCustomerPrice = getBaseCustPrice(pizzaItem.getSize(), pizzaItem.getCrustType());
        double baseBusinessPrice = getBaseBusPrice(pizzaItem.getSize(), pizzaItem.getCrustType());
        
        double expectedCustomerPrice = baseCustomerPrice;
        double expectedBusinessPrice = baseBusinessPrice;
        
        for (Topping currentTopping : pizzaItem.getToppings()) {
            double requiredUnits = calculateToppingUnitsForSize(currentTopping, pizzaItem.getSize());
            expectedCustomerPrice += requiredUnits * currentTopping.getCustPrice();
            expectedBusinessPrice += requiredUnits * currentTopping.getBusPrice();
        }
        
        double discountedCustomerPrice = applyPizzaDiscountsToPrice(expectedCustomerPrice, pizzaItem.getDiscounts());
        double customerPriceDifference = pizzaItem.getCustPrice() - discountedCustomerPrice;
        double businessPriceDifference = pizzaItem.getBusPrice() - expectedBusinessPrice;
        
        for (Topping currentTopping : pizzaItem.getToppings()) {
            if (currentTopping.getDoubled()) {
                continue;
            }
            
            double requiredUnits = calculateToppingUnitsForSize(currentTopping, pizzaItem.getSize());
            double additionalCustomerPrice = requiredUnits * currentTopping.getCustPrice();
            double additionalBusinessPrice = requiredUnits * currentTopping.getBusPrice();
            
            if (customerPriceDifference >= additionalCustomerPrice - 0.001 && 
                businessPriceDifference >= additionalBusinessPrice - 0.001) {
                currentTopping.setDoubled(true);
                customerPriceDifference -= additionalCustomerPrice;
                businessPriceDifference -= additionalBusinessPrice;
            }
        }
    }
    
    private static double applyPizzaDiscountsToPrice(double originalPrice, ArrayList<Discount> discountList) {
        if (discountList == null || discountList.isEmpty()) {
            return originalPrice;
        }
        
        double adjustedPrice = originalPrice;
        
        for (Discount currentDiscount : discountList) {
            if (currentDiscount.isPercent()) {
                adjustedPrice *= (1 - currentDiscount.getAmount() / 100.0);
            } else {
                adjustedPrice -= currentDiscount.getAmount();
            }
        }
        
        return adjustedPrice;
    }
    
    private static void updateToppingInventory(Topping toppingItem, String pizzaSize, boolean isDoubleTopping) 
            throws SQLException, IOException {
        double requiredUnits = calculateToppingUnitsForSize(toppingItem, pizzaSize);
        
        if (isDoubleTopping) {
            requiredUnits *= 2;
        }
        
        double unitsToConsume = Math.ceil(requiredUnits);
        
        verifyToppingInventoryAvailability(toppingItem.getTopID(), unitsToConsume);
        
        String inventoryUpdateQuery = "UPDATE topping SET topping_CurINVT = topping_CurINVT - ? WHERE topping_TopID=?";
        
        try (PreparedStatement inventoryStatement = databaseConnection.prepareStatement(inventoryUpdateQuery)) {
            inventoryStatement.setDouble(1, unitsToConsume);
            inventoryStatement.setInt(2, toppingItem.getTopID());
            inventoryStatement.executeUpdate();
        }
    }
    
    private static void verifyToppingInventoryAvailability(int toppingIdentifier, double requiredUnits) throws SQLException {
        String inventoryCheckQuery = "SELECT topping_CurINVT FROM topping WHERE topping_TopID=?";
        
        try (PreparedStatement checkStatement = databaseConnection.prepareStatement(inventoryCheckQuery)) {
            checkStatement.setInt(1, toppingIdentifier);
            ResultSet inventoryResultSet = checkStatement.executeQuery();
            
            if (inventoryResultSet.next()) {
                double currentInventoryLevel = inventoryResultSet.getDouble(1);
                if (currentInventoryLevel < requiredUnits) {
                    throw new SQLException("Insufficient inventory for topping with identifier: " + toppingIdentifier);
                }
            }
        }
    }
    
    private static double calculateToppingUnitsForSize(Topping toppingItem, String pizzaSize) {
        switch (pizzaSize) {
            case SIZE_SMALL:
                return toppingItem.getSmallAMT();
            case SIZE_MEDIUM:
                return toppingItem.getMedAMT();
            case SIZE_LARGE:
                return toppingItem.getLgAMT();
            case SIZE_XLARGE:
                return toppingItem.getXLAMT();
            default:
                return toppingItem.getSmallAMT();
        }
    }
    
    // =========================================================
    // PRIVATE HELPER METHODS: UTILITIES
    // =========================================================
    
    private static Timestamp parseTimestampString(String dateTimeString) {
        try {
            return Timestamp.valueOf(dateTimeString);
        } catch (IllegalArgumentException exception) {
            return new Timestamp(System.currentTimeMillis());
        }
    }
    
    private static String[] parseAddressString(String addressString) {
        String[] addressSegments = addressString.split("\\t");
        
        if (addressSegments.length < 5) {
            String[] fallbackSegments = addressString.split("\\s+");
            if (fallbackSegments.length >= 5) {
                return new String[]{
                    fallbackSegments[0],
                    fallbackSegments[1],
                    fallbackSegments[2],
                    fallbackSegments[3],
                    fallbackSegments[4]
                };
            }
        }
        
        if (addressSegments.length < 5) {
            return new String[]{"0", "", "", "", "0"};
        }
        
        return addressSegments;
    }
    
    // =========================================================
    // INNER CLASS: DATABASE SESSION
    // =========================================================
    
    private static class DatabaseSession implements AutoCloseable {
        private final Connection sessionConnection;
        private final boolean shouldCloseConnection;
        private boolean isTransactionActive;
        
        public DatabaseSession(Connection connection, boolean closeConnectionFlag) {
            this.sessionConnection = connection;
            this.shouldCloseConnection = closeConnectionFlag;
            this.isTransactionActive = false;
        }
        
        public Connection getConnection() {
            return sessionConnection;
        }
        
        public void beginTransaction() throws SQLException {
            sessionConnection.setAutoCommit(false);
            isTransactionActive = true;
        }
        
        public void commit() throws SQLException {
            if (isTransactionActive) {
                sessionConnection.commit();
                sessionConnection.setAutoCommit(true);
                isTransactionActive = false;
            }
        }
        
        public void rollback() throws SQLException {
            if (isTransactionActive) {
                sessionConnection.rollback();
                sessionConnection.setAutoCommit(true);
                isTransactionActive = false;
            }
        }
        
        public PreparedStatement prepareStatement(String sqlStatement) throws SQLException {
            return sessionConnection.prepareStatement(sqlStatement);
        }
        
        public PreparedStatement prepareStatement(String sqlStatement, int autoGeneratedKeys) throws SQLException {
            return sessionConnection.prepareStatement(sqlStatement, autoGeneratedKeys);
        }
        
        @Override
        public void close() throws SQLException {
            try {
                if (isTransactionActive) {
                    rollback();
                }
            } finally {
                if (shouldCloseConnection && sessionConnection != null && !sessionConnection.isClosed()) {
                    closeConnection();
                }
            }
        }
    }
}