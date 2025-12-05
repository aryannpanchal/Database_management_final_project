package cpsc4620;

public class DineinOrder extends Order {
    private int TableNum;

    public DineinOrder(int orderID, int custID, String date, double custPrice,
            double busPrice, boolean isComplete, int tableNum) {
        super(orderID, custID, DBNinja.dine_in, date, custPrice, busPrice, isComplete);
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
        return super.toString() + " Table number " + TableNum;
    }
}