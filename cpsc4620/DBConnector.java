package cpsc4620;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnector {

    protected static String user = "root";
    protected static String password = "Aryan.Panchal@007";  // your password
    protected static String database_name = "PizzaDB";

    // NOTE: include the database name in the URL
    private static String url =
            "jdbc:mysql://localhost:3306/" + database_name + "?serverTimezone=UTC&useSSL=false";

    private static Connection conn = null;

    public static Connection make_connection() throws SQLException, IOException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.out.println("Could not load the driver");
            System.out.println("Message : " + e.getMessage());
            return null;
        }

        try {
            conn = DriverManager.getConnection(url, user, password);
            return conn;
        } catch (SQLException e) {
            System.out.println("Could not connect to database");
            System.out.println("Message : " + e.getMessage());
            return null;
        }
    }

    public static void close_connection() throws SQLException {
        if (conn != null && !conn.isClosed()) {
            conn.close();
            conn = null;
        }
    }

    public static Connection getConnection() {
        return conn;
    }
}
