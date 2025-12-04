package cpsc4620;

public class PickupOrder extends Order {

    private boolean isPickedUp;

    /**
     * Used by Menu when creating a new pickup order and by DBNinja when
     * reconstructing orders from the database.
     *
     * @param orderID    the order ID (or -1 before DB insert)
     * @param custID     the customer ID
     * @param date       order date/time as a String
     * @param custPrice  total customer price for the order
     * @param busPrice   total business cost for the order
     * @param isPickedUp whether the order has been picked up
     * @param isComplete whether the order is marked complete
     */
    public PickupOrder(int orderID, int custID, String date,
                       double custPrice, double busPrice,
                       boolean isPickedUp, boolean isComplete) {
        // OrderType must be DBNinja.pickup ("pickup" in the DB)
        super(orderID, custID, DBNinja.pickup, date, custPrice, busPrice, isComplete);
        this.isPickedUp = isPickedUp;
    }

    public boolean getIsPickedUp() {
        return isPickedUp;
    }

    public void setIsPickedUp(boolean isPickedUp) {
        this.isPickedUp = isPickedUp;
    }

    @Override
    public String toString() {
        return super.toString() + " | Picked Up: " + (isPickedUp ? "Yes" : "No");
    }
}
