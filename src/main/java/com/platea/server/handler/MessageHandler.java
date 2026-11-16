
package com.platea.server.handler;

import com.google.gson.JsonParser;
import com.platea.server.model.Evento;
import com.platea.server.model.TipoEntrada;
import com.platea.server.model.Recinto;
import com.platea.server.model.Zona;
import com.platea.server.reservas.ReservasManager;
import com.platea.server.database.CatalogoDBReader;
import com.platea.server.database.MisEntradasDBReader;
import com.platea.server.database.AsientosDBReader;

import java.util.Collection;
import java.util.List;
import java.sql.SQLException;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

public class MessageHandler {

    public static String handleMessage(String message, CatalogoDBReader catalogoDBReader,
            ReservasManager reservasManager) {

        try {
            // Creamos un elemento JSON
            JsonElement element = JsonParser.parseString(message);

            // Si ese elemento no es un OBJETO JSON devolvemos respuesta de error
            if (!element.isJsonObject()) {
                return createErrorResponse("El mensaje debe ser un objeto JSON.");
            }

            // Si es un objeto json, lo obtenemos a partir del elemento
            JsonObject request = element.getAsJsonObject();

            // Si NO existe "type" o su valor es null
            if (!request.has("type") || request.get("type").isJsonNull()) {
                return createErrorResponse("No existe type o su valor es null.");
            }

            // Si type NO es un valor primitivo o NO es una cadena de texto (String)
            // Esto evita que type contenga valores como objetos o arrays o lo rechaza si no
            // es una cadena de texto
            if (!request.get("type").isJsonPrimitive()
                    || !request.get("type").getAsJsonPrimitive().isString()) {
                return createErrorResponse(
                        "El valor type no es un tipo primitivo o no es una cadena de texto.");
            }

            // Si el type es valido, lo obtenemos como String
            String type = request.get("type").getAsString();

            // ES VÁLIDO.

            // Si type es ping:
            if (type.equals("ping")) {
                JsonObject response = new JsonObject();
                response.addProperty("type", "pong");
                return response.toString();
            }

            // Si type es bounce:
            else if (type.equals("bounce")) {
                return message;
            }

            // Si type es getEventos:
            else if (type.equals("getEventos")) {

                Collection<Evento> values = catalogoDBReader.getEventos().values();

                JsonObject response = new JsonObject();
                response.addProperty("type", "eventos");

                JsonArray eventosArray = new JsonArray();

                for (Evento e : values) {

                    JsonObject evento = new JsonObject();

                    evento.addProperty("id", e.getId());
                    evento.addProperty("nom", e.getNom());
                    evento.addProperty("recinte", e.getRecinte());
                    evento.addProperty("dataHora", e.getDataHora().toString());
                    evento.addProperty("categoria", e.getCategoria());
                    evento.addProperty("descripcio", e.getDescripcio());
                    evento.addProperty("imatge", e.getImatge());

                    eventosArray.add(evento);
                }

                response.add("eventos", eventosArray);
                return response.toString();
            }

            // ********** CONSULTAR RECINTOS Y ZONAS **********

            // Si type es getRecintos:
            else if (type.equals("getRecintos")) {

                // Recuperamos los recintos cargados desde SQLite
                Collection<Recinto> values = catalogoDBReader.getRecintos().values();

                JsonObject response = new JsonObject();
                response.addProperty("type", "recintos");

                JsonArray recintosArray = new JsonArray();

                // Recorremos todos los recintos
                for (Recinto r : values) {

                    JsonObject recinto = new JsonObject();

                    recinto.addProperty("id", r.getId());
                    recinto.addProperty("nombre", r.getNom());

                    JsonArray zonasArray = new JsonArray();

                    // Recorremos todas las zonas del recinto
                    for (Zona z : r.getZones()) {

                        JsonObject zona = new JsonObject();

                        zona.addProperty("id", z.getId());
                        zona.addProperty("nombre", z.getNom());
                        zona.addProperty("tipo", z.getTipus());
                        zona.addProperty("aforo", z.getAforament());

                        // Las zonas numeradas tienen filas y butacas por fila
                        if ("numerada".equalsIgnoreCase(z.getTipus())) {

                            zona.addProperty("filas", z.getFilas());
                            zona.addProperty(
                                    "asientosPorFila",
                                    z.getAsientosPorFila());

                        } else {

                            // Las zonas generales no tienen asientos numerados
                            zona.add("filas", com.google.gson.JsonNull.INSTANCE);
                            zona.add("asientosPorFila",
                                    com.google.gson.JsonNull.INSTANCE);
                        }

                        zonasArray.add(zona);
                    }

                    recinto.add("zonas", zonasArray);
                    recintosArray.add(recinto);
                }

                response.add("recintos", recintosArray);

                return response.toString();
            }

            // Si type es getTiposEntrada:
            else if (type.equals("getTiposEntrada")) {

                // Si NO existe "eventoId" o su valor es null
                if (!request.has("eventoId") || request.get("eventoId").isJsonNull()) {
                    return createErrorResponse("No existe el evento o su valor es null.");
                }

                // Comprobamos que es un string (igual que arriba)
                if (!request.get("eventoId").isJsonPrimitive()
                        || !request.get("eventoId").getAsJsonPrimitive().isString()) {
                    return createErrorResponse(
                            "El valor eventoId no es un tipo primitivo o no es una cadena de texto.");
                }

                String eventoId = request.get("eventoId").getAsString();

                // Comprobamos que no está vacío ""
                if (eventoId.isBlank()) {
                    return createErrorResponse("El eventoId es una cadena vacía.");
                }

                Evento evento = catalogoDBReader.getEventos().get(eventoId);

                if (evento == null) {
                    return createErrorResponse("No se ha encontrado ningún evento.");
                }

                List<TipoEntrada> listaTipoEntrada = evento.getTipusEntrades();

                JsonObject response = new JsonObject();
                response.addProperty("type", "tiposEntrada");
                response.addProperty("eventoId", eventoId);

                JsonArray tiposEntradaArray = new JsonArray();

                for (TipoEntrada e : listaTipoEntrada) {

                    JsonObject entrada = new JsonObject();

                    entrada.addProperty("id", e.getId());
                    entrada.addProperty("nom", e.getNom());
                    entrada.addProperty("zona", e.getZona());
                    entrada.addProperty("preu", e.getPreu());

                    // Calculamos las entradas disponibles descontando las reservas
                    int disponibles;

                    try {
                        disponibles = reservasManager.getDisponibles(eventoId, e);

                    } catch (SQLException ex) {
                        return createErrorResponse(
                                "No se ha podido consultar la disponibilidad.");
                    }

                    entrada.addProperty("disponibles", disponibles);
                    tiposEntradaArray.add(entrada);
                }

                response.add("tiposEntrada", tiposEntradaArray);
                return response.toString();
            }

            // ********** COMPRAR ENTRADAS **********

            // Si type es comprarEntradas:
            else if (type.equals("comprarEntradas")) {

                // Enviamos los datos al gestor de reservas
                // Este comprueba la compra, genera los localizadores y guarda los datos en
                // SQLite
                return reservasManager.comprarEntradas(request);
            }

            // ********** CONSULTAR MIS ENTRADAS **********

            // Si type es getMisEntradas:
            else if (type.equals("getMisEntradas")) {

                // Comprobamos que exista el correo y que sea una cadena de texto
                if (!request.has("correo")
                        || !request.get("correo").isJsonPrimitive()
                        || !request.get("correo").getAsJsonPrimitive().isString()) {
                    return createErrorResponse("No se ha recibido un correo válido.");
                }

                String correo = request.get("correo").getAsString().trim();

                // Comprobamos que el correo no esté vacío
                if (correo.isBlank()) {
                    return createErrorResponse("El correo está vacío.");
                }

                try {
                    // Consultamos las entradas asociadas al correo en SQLite
                    JsonArray entradas = MisEntradasDBReader.getMisEntradas(correo);

                    JsonObject response = new JsonObject();
                    response.addProperty("type", "misEntradas");
                    response.add("entradas", entradas);

                    return response.toString();

                } catch (SQLException e) {
                    return createErrorResponse(
                            "No se han podido consultar las entradas del usuario.");
                }
            }

            // ********** CONSULTAR BUTACAS NUMERADAS **********

            // Si type es getAsientos:
            else if (type.equals("getAsientos")) {

                // Comprobamos que exista el eventoId y sea un String
                if (!request.has("eventoId")
                        || !request.get("eventoId").isJsonPrimitive()
                        || !request.get("eventoId").getAsJsonPrimitive().isString()) {
                    return createErrorResponse("No se ha recibido un eventoId válido.");
                }

                // Comprobamos que exista el tipoEntradaId y sea un String
                if (!request.has("tipoEntradaId")
                        || !request.get("tipoEntradaId").isJsonPrimitive()
                        || !request.get("tipoEntradaId").getAsJsonPrimitive().isString()) {
                    return createErrorResponse("No se ha recibido un tipoEntradaId válido.");
                }

                String eventoId = request.get("eventoId").getAsString().trim();
                String tipoEntradaId = request.get("tipoEntradaId").getAsString().trim();

                // Comprobamos que los identificadores no estén vacíos
                if (eventoId.isBlank() || tipoEntradaId.isBlank()) {
                    return createErrorResponse(
                            "El eventoId o el tipoEntradaId están vacíos.");
                }

                try {
                    // Consultamos el estado real de las butacas en SQLite
                    JsonObject response = AsientosDBReader.getAsientos(
                            eventoId, tipoEntradaId);

                    return response.toString();

                } catch (IllegalArgumentException e) {
                    return createErrorResponse(e.getMessage());

                } catch (SQLException e) {
                    return createErrorResponse(
                            "No se ha podido consultar el estado de las butacas.");
                }
            }

            // Si no es ningún tipo de mensaje conocido
            else {
                return createErrorResponse("Tipo de mensaje desconocido.");
            }

        } catch (JsonParseException e) {
            return createErrorResponse(
                    "El mensaje recibido no contiene un JSON válido.");
        }
    }

    // **** MÉTODOS ÚTILES ****

    // Método para gestionar los type error de JSONs con mensaje personalizado.
    private static String createErrorResponse(String errorMessage) {

        JsonObject jsonError = new JsonObject();

        jsonError.addProperty("type", "error");
        jsonError.addProperty("message", errorMessage);

        return jsonError.toString();
    }
}
