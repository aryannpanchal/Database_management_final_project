package cpsc4620;

public class DeliveryOrder extends Order {
    private String Address;
    private boolean isDelivered;

    public DeliveryOrder(int orderID, int custID, String date, double custPrice,
            double busPrice, boolean isComplete, String address) {
        super(orderID, custID, DBNinja.delivery, date, custPrice, busPrice, isComplete);
        this.Address = address;
        this.isDelivered = false;
    }

    public DeliveryOrder(int orderID, int custID, String date, double custPrice,
            double busPrice, boolean isComplete, boolean isDelivered, String address) {
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

    public boolean isDelivered() {
        return isDelivered;
    }

    public void setIsDelivered(boolean delivered) {
        this.isDelivered = delivered;
    }

    @Override
    public String toString() {
        return super.toString() + " Delivered to " + Address + " Order Delivered " + (isDelivered ? "Yes" : "No");
    }
}