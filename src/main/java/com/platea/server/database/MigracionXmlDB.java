package com.platea.server.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.ResultSet;

import com.platea.server.xml.CatalogoXmlReader;
import com.platea.server.model.Recinto;
import com.platea.server.model.Zona;
import com.platea.server.model.Evento;
import com.platea.server.model.TipoEntrada;

public class MigracionXmlDB {
    // Método para migrar los datos del XML a SQLite
    public static void migrar() throws SQLException {
        // Cargamos los datos del XML
        CatalogoXmlReader catalogo = new CatalogoXmlReader();
        catalogo.loadCatalogo();
        // Abrimos la conexión con SQLite
        try (Connection conn = DatabaseConnection.getConnection()) {
            // Se agrupan las inserciones en una sola
            // De esta manera si una falla, no se guardan cambios a la mitad
            conn.setAutoCommit(false);

            try {
                // Comprobamos si el catálogo ya está migrado
                if (catalogoMigrado(conn)) {
                    System.out.println("El catálogo ya está migrado a SQLite.");
                    conn.rollback();
                    return;
                }

                // Aquí realizaremos todas las inserciones
                insertarRecintos(conn, catalogo);
                insertarZonas(conn, catalogo);
                insertarAsientos(conn, catalogo);
                insertarEventos(conn, catalogo);
                insertarTiposEntrada(conn, catalogo);
                conn.commit();

            } catch (SQLException e) {
                // Deshace los cambios si hay algun error SQL
                conn.rollback();
                throw e;
            }
        }
    }

    // Método para comprobar si el catálogo ya está migrado
    private static boolean catalogoMigrado(Connection conn) throws SQLException {

        String sql = "SELECT COUNT(*) FROM EVENTOS";

        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        }

        return false;
    }

    // Método para insertar los recintos en la BD
    private static void insertarRecintos(Connection conn, CatalogoXmlReader catalogo) throws SQLException {
        String sql = "INSERT INTO RECINTOS (id, nombre) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Recinto recinto : catalogo.getRecintos().values()) {
                ps.setString(1, recinto.getId()); // Ej: R1
                ps.setString(2, recinto.getNom()); // Ej: Sala Petita
                ps.executeUpdate(); // ejecutamos el preparedstatement
            }
        }
    }

    // Método para insertar las zonas en la BD
    private static void insertarZonas(Connection conn, CatalogoXmlReader catalogo) throws SQLException {
        String sql = "INSERT INTO ZONAS (id, recinto_id, nombre, tipo, aforo, filas, asientos_por_fila) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {

            for (Recinto recinto : catalogo.getRecintos().values()) {

                for (Zona zona : recinto.getZones()) {

                    ps.setString(1, zona.getId());
                    ps.setString(2, recinto.getId());
                    ps.setString(3, zona.getNom());
                    ps.setString(4, zona.getTipus());
                    ps.setInt(5, zona.getAforament());
                    // IgnoreCase para que no haga falta q coincidan mayusculas
                    if (zona.getTipus().equalsIgnoreCase("numerada")) {
                        ps.setInt(6, zona.getFilas());
                        ps.setInt(7, zona.getAsientosPorFila());
                    } else {
                        // java.sql.Types.INTEGER indica que el campo vacio corresponde a un num entero
                        ps.setNull(6, java.sql.Types.INTEGER);
                        ps.setNull(7, java.sql.Types.INTEGER);
                    }

                    ps.executeUpdate();
                }
            }
        }

    }

    // Método para insertar los asientos de las zonas numeradas
    private static void insertarAsientos(Connection conn, CatalogoXmlReader catalogo) throws SQLException {

        String sql = "INSERT INTO ASIENTOS (zona_id, fila, numero) VALUES (?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {

            for (Recinto recinto : catalogo.getRecintos().values()) {

                for (Zona zona : recinto.getZones()) {

                    // Solo generamos asientos para las zonas numeradas
                    if (zona.getTipus().equalsIgnoreCase("numerada")) {

                        // Recorremos las filas de la zona
                        for (int fila = 1; fila <= zona.getFilas(); fila++) {

                            // Recorremos los asientos de cada fila
                            for (int numero = 1; numero <= zona.getAsientosPorFila(); numero++) {

                                ps.setString(1, zona.getId());
                                ps.setInt(2, fila);
                                ps.setInt(3, numero);

                                ps.executeUpdate();
                            }
                        }
                    }
                }
            }
        }
    }

    // Método para insertar los eventos en la BD
    private static void insertarEventos(Connection conn, CatalogoXmlReader catalogo) throws SQLException {

        String sql = "INSERT INTO EVENTOS (id, recinto_id, nombre, fecha_hora, categoria, descripcion, imagen) VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {

            for (Evento evento : catalogo.getEventos().values()) {

                ps.setString(1, evento.getId());
                ps.setString(2, evento.getRecinte());
                ps.setString(3, evento.getNom());
                ps.setString(4, evento.getDataHora().toString());
                ps.setString(5, evento.getCategoria());
                ps.setString(6, evento.getDescripcio());
                ps.setString(7, evento.getImatge());

                ps.executeUpdate();
            }
        }
    }

    // Método para insertar los tipos de entrada en la BD
    private static void insertarTiposEntrada(Connection conn, CatalogoXmlReader catalogo) throws SQLException {

        String sql = "INSERT INTO TIPOS_ENTRADA (id, evento_id, zona_id, nombre, precio, cuota) VALUES (?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {

            for (Evento evento : catalogo.getEventos().values()) {

                for (TipoEntrada tipo : evento.getTipusEntrades()) {

                    ps.setString(1, tipo.getId());
                    ps.setString(2, evento.getId());
                    ps.setString(3, tipo.getZona());
                    ps.setString(4, tipo.getNom());
                    ps.setDouble(5, tipo.getPreu().doubleValue());
                    ps.setInt(6, tipo.getQuota());

                    ps.executeUpdate();
                }
            }
        }
    }
    
}