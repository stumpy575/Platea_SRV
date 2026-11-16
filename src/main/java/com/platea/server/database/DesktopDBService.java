package com.platea.server.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

/* Consutlas y validación d entradas para el DESKTOP. */
public class DesktopDBService {

    // Aforo disponible de una zona general en un evento concreto.
    public static JsonObject getAforoZona(String eventoId, String zonaId) throws SQLException {
        String sql = "SELECT z.aforo, z.tipo, "
                + "(SELECT COUNT(*) FROM ENTRADAS e "
                + "JOIN TIPOS_ENTRADA t ON t.id = e.tipo_entrada_id "
                + "WHERE e.evento_id = ev.id AND t.evento_id = ev.id "
                + "AND t.zona_id = z.id AND e.estado IN ('reservada','vendida')) AS ocupadas "
                + "FROM EVENTOS ev JOIN ZONAS z ON z.recinto_id = ev.recinto_id "
                + "WHERE ev.id = ? AND z.id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, eventoId);
            ps.setString(2, zonaId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("El evento o la zona no existen o no correpsondenn al mismo recinto.");
                }
                if (!"general".equalsIgnoreCase(rs.getString("tipo"))) {
                    throw new IllegalArgumentException("La zona indicada no es general.");
                }
                int aforo = rs.getInt("aforo");
                int ocupadas = rs.getInt("ocupadas");
                JsonObject respuesta = new JsonObject();
                respuesta.addProperty("type", "aforoZona");
                respuesta.addProperty("eventoId", eventoId);
                respuesta.addProperty("zonaId", zonaId);
                respuesta.addProperty("aforo", aforo);
                respuesta.addProperty("ocupadas", ocupadas);
                respuesta.addProperty("disponibles", Math.max(0, aforo - ocupadas));
                return respuesta;
            }
        }
    }

    // Listado cronológico de entradas de un evento incluidas canceladas o validadas.
    public static JsonObject getEntradasEvento(String eventoId) throws SQLException {
        String existe = "SELECT 1 FROM EVENTOS WHERE id = ?";
        String sql = "SELECT c.fecha AS fecha_compra, c.comprador_nombre, "
                + "e.localizador, e.tipo_entrada_id, t.nombre AS tipo_nombre, "
                + "e.titular, e.estado, a.fila, a.numero, a.id AS asiento_id, "
                + "v.fecha AS fecha_validacion "
                + "FROM ENTRADAS e "
                + "JOIN COMPRAS c ON c.id = e.compra_id "
                + "JOIN TIPOS_ENTRADA t ON t.id = e.tipo_entrada_id "
                + "LEFT JOIN ASIENTOS a ON a.id = e.asiento_id "
                + "LEFT JOIN VALIDACIONES v ON v.entrada_id = e.id "
                + "WHERE e.evento_id = ? AND t.evento_id = e.evento_id "
                + "ORDER BY c.fecha ASC, e.id ASC";

        try (Connection conn = DatabaseConnection.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(existe)) {
                ps.setString(1, eventoId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new IllegalArgumentException("El evento no existe.");
                    }
                }
            }
            JsonArray entradas = new JsonArray();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, eventoId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        JsonObject entrada = new JsonObject();
                        entrada.addProperty("fechaCompra", rs.getString("fecha_compra"));
                        entrada.addProperty("localizador", rs.getString("localizador"));
                        entrada.addProperty("tipoEntradaId", rs.getString("tipo_entrada_id"));
                        entrada.addProperty("tipoEntradaNom", rs.getString("tipo_nombre"));
                        entrada.addProperty("titular", rs.getString("titular"));
                        entrada.addProperty("compradorNombre", rs.getString("comprador_nombre"));
                        String fechaValidacion = rs.getString("fecha_validacion");
                        entrada.addProperty("estado", fechaValidacion != null ? "validada" : rs.getString("estado"));
                        if (fechaValidacion == null) {
                            entrada.add("fechaValidacion", JsonNull.INSTANCE);
                        } else {
                            entrada.addProperty("fechaValidacion", fechaValidacion);
                        }
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
            JsonObject respuesta = new JsonObject();
            respuesta.addProperty("type", "entradasEvento");
            respuesta.addProperty("eventoId", eventoId);
            respuesta.add("entradas", entradas);
            return respuesta;
        }
    }

    // Inserción atómica: UNIQUE(entrada_id) impide validar dos veces el mismo localizador.
    public static JsonObject validarEntrada(String localizador) throws SQLException {
        String insertar = "INSERT OR IGNORE INTO VALIDACIONES (entrada_id, fecha) "
                + "SELECT id, ? FROM ENTRADAS "
                + "WHERE localizador = ? AND estado = 'vendida'";
        String consulta = "SELECT e.estado, e.titular, t.nombre AS tipo_nombre, "
                + "a.fila, a.numero, a.id AS asiento_id, v.fecha AS fecha_validacion "
                + "FROM ENTRADAS e JOIN TIPOS_ENTRADA t ON t.id = e.tipo_entrada_id "
                + "LEFT JOIN ASIENTOS a ON a.id = e.asiento_id "
                + "LEFT JOIN VALIDACIONES v ON v.entrada_id = e.id "
                + "WHERE e.localizador = ?";
        JsonObject respuesta = new JsonObject();
        respuesta.addProperty("type", "validacionEntrada");
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int insertadas;
                try (PreparedStatement ps = conn.prepareStatement(insertar)) {
                    ps.setString(1, LocalDateTime.now().toString());
                    ps.setString(2, localizador);
                    insertadas = ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(consulta)) {
                    ps.setString(1, localizador);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            respuesta.addProperty("ok", false);
                            respuesta.addProperty("motivo", "El localizador no existe.");
                        } else if (insertadas == 1) {
                            respuesta.addProperty("ok", true);
                            respuesta.addProperty("titular", rs.getString("titular"));
                            respuesta.addProperty("tipoEntradaNom", rs.getString("tipo_nombre"));
                            int asientoId = rs.getInt("asiento_id");
                            if (rs.wasNull()) {
                                respuesta.add("fila", JsonNull.INSTANCE);
                                respuesta.add("numero", JsonNull.INSTANCE);
                            } else {
                                respuesta.addProperty("fila", rs.getInt("fila"));
                                respuesta.addProperty("numero", rs.getInt("numero"));
                            }
                        } else if (rs.getString("fecha_validacion") != null) {
                            respuesta.addProperty("ok", false);
                            respuesta.addProperty("motivo", "La entrada ya ha sido validada.");
                        } else {
                            respuesta.addProperty("ok", false);
                            respuesta.addProperty("motivo", "La entrada no está vendida (estado: " + rs.getString("estado") + ").");
                        }
                    }
                }
                conn.commit();
                return respuesta;
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        }
    }
}
