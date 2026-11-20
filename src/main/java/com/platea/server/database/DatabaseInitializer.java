package com.platea.server.database;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseInitializer {

    // Método para inicializar la base de datos automáticamente
    public static void inicializar() throws SQLException, IOException {

        // Comprobamos que exista la carpeta database
        Path carpeta = Path.of("database");
        Files.createDirectories(carpeta);

        // Abrimos la conexión con SQLite
        try (Connection conn = DatabaseConnection.getConnection()) {

            // Comprobamos cuántas tablas de Altea existen
            int tablas = contarTablas(conn);

            if (tablas == 0) {

                // Comprobamos que no haya otras tablas desconocidas
                String sql = "SELECT COUNT(*) FROM sqlite_master "
                        + "WHERE type = 'table' AND name NOT LIKE 'sqlite_%'";

                try (PreparedStatement ps = conn.prepareStatement(sql);
                        ResultSet rs = ps.executeQuery()) {

                    if (rs.next() && rs.getInt(1) > 0) {
                        throw new SQLException(
                                "La base de datos contiene tablas desconocidas.");
                    }
                }

                // Creamos las tablas utilizando schema.sql
                crearTablas(conn);
                System.out.println("Tablas SQLite creadas correctamente.");

            } else if (tablas != 7) {

                // No modificamos una base de datos incompleta
                throw new SQLException(
                        "La base de datos está incompleta. "
                                + "Se han encontrado " + tablas + " de 7 tablas.");
            }

            // Nueva tabla auxiliar para registrar entradas validadas.
            // CREATE IF NOT EXISTS conserva intactas las compras anteriores.
            crearTablaValidaciones(conn);
        }

        // Comprobamos si el catálogo ya contiene datos
        try (Connection conn = DatabaseConnection.getConnection()) {

            int recintos = contarRegistros(conn, "RECINTOS");
            int zonas = contarRegistros(conn, "ZONAS");
            int asientos = contarRegistros(conn, "ASIENTOS");
            int eventos = contarRegistros(conn, "EVENTOS");
            int tiposEntrada = contarRegistros(conn, "TIPOS_ENTRADA");

            // Si todas las tablas del catálogo están vacías, migramos el XML
            if (recintos == 0 && zonas == 0 && asientos == 0
                    && eventos == 0 && tiposEntrada == 0) {

                // Comprobamos que no existan compras anteriores
                if (contarRegistros(conn, "COMPRAS") > 0
                        || contarRegistros(conn, "ENTRADAS") > 0) {
                    throw new SQLException(
                            "Existen compras sin un catálogo inicializado.");
                }

            } else if (recintos > 0 && zonas > 0 && asientos > 0
                    && eventos > 0 && tiposEntrada > 0) {

                // El catálogo ya existe y conservamos todos sus datos
                System.out.println("La base de datos ya está inicializada.");
                return;

            } else {
                // Evitamos modificar un catálogo parcialmente cargado
                throw new SQLException("El catálogo SQLite está incompleto.");
            }
        }

        // Importamos el XML mediante el método que ya tenemos
        MigracionXmlDB.migrar();
        System.out.println("Catálogo XML migrado correctamente a SQLite.");
    }

    // Crear tabla auxiliar de validaciones tanto en BD nueva como existente
    private static void crearTablaValidaciones(Connection conn) throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS VALIDACIONES ("
                + "entrada_id INTEGER PRIMARY KEY, "
                + "fecha TEXT NOT NULL, "
                + "FOREIGN KEY (entrada_id) REFERENCES ENTRADAS(id))";
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    // Método para contar las siete tablas principales de Altea
    private static int contarTablas(Connection conn) throws SQLException {
        String sql = "SELECT COUNT(*) FROM sqlite_master "
                + "WHERE type = 'table' "
                + "AND name IN ('RECINTOS', 'ZONAS', 'ASIENTOS', "
                + "'EVENTOS', 'TIPOS_ENTRADA', 'COMPRAS', 'ENTRADAS')";

        try (PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    // Método para contar los registros de una tabla
    private static int contarRegistros(Connection conn, String tabla)
            throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + tabla;
        try (PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    // Método para crear las tablas utilizando schema.sql
    private static void crearTablas(Connection conn)
            throws SQLException, IOException {
        Path archivo = Path.of("database", "schema.sql");
        if (!Files.exists(archivo)) {
            throw new IOException("No se ha encontrado database/schema.sql");
        }
        String contenido = Files.readString(archivo);
        String[] instrucciones = contenido.split(";");
        conn.setAutoCommit(false);
        try (Statement stmt = conn.createStatement()) {
            for (String instruccion : instrucciones) {
                String sql = instruccion.trim();
                if (!sql.isEmpty()) {
                    stmt.execute(sql);
                }
            }
            if (contarTablas(conn) != 7) {
                throw new SQLException(
                        "No se han creado correctamente las siete tablas.");
            }
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        }
    }
}
