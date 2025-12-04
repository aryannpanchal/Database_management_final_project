package cpsc4620;

import java.util.ArrayList;

public class Topping {

    private int TopID;
    private String TopName;

    // Amount of this topping used per pizza size
    private double SmallAMT;
    private double MedAMT;
    private double LgAMT;
    private double XLAMT;

    // Price per unit of topping
    private double CustPrice;
    private double BusPrice;

    // Inventory levels
    private int MinINVT;
    private int CurINVT;

    // Whether this topping is doubled on a specific pizza
    private boolean isDoubled;

    public Topping(int topID, String topName,
                   double smallAMT, double medAMT, double lgAMT, double xLAMT,
                   double custPrice, double busPrice,
                   int minINVT, int curINVT) {
        this.TopID = topID;
        this.TopName = topName;
        this.SmallAMT = smallAMT;
        this.MedAMT = medAMT;
        this.LgAMT = lgAMT;
        this.XLAMT = xLAMT;
        this.CustPrice = custPrice;
        this.BusPrice = busPrice;
        this.MinINVT = minINVT;
        this.CurINVT = curINVT;
        this.isDoubled = false;
    }

    // Optional empty constructor (not required by DBNinja, but harmless)
    public Topping() {
    }

    public int getTopID() {
        return TopID;
    }

    public String getTopName() {
        return TopName;
    }

    public double getSmallAMT() {
        return SmallAMT;
    }

    public double getMedAMT() {
        return MedAMT;
    }

    public double getLgAMT() {
        return LgAMT;
    }

    public double getXLAMT() {
        return XLAMT;
    }

    public double getCustPrice() {
        return CustPrice;
    }

    public double getBusPrice() {
        return BusPrice;
    }

    public int getMinINVT() {
        return MinINVT;
    }

    public int getCurINVT() {
        return CurINVT;
    }

    public boolean getDoubled() {
        return isDoubled;
    }

    public void setTopID(int topID) {
        TopID = topID;
    }

    public void setTopName(String topName) {
        TopName = topName;
    }

    public void setSmallAMT(double smallAMT) {
        SmallAMT = smallAMT;
    }

    public void setMedAMT(double medAMT) {
        MedAMT = medAMT;
    }

    public void setLgAMT(double lgAMT) {
        LgAMT = lgAMT;
    }

    public void setXLAMT(double xLAMT) {
        XLAMT = xLAMT;
    }

    public void setCustPrice(double custPrice) {
        CustPrice = custPrice;
    }

    public void setBusPrice(double busPrice) {
        BusPrice = busPrice;
    }

    public void setMinINVT(int minINVT) {
        MinINVT = minINVT;
    }

    public void setCurINVT(int curINVT) {
        CurINVT = curINVT;
    }

    public void setDoubled(boolean doubled) {
        isDoubled = doubled;
    }

    /**
     * Print toppings in the style Menu.printOrderDetails expects.
     */
    public static void printToppings(ArrayList<Topping> myToppings) {
        if (myToppings.isEmpty()) {
            System.out.println("NO TOPPINGS");
        } else {
            for (Topping t : myToppings) {
                System.out.println(t.pizzaTopping());
            }
            System.out.println();
        }
    }

    @Override
    public String toString() {
        return "Topping [TopID=" + TopID +
                ", TopName=" + TopName +
                ", SmallAMT=" + SmallAMT +
                ", MedAMT=" + MedAMT +
                ", LgAMT=" + LgAMT +
                ", XLAMT=" + XLAMT +
                ", CustPrice=" + CustPrice +
                ", BusPrice=" + BusPrice +
                ", MinINVT=" + MinINVT +
                ", CurINVT=" + CurINVT + "]";
    }

    /**
     * Compact string used when printing pizza details.
     */
    public String pizzaTopping() {
        return "Topping: " + TopName +
                ", Doubled?: " + (isDoubled ? "Yes" : "No");
    }
}
