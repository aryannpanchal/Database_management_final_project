package cpsc4620;

import java.util.ArrayList;

public class Topping {

    private int TopID;
    private String TopName;
    private double SmallAMT;
    private double MedAMT;
    private double LgAMT;
    private double XLAMT;
    private double CustPrice;
    private double BusPrice;
    private int MinINVT;
    private int CurINVT;

    // Not stored in DB – used only when this topping is attached to a Pizza
    private boolean doubled;

    public Topping(int topID,
                   String topName,
                   double smallAMT,
                   double medAMT,
                   double lgAMT,
                   double xLAMT,
                   double custPrice,
                   double busPrice,
                   int minINVT,
                   int curINVT) {
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
        this.doubled = false;
    }

    // --------- Getters ---------
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
        return doubled;
    }

    // --------- Setters ---------
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
        this.doubled = doubled;
    }

    // --------- Printing used by Menu.printOrderDetails ---------
    public static void printToppings(ArrayList<Topping> myToppings) {
        if (myToppings == null || myToppings.size() == 0) {
            System.out.println("TOPPINGS: NONE");
            return;
        }

        System.out.print("TOPPINGS: ");
        for (Topping t : myToppings) {
            String extra = t.getDoubled() ? "Yes" : "No";
            System.out.print("Topping: " + t.getTopName() + ", Doubled?: " + extra + " ");
        }
        System.out.println();
    }

    @Override
    public String toString() {
        // Match the format the autograder expects
        return "Topping [TopID=" + TopID +
                ", TopName=" + TopName +
                ", smallAMT=" + SmallAMT +
                ", MedAMT=" + MedAMT +
                ", LgAMT=" + LgAMT +
                ", XLAMT=" + XLAMT +
                ", CustPrice=" + CustPrice +
                ", BusPrice=" + BusPrice +
                ", MinINVT=" + MinINVT +
                ", CurINVT=" + CurINVT +
                "]";
    }
}
