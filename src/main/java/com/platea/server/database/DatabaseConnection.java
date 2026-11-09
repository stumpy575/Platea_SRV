package com.platea.server.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseConnection {

    // Método para devolver la conexión
    public static Connection getConnection() throws SQLException {
        // URL de la DB
        String url = "jdbc:sqlite:database/altea.db";
        Connection conn = DriverManager.getConnection(url);

        // Usamos try with resources para cerrar el stmt automáticamente
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON");

        } catch (SQLException e) {
            conn.close();
            throw e;
        }

        return conn;
    }
}