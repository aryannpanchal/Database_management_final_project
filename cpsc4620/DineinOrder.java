package cpsc4620;

public class DineinOrder extends Order {

    private int TableNum;

    /**
     * Used by Menu when creating a new dine-in order and by DBNinja when
     * reconstructing orders from the database.
     *
     * @param orderID    the order ID (or -1 before DB insert)
     * @param custID     the customer ID (for dine-in this is usually -1 / in-store)
     * @param date       order date/time as a String
     * @param custPrice  total customer price for the order
     * @param busPrice   total business cost for the order
     * @param isComplete whether the order is marked complete
     * @param tableNum   table number for the dine-in order
     */
    public DineinOrder(int orderID, int custID, String date,
                       double custPrice, double busPrice,
                       boolean isComplete, int tableNum) {
        // OrderType must be DBNinja.dine_in ("dinein" in the DB)
        super(orderID, custID, DBNinja.dinein, date, custPrice, busPrice, isComplete);
        this.TableNum = tableNum;
    }

    public int getTableNum() {
        return TableNum;
    }

    public void setTableNum(int tableNum) {
        this.TableNum = tableNum;
    }

    @Override
    public String toString() {
        return super.toString() + " | Table number: " + TableNum;
    }
}
