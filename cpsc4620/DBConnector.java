package cpsc4620;

import java.sql.*;
import java.io.IOException;

public class DBConnector {

    public static Connection getConnection() throws SQLException, IOException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            String url = "jdbc:mysql://localhost:3306/pizzadb";
            String user = "root";
            String password = "Aryan.Panchal@007";
            return DriverManager.getConnection(url, user, password);
        } catch (ClassNotFoundException e) {
            throw new SQLException("MySQL JDBC Driver not found", e);
        }
    }
}

