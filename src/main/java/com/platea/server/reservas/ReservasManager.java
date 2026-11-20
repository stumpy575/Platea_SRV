
package com.platea.server.reservas;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import com.platea.server.database.CatalogoDBReader;
import com.platea.server.database.DatabaseConnection;
import com.platea.server.model.Evento;
import com.platea.server.model.Recinto;
import com.platea.server.model.TipoEntrada;
import com.platea.server.model.Zona;

public class ReservasManager {

    // Catálogo de eventos cargado desde SQLite
    private CatalogoDBReader catalogoDBReader;

    // Constructor
    public ReservasManager(CatalogoDBReader catalogoDBReader) {
        this.catalogoDBReader = catalogoDBReader;
    }

    // Método para devolver un error de compra
    private String crearError(String mensaje) {

        JsonObject respuesta = new JsonObject();
        respuesta.addProperty("type", "compraError");
        respuesta.addProperty("message", mensaje);

        return respuesta.toString();
    }

    // Método para devolver las butacas que han fallado
    private String crearErrorAsientos(String mensaje, JsonArray fallidos) {

        JsonObject respuesta = new JsonObject();
        respuesta.addProperty("type", "compraError");
        respuesta.addProperty("message", mensaje);
        respuesta.add("asientosFallidos", fallidos);

        return respuesta.toString();
    }

    // Método para buscar un tipo de entrada dentro de un evento
    private TipoEntrada buscarTipoEntrada(Evento evento, String tipoEntradaId) {

        for (TipoEntrada tipo : evento.getTipusEntrades()) {

            if (tipo.getId().equals(tipoEntradaId)) {
                return tipo;
            }
        }

        return null;
    }

    // Método para buscar la zona de un tipo de entrada
    private Zona buscarZona(Evento evento, TipoEntrada tipoEntrada) {

        Recinto recinto = catalogoDBReader.getRecintos().get(evento.getRecinte());

        if (recinto == null) {
            return null;
        }

        for (Zona zona : recinto.getZones()) {

            if (zona.getId().equals(tipoEntrada.getZona())) {
                return zona;
            }
        }

        return null;
    }

    // Comprobamos si el tipo de entrada pertenece a una zona general
    private boolean esGeneral(Evento evento, TipoEntrada tipoEntrada) {

        Zona zona = buscarZona(evento, tipoEntrada);

        if (zona == null) {
            return false;
        }

        return zona.getTipus().equalsIgnoreCase("general");
    }

    // Contamos las entradas reservadas o vendidas de un tipo
    private int contarReservadas(Connection conn, String eventoId,
            String tipoEntradaId) throws SQLException {

        String sql = "SELECT COUNT(*) FROM ENTRADAS "
                + "WHERE evento_id = ? AND tipo_entrada_id = ? "
                + "AND estado IN ('reservada', 'vendida')";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, eventoId);
            ps.setString(2, tipoEntradaId);

            try (ResultSet rs = ps.executeQuery()) {

                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }

        return 0;
    }

    // Contamos las entradas reservadas o vendidas de una zona en un evento
    private int contarReservadasZona(Connection conn, String eventoId,
            String zonaId) throws SQLException {

        String sql = "SELECT COUNT(*) FROM ENTRADAS e "
                + "JOIN TIPOS_ENTRADA t ON e.tipo_entrada_id = t.id "
                + "WHERE e.evento_id = ? AND t.evento_id = ? "
                + "AND t.zona_id = ? "
                + "AND e.estado IN ('reservada', 'vendida')";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, eventoId);
            ps.setString(2, eventoId);
            ps.setString(3, zonaId);

            try (ResultSet rs = ps.executeQuery()) {

                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }

        return 0;
    }

    // Calculamos las entradas que quedan disponibles
    private int getDisponibles(Connection conn, String eventoId,
            TipoEntrada tipoEntrada) throws SQLException {

        Evento evento = catalogoDBReader.getEventos().get(eventoId);

        if (evento == null) {
            throw new SQLException("No existe el evento " + eventoId);
        }

        Zona zona = buscarZona(evento, tipoEntrada);

        if (zona == null) {
            throw new SQLException("No existe la zona del tipo de entrada.");
        }

        // Comprobamos las entradas disponibles del tipo
        int reservadasTipo = contarReservadas(conn, eventoId, tipoEntrada.getId());
        int disponiblesTipo = tipoEntrada.getQuota() - reservadasTipo;

        // Comprobamos las entradas disponibles de toda la zona
        int reservadasZona = contarReservadasZona(conn, eventoId, zona.getId());
        int disponiblesZona = zona.getAforament() - reservadasZona;

        // Devolvemos el límite más restrictivo
        return Math.max(0, Math.min(disponiblesTipo, disponiblesZona));
    }

    // Método público para consultar la disponibilidad desde SQLite
    public int getDisponibles(String eventoId, TipoEntrada tipoEntrada) throws SQLException {

        try (Connection conn = DatabaseConnection.getConnection()) {
            return getDisponibles(conn, eventoId, tipoEntrada);
        }
    }

    // Método para guardar una compra en la BD
    private long guardarCompra(Connection conn, String nombre,
            String correo) throws SQLException {

        String sql = "INSERT INTO COMPRAS "
                + "(comprador_nombre, comprador_correo, fecha) "
                + "VALUES (?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, nombre);
            ps.setString(2, correo);
            ps.setString(3, LocalDateTime.now().toString());

            ps.executeUpdate();
        }

        // Obtenemos el ID de la compra que acabamos de insertar
        String sqlId = "SELECT last_insert_rowid()";

        try (PreparedStatement ps = conn.prepareStatement(sqlId);
                ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                return rs.getLong(1);
            }
        }

        throw new SQLException("No se ha podido obtener el ID de la compra.");
    }

    // Método para guardar una entrada en la BD
    private void guardarEntrada(Connection conn, long compraId,
            String eventoId, TipoEntrada tipoEntrada,
            Integer asientoId, String localizador, String titular)
            throws SQLException {

        String sql = "INSERT INTO ENTRADAS "
                + "(compra_id, evento_id, tipo_entrada_id, asiento_id, "
                + "localizador, titular, precio, estado) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, compraId);
            ps.setString(2, eventoId);
            ps.setString(3, tipoEntrada.getId());

            // Las entradas generales no tienen asiento asignado
            if (asientoId == null) {
                ps.setNull(4, Types.INTEGER);
            } else {
                ps.setInt(4, asientoId);
            }

            ps.setString(5, localizador);
            ps.setString(6, titular);

            // Guardamos el precio pagado en el momento de la compra
            ps.setDouble(7, tipoEntrada.getPreu().doubleValue());

            // La entrada queda vendida al completar la compra
            ps.setString(8, "vendida");

            ps.executeUpdate();
        }
    }

    // Comprobamos que una butaca existe y pertenece a la zona indicada
    private boolean existeAsiento(Connection conn, int asientoId,
            String zonaId) throws SQLException {

        String sql = "SELECT 1 FROM ASIENTOS "
                + "WHERE id = ? AND zona_id = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, asientoId);
            ps.setString(2, zonaId);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    // Comprobamos si una butaca ya está reservada o vendida para un evento
    private boolean asientoOcupado(Connection conn, String eventoId,
            int asientoId) throws SQLException {

        String sql = "SELECT 1 FROM ENTRADAS "
                + "WHERE evento_id = ? AND asiento_id = ? "
                + "AND estado IN ('reservada', 'vendida') LIMIT 1";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, eventoId);
            ps.setInt(2, asientoId);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    // Comprobamos si un localizador ya existe en SQLite
    private boolean existeLocalizador(Connection conn, String localizador)
            throws SQLException {

        String sql = "SELECT 1 FROM ENTRADAS "
                + "WHERE localizador = ? LIMIT 1";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, localizador);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    // Generamos localizadores UUID de seis caracteres sin duplicados
    private String generarLocalizador(Connection conn) throws SQLException {

        for (int i = 0; i < 100; i++) {

            String localizador = "ALT-"
                    + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

            if (!existeLocalizador(conn, localizador)) {
                return localizador;
            }
        }

        throw new SQLException("No se ha podido generar un localizador único.");
    }

    // Añadimos una butaca con error a la respuesta
    private void agregarAsientoFallido(JsonArray fallidos, String eventoId,
            String tipoEntradaId, int asientoId, String motivo) {

        JsonObject fallo = new JsonObject();

        fallo.addProperty("eventoId", eventoId);
        fallo.addProperty("tipoEntradaId", tipoEntradaId);
        fallo.addProperty("asientoId", asientoId);
        fallo.addProperty("motivo", motivo);

        fallidos.add(fallo);
    }

    // Método principal para realizar la compra
    public String comprarEntradas(JsonObject request) {

        try {

            // ********** COMPROBAR DATOS DEL COMPRADOR **********

            if (request == null
                    || !request.has("nombre") || !request.has("correo")
                    || !request.get("nombre").isJsonPrimitive()
                    || !request.get("correo").isJsonPrimitive()
                    || !request.get("nombre").getAsJsonPrimitive().isString()
                    || !request.get("correo").getAsJsonPrimitive().isString()) {

                return crearError("Faltan datos del comprador.");
            }

            String nombre = request.get("nombre").getAsString().trim();
            String correo = request.get("correo").getAsString().trim();

            if (nombre.isBlank() || correo.isBlank()) {
                return crearError("El nombre o el correo están vacíos.");
            }

            // Comprobamos que exista la lista de entradas
            if (!request.has("entradas") || !request.get("entradas").isJsonArray()) {
                return crearError("No se ha recibido la lista de entradas.");
            }

            JsonArray entradas = request.getAsJsonArray("entradas");

            if (entradas.size() == 0) {
                return crearError("La cesta está vacía.");
            }

            // Guardamos los tipos y zonas solicitados en la compra
            Map<String, Integer> solicitadoPorTipo = new HashMap<>();
            Map<String, Integer> solicitadoPorZona = new HashMap<>();
            Map<String, TipoEntrada> tiposCompra = new HashMap<>();
            Map<String, Zona> zonasCompra = new HashMap<>();
            Map<String, String> eventosPorTipo = new HashMap<>();
            Map<String, String> eventosPorZona = new HashMap<>();

            // Guardamos las entradas de la cesta una vez validadas
            ArrayList<JsonObject> entradasCompra = new ArrayList<>();

            // Guardamos las butacas para detectar duplicados dentro de la cesta
            Set<String> asientosSeleccionados = new HashSet<>();

            // ********** VALIDAR TODA LA COMPRA **********

            for (JsonElement elemento : entradas) {

                if (!elemento.isJsonObject()) {
                    return crearError("Los datos de una entrada no son válidos.");
                }

                JsonObject entrada = elemento.getAsJsonObject();

                // Comprobamos los campos obligatorios
                if (!entrada.has("eventoId") || !entrada.has("tipoEntradaId")
                        || !entrada.get("eventoId").isJsonPrimitive()
                        || !entrada.get("tipoEntradaId").isJsonPrimitive()
                        || !entrada.get("eventoId").getAsJsonPrimitive().isString()
                        || !entrada.get("tipoEntradaId").getAsJsonPrimitive().isString()) {

                    return crearError("Faltan datos de una entrada.");
                }

                String eventoId = entrada.get("eventoId").getAsString().trim();
                String tipoEntradaId = entrada.get("tipoEntradaId").getAsString().trim();

                if (eventoId.isBlank() || tipoEntradaId.isBlank()) {
                    return crearError("El evento o tipo de entrada está vacío.");
                }

                // Buscamos el evento en el catálogo SQLite
                Evento evento = catalogoDBReader.getEventos().get(eventoId);

                if (evento == null) {
                    return crearError("No existe el evento " + eventoId);
                }

                // Buscamos el tipo de entrada
                TipoEntrada tipoEntrada = buscarTipoEntrada(evento, tipoEntradaId);

                if (tipoEntrada == null) {
                    return crearError("No existe el tipo de entrada " + tipoEntradaId);
                }

                // Buscamos la zona para comprobar también su aforo
                Zona zona = buscarZona(evento, tipoEntrada);

                if (zona == null) {
                    return crearError("No existe la zona del tipo de entrada.");
                }

                int cantidad;

                if (esGeneral(evento, tipoEntrada)) {

                    // ********** ENTRADA GENERAL **********

                    if (!entrada.has("cantidad")
                            || !entrada.get("cantidad").isJsonPrimitive()
                            || !entrada.get("cantidad").getAsJsonPrimitive().isNumber()
                            || entrada.has("asientoId")) {

                        return crearError("La entrada general necesita una cantidad.");
                    }

                    try {
                        // Solo admitimos cantidades enteras y positivas
                        cantidad = Integer.parseInt(
                                entrada.get("cantidad").getAsString());

                    } catch (NumberFormatException e) {
                        return crearError("La cantidad no es válida.");
                    }

                    if (cantidad <= 0) {
                        return crearError("La cantidad debe ser mayor que cero.");
                    }

                } else if ("numerada".equalsIgnoreCase(zona.getTipus())) {

                    // ********** ENTRADA NUMERADA **********

                    if (!entrada.has("asientoId")
                            || !entrada.get("asientoId").isJsonPrimitive()
                            || !entrada.get("asientoId").getAsJsonPrimitive().isNumber()
                            || entrada.has("cantidad")) {

                        return crearError(
                                "La entrada numerada necesita un asientoId y no admite cantidad.");
                    }

                    int asientoId;

                    try {
                        asientoId = Integer.parseInt(
                                entrada.get("asientoId").getAsString());

                    } catch (NumberFormatException e) {
                        return crearError("El asientoId no es válido.");
                    }

                    if (asientoId <= 0) {
                        return crearError("El asientoId debe ser positivo.");
                    }

                    // Comprobamos que la misma butaca no esté repetida en la cesta
                    String claveAsiento = eventoId + "|" + asientoId;

                    if (!asientosSeleccionados.add(claveAsiento)) {

                        JsonArray fallidos = new JsonArray();

                        agregarAsientoFallido(fallidos, eventoId,
                                tipoEntradaId, asientoId,
                                "La butaca está repetida en la cesta.");

                        return crearErrorAsientos(
                                "Hay butacas repetidas en la cesta.", fallidos);
                    }

                    // Una entrada numerada representa una única butaca
                    cantidad = 1;

                } else {
                    return crearError("El tipo de zona no es válido.");
                }

                // Sumamos las cantidades si el mismo tipo aparece varias veces
                int totalTipo = Math.addExact(
                        solicitadoPorTipo.getOrDefault(tipoEntradaId, 0),
                        cantidad);

                solicitadoPorTipo.put(tipoEntradaId, totalTipo);
                tiposCompra.put(tipoEntradaId, tipoEntrada);
                eventosPorTipo.put(tipoEntradaId, eventoId);

                // Sumamos las cantidades de todos los tipos de una misma zona
                String claveZona = eventoId + "|" + zona.getId();

                int totalZona = Math.addExact(
                        solicitadoPorZona.getOrDefault(claveZona, 0),
                        cantidad);

                solicitadoPorZona.put(claveZona, totalZona);
                zonasCompra.put(claveZona, zona);
                eventosPorZona.put(claveZona, eventoId);

                entradasCompra.add(entrada);
            }

            // ********** COMPROBAR DISPONIBILIDAD Y GUARDAR COMPRA **********

            // Abrimos una única conexión para toda la compra
            try (Connection conn = DatabaseConnection.getConnection()) {

                // BEGIN IMMEDIATE obtiene la reserva de escritura al principio
                // Evita que dos compras comprueben simultáneamente el mismo aforo
                boolean transaccionIniciada = false;

                try {

                    try (Statement stmt = conn.createStatement()) {
                        stmt.execute("PRAGMA busy_timeout = 5000");
                        stmt.execute("BEGIN IMMEDIATE");
                    }

                    transaccionIniciada = true;

                    // ********** COMPROBAR BUTACAS **********

                    JsonArray asientosFallidos = new JsonArray();

                    // Revisamos todas las butacas antes de guardar ninguna compra
                    for (JsonObject entrada : entradasCompra) {

                        if (!entrada.has("asientoId")) {
                            continue;
                        }

                        String eventoId = entrada.get("eventoId").getAsString();
                        String tipoEntradaId = entrada.get("tipoEntradaId").getAsString();
                        int asientoId = entrada.get("asientoId").getAsInt();

                        TipoEntrada tipoEntrada = tiposCompra.get(tipoEntradaId);
                        Zona zona = buscarZona(
                                catalogoDBReader.getEventos().get(eventoId),
                                tipoEntrada);

                        if (!existeAsiento(conn, asientoId, zona.getId())) {

                            agregarAsientoFallido(asientosFallidos, eventoId,
                                    tipoEntradaId, asientoId,
                                    "La butaca no existe en la zona seleccionada.");

                        } else if (asientoOcupado(conn, eventoId, asientoId)) {

                            agregarAsientoFallido(asientosFallidos, eventoId,
                                    tipoEntradaId, asientoId,
                                    "La butaca ya está reservada o vendida.");
                        }
                    }

                    if (asientosFallidos.size() > 0) {

                        try (Statement stmt = conn.createStatement()) {
                            stmt.execute("ROLLBACK");
                        }

                        transaccionIniciada = false;

                        return crearErrorAsientos(
                                "La compra se ha rechazado porque algunas butacas "
                                        + "no están disponibles.",
                                asientosFallidos);
                    }

                    // ********** COMPROBAR CUOTAS **********

                    // Comprobamos la cuota de cada tipo de entrada
                    for (String tipoEntradaId : solicitadoPorTipo.keySet()) {

                        String eventoId = eventosPorTipo.get(tipoEntradaId);
                        TipoEntrada tipoEntrada = tiposCompra.get(tipoEntradaId);

                        int reservadas = contarReservadas(
                                conn, eventoId, tipoEntradaId);

                        int disponibles = tipoEntrada.getQuota() - reservadas;

                        if (solicitadoPorTipo.get(tipoEntradaId) > disponibles) {

                            try (Statement stmt = conn.createStatement()) {
                                stmt.execute("ROLLBACK");
                            }

                            transaccionIniciada = false;

                            return crearError(
                                    "No hay suficientes entradas disponibles "
                                            + "del tipo " + tipoEntradaId + ".");
                        }
                    }

                    // Comprobamos el aforo compartido de cada zona
                    for (String claveZona : solicitadoPorZona.keySet()) {

                        String eventoId = eventosPorZona.get(claveZona);
                        Zona zona = zonasCompra.get(claveZona);

                        int reservadas = contarReservadasZona(
                                conn, eventoId, zona.getId());

                        int disponibles = zona.getAforament() - reservadas;

                        if (solicitadoPorZona.get(claveZona) > disponibles) {

                            try (Statement stmt = conn.createStatement()) {
                                stmt.execute("ROLLBACK");
                            }

                            transaccionIniciada = false;

                            return crearError(
                                    "No hay suficientes plazas disponibles "
                                            + "en la zona " + zona.getId() + ".");
                        }
                    }

                    // ********** GENERAR LAS RESERVAS **********

                    JsonArray localizadores = new JsonArray();

                    // Guardamos primero la compra y obtenemos su ID
                    long compraId = guardarCompra(conn, nombre, correo);

                    // Recorremos todas las entradas de la cesta
                    for (JsonObject entrada : entradasCompra) {

                        String eventoId = entrada.get("eventoId").getAsString();
                        String tipoEntradaId = entrada.get("tipoEntradaId").getAsString();

                        TipoEntrada tipoEntrada = tiposCompra.get(tipoEntradaId);

                        boolean numerada = entrada.has("asientoId");

                        int cantidad = numerada
                                ? 1
                                : entrada.get("cantidad").getAsInt();

                        Integer asientoId = numerada
                                ? entrada.get("asientoId").getAsInt()
                                : null;

                        // Generamos un localizador por cada entrada comprada
                        for (int i = 0; i < cantidad; i++) {

                            // Conservamos UUID.randomUUID() con seis caracteres
                            String localizador = generarLocalizador(conn);

                            // Reserva que guardaremos en SQLite
                            guardarEntrada(
                                    conn,
                                    compraId,
                                    eventoId,
                                    tipoEntrada,
                                    asientoId,
                                    localizador,
                                    nombre);

                            // Localizador que devolveremos a la APP
                            JsonObject localizadorRespuesta = new JsonObject();

                            localizadorRespuesta.addProperty("eventoId", eventoId);
                            localizadorRespuesta.addProperty(
                                    "tipoEntradaId", tipoEntradaId);
                            localizadorRespuesta.addProperty(
                                    "localizador", localizador);

                            // La respuesta también identifica la butaca comprada
                            if (asientoId != null) {
                                localizadorRespuesta.addProperty(
                                        "asientoId", asientoId);
                            }

                            localizadores.add(localizadorRespuesta);
                        }
                    }

                    // ********** GUARDAR LA COMPRA EN SQLITE **********

                    // Confirmamos todas las inserciones juntas
                    try (Statement stmt = conn.createStatement()) {
                        stmt.execute("COMMIT");
                    }

                    transaccionIniciada = false;

                    // ********** RESPUESTA CORRECTA **********

                    JsonObject respuesta = new JsonObject();
                    respuesta.addProperty("type", "compraOK");
                    respuesta.add("localizadores", localizadores);

                    return respuesta.toString();

                } catch (Exception e) {

                    // Si ocurre algún error, deshacemos toda la compra
                    if (transaccionIniciada) {
                        try (Statement stmt = conn.createStatement()) {
                            stmt.execute("ROLLBACK");
                        } catch (SQLException rollbackError) {
                            e.addSuppressed(rollbackError);
                        }
                    }

                    throw e;
                }
            }

        } catch (Exception e) {

            // Si ocurre algún error, devolvemos una respuesta de compra
            return crearError("No se ha podido completar la compra: " + e.getMessage());
        }
    }
}
