package com.platea.server.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

public class MisEntradasDBReader {

    // Método para obtener las entradas compradas por un correo electrónico
    public static JsonArray getMisEntradas(String correo) throws SQLException {

        JsonArray entradas = new JsonArray();

        // Consultamos las compras del usuario y sus entradas
        String sql = "SELECT e.id, e.compra_id, e.evento_id, "
                + "e.tipo_entrada_id, e.localizador, e.titular, "
                + "e.precio, e.estado, e.asiento_id, "
                + "c.fecha AS fecha_compra, "
                + "ev.nombre AS evento_nombre, ev.fecha_hora, ev.imagen, "
                + "t.nombre AS tipo_nombre, t.zona_id, "
                + "a.fila, a.numero "
                + "FROM ENTRADAS e "
                + "JOIN COMPRAS c ON e.compra_id = c.id "
                + "JOIN EVENTOS ev ON e.evento_id = ev.id "
                + "JOIN TIPOS_ENTRADA t ON e.tipo_entrada_id = t.id "
                + "LEFT JOIN ASIENTOS a ON e.asiento_id = a.id "
                + "WHERE LOWER(TRIM(c.comprador_correo)) = LOWER(TRIM(?)) "
                + "ORDER BY c.fecha DESC, e.id DESC";

        // Abrimos la conexión con SQLite
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, correo);

            try (ResultSet rs = ps.executeQuery()) {

                // Recorremos todas las entradas encontradas
                while (rs.next()) {

                    JsonObject entrada = new JsonObject();

                    entrada.addProperty("id", rs.getInt("id"));
                    entrada.addProperty("compraId", rs.getInt("compra_id"));
                    entrada.addProperty("eventoId", rs.getString("evento_id"));
                    entrada.addProperty("tipoEntradaId", rs.getString("tipo_entrada_id"));
                    entrada.addProperty("localizador", rs.getString("localizador"));
                    entrada.addProperty("titular", rs.getString("titular"));
                    entrada.addProperty("precio", rs.getDouble("precio"));
                    entrada.addProperty("estado", rs.getString("estado"));
                    entrada.addProperty("fechaCompra", rs.getString("fecha_compra"));

                    // Datos del evento para mostrarlos en la APP
                    entrada.addProperty("eventoNom", rs.getString("evento_nombre"));
                    entrada.addProperty("dataHora", rs.getString("fecha_hora"));
                    entrada.addProperty("imatge", rs.getString("imagen"));

                    // Datos del tipo de entrada
                    entrada.addProperty("tipoEntradaNom", rs.getString("tipo_nombre"));
                    entrada.addProperty("zona", rs.getString("zona_id"));

                    // Las entradas generales no tienen asiento asignado
                    int asientoId = rs.getInt("asiento_id");

                    if (rs.wasNull()) {
                        entrada.add("asientoId", JsonNull.INSTANCE);
                        entrada.add("fila", JsonNull.INSTANCE);
                        entrada.add("numero", JsonNull.INSTANCE);
                    } else {
                        entrada.addProperty("asientoId", asientoId);
                        entrada.addProperty("fila", rs.getInt("fila"));
                        entrada.addProperty("numero", rs.getInt("numero"));
                    }

                    entradas.add(entrada);
                }
            }
        }

        return entradas;
    }
}