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
                throw new SQLException(
                        "El catálogo SQLite está incompleto.");
            }
        }

        // Importamos el XML mediante el método que ya tenemos
        MigracionXmlDB.migrar();

        System.out.println("Catálogo XML migrado correctamente a SQLite.");
    }

    // Método para contar las tablas de Altea
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

        // Comprobamos que exista el archivo SQL
        if (!Files.exists(archivo)) {
            throw new IOException("No se ha encontrado database/schema.sql");
        }

        // Leemos las instrucciones SQL
        String contenido = Files.readString(archivo);

        // Separamos las instrucciones por punto y coma
        String[] instrucciones = contenido.split(";");

        // Agrupamos la creación de tablas en una transacción
        conn.setAutoCommit(false);

        try (Statement stmt = conn.createStatement()) {

            for (String instruccion : instrucciones) {

                String sql = instruccion.trim();

                if (!sql.isEmpty()) {
                    stmt.execute(sql);
                }
            }

            // Comprobamos que se hayan creado las siete tablas
            if (contarTablas(conn) != 7) {
                throw new SQLException(
                        "No se han creado correctamente las siete tablas.");
            }

            conn.commit();

        } catch (SQLException e) {

            // Deshacemos los cambios si falla la creación
            conn.rollback();
            throw e;
        }
    }
}