package cpsc4620;

public class DeliveryOrder extends Order {

    private String Address;
    private boolean isDelivered;

    /**
     * Used by Menu when creating a NEW delivery order.
     * It always starts as not delivered and not complete.
     *
     * @param orderID    the order ID (or -1 before DB insert)
     * @param custID     the customer ID
     * @param date       order date/time as a String
     * @param custPrice  total customer price for the order
     * @param busPrice   total business cost for the order
     * @param isComplete whether the order is marked complete
     * @param address    formatted address string
     */
    public DeliveryOrder(int orderID, int custID, String date,
                         double custPrice, double busPrice,
                         boolean isComplete, String address) {
        // OrderType must be DBNinja.delivery ("delivery" in the DB)
        super(orderID, custID, DBNinja.delivery, date, custPrice, busPrice, isComplete);
        this.Address = address;
        this.isDelivered = false;
    }

    /**
     * Used by DBNinja when reconstructing an existing delivery order
     * from the database, including the delivered flag.
     *
     * @param orderID     the order ID
     * @param custID      the customer ID
     * @param date        order date/time as a String
     * @param custPrice   total customer price for the order
     * @param busPrice    total business cost for the order
     * @param isComplete  whether the order is marked complete
     * @param isDelivered whether the order has been delivered
     * @param address     formatted address string
     */
    public DeliveryOrder(int orderID, int custID, String date,
                         double custPrice, double busPrice,
                         boolean isComplete, boolean isDelivered,
                         String address) {
        super(orderID, custID, DBNinja.delivery, date, custPrice, busPrice, isComplete);
        this.Address = address;
        this.isDelivered = isDelivered;
    }

    public String getAddress() {
        return Address;
    }

    public void setAddress(String address) {
        this.Address = address;
    }

    public boolean getIsDelivered() {
        return isDelivered;
    }

    public void setIsDelivered(boolean delivered) {
        this.isDelivered = delivered;
    }

    @Override
    public String toString() {
        return super.toString()
                + " | Delivered to: " + Address
                + " | Order Delivered: " + (isDelivered ? "Yes" : "No");
    }
}
