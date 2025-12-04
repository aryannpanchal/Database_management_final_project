package cpsc4620;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnector {

    // *** KEEP THESE MATCHED TO WHATEVER YOUR COURSE EXPECTS ***
    private static final String URL      = "jdbc:mysql://localhost:3306/PizzaDB?useSSL=false&serverTimezone=UTC";
    private static final String USER     = "root";         // or whatever your instructor told you
    private static final String PASSWORD = "Aryan.Panchal@007";         // or whatever your instructor told you

    public static Connection make_connection() {
        try {
            // Try MySQL 8+ driver first
            try {
                Class.forName("com.mysql.cj.jdbc.Driver");
            } catch (ClassNotFoundException e) {
                // Fallback to older 5.x driver name
                Class.forName("com.mysql.jdbc.Driver");
            }

            // If we got here, one of the driver classes loaded successfully
            return DriverManager.getConnection(URL, USER, PASSWORD);

        } catch (ClassNotFoundException e) {
            System.out.println("Could not load the driver");
            System.out.println("Message : " + e.getMessage());
        } catch (SQLException e) {
            System.out.println("Could not connect to the database");
            System.out.println("Message : " + e.getMessage());
        }

        return null;
    }
}
