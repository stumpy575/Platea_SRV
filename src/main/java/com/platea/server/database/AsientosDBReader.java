
package com.platea.server.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class AsientosDBReader {

    // Método para consultar el estado de las butacas de un evento
    // y un tipo de entrada numerada.
    public static JsonObject getAsientos(String eventoId, String tipoEntradaId)
            throws SQLException {

        // Buscamos la zona del tipo de entrada y comprobamos que sea numerada
        String sqlZona = "SELECT z.id, z.nombre, z.tipo, "
                + "z.filas, z.asientos_por_fila "
                + "FROM TIPOS_ENTRADA t "
                + "JOIN EVENTOS ev ON t.evento_id = ev.id "
                + "JOIN ZONAS z ON t.zona_id = z.id "
                + "WHERE t.id = ? AND ev.id = ? "
                + "AND z.recinto_id = ev.recinto_id";

        // Consultamos los asientos y comprobamos su ocupación real en SQLite
        String sqlAsientos = "SELECT a.id, a.fila, a.numero, "
                + "EXISTS ("
                + "SELECT 1 FROM ENTRADAS e "
                + "WHERE e.evento_id = ? "
                + "AND e.asiento_id = a.id "
                + "AND e.estado IN ('reservada', 'vendida')"
                + ") AS ocupado "
                + "FROM ASIENTOS a "
                + "WHERE a.zona_id = ? "
                + "ORDER BY a.fila, a.numero";

        try (Connection conn = DatabaseConnection.getConnection()) {

            String zonaId;
            String zonaNombre;
            int filas;
            int asientosPorFila;

            // ********** VALIDAR EVENTO, TIPO Y ZONA **********

            try (PreparedStatement ps = conn.prepareStatement(sqlZona)) {

                ps.setString(1, tipoEntradaId);
                ps.setString(2, eventoId);

                try (ResultSet rs = ps.executeQuery()) {

                    if (!rs.next()) {
                        throw new IllegalArgumentException(
                                "No existe el tipo de entrada para ese evento.");
                    }

                    if (!"numerada".equalsIgnoreCase(rs.getString("tipo"))) {
                        throw new IllegalArgumentException(
                                "El tipo de entrada no pertenece a una zona numerada.");
                    }

                    zonaId = rs.getString("id");
                    zonaNombre = rs.getString("nombre");
                    filas = rs.getInt("filas");
                    asientosPorFila = rs.getInt("asientos_por_fila");
                }
            }

            // ********** CONSULTAR BUTACAS **********

            JsonArray asientos = new JsonArray();

            try (PreparedStatement ps = conn.prepareStatement(sqlAsientos)) {

                ps.setString(1, eventoId);
                ps.setString(2, zonaId);

                try (ResultSet rs = ps.executeQuery()) {

                    while (rs.next()) {

                        JsonObject asiento = new JsonObject();

                        asiento.addProperty("id", rs.getInt("id"));
                        asiento.addProperty("fila", rs.getInt("fila"));
                        asiento.addProperty("numero", rs.getInt("numero"));

                        // Las entradas reservadas o vendidas ocupan la butaca
                        boolean ocupado = rs.getInt("ocupado") != 0;

                        asiento.addProperty(
                                "estado",
                                ocupado ? "vendida" : "libre");

                        asientos.add(asiento);
                    }
                }
            }

            // ********** PREPARAR RESPUESTA **********

            JsonObject respuesta = new JsonObject();

            respuesta.addProperty("type", "asientos");
            respuesta.addProperty("eventoId", eventoId);
            respuesta.addProperty("tipoEntradaId", tipoEntradaId);
            respuesta.addProperty("zonaId", zonaId);
            respuesta.addProperty("zonaNombre", zonaNombre);
            respuesta.addProperty("filas", filas);
            respuesta.addProperty("asientosPorFila", asientosPorFila);
            respuesta.add("asientos", asientos);

            return respuesta;
        }
    }
}
