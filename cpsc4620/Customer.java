package cpsc4620;

public class Customer {
    private int CustID;
    private String FName;
    private String LName;
    private String Phone;
    private String Address;   // optional, not stored in DB for this project

    public Customer(int custID, String fName, String lName, String phone) {
        this.CustID = custID;
        this.FName = fName;
        this.LName = lName;
        this.Phone = phone;
        this.Address = "";
    }

    public int getCustID() {
        return CustID;
    }

    public String getFName() {
        return FName;
    }

    public String getLName() {
        return LName;
    }

    public String getPhone() {
        return Phone;
    }

    public String getAddress() {
        return Address;
    }

    public void setCustID(int custID) {
        CustID = custID;
    }

    public void setFName(String fName) {
        FName = fName;
    }

    public void setLName(String lName) {
        LName = lName;
    }

    public void setPhone(String phone) {
        Phone = phone;
    }

    /**
     * Optional helper if you ever want to stash a formatted address in this object.
     * Not used by Menu/DBNinja for this project; delivery addresses live in the delivery table.
     */
    public void setAddress(String street, String city, String state, String zip) {
        // was "/n" before, which is just a slash and an n, not a newline
        Address = street + "\n" + city + "\n" + state + "\n" + zip;
    }

    @Override
    public String toString() {
        return "CustID=" + CustID + " | Name= " + FName + " " + LName + ", Phone= " + Phone;
    }
}
