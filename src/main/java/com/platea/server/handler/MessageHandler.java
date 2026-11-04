package com.platea.server.handler;

import com.google.gson.JsonParser;
import com.platea.server.model.Evento;
import com.platea.server.model.TipoEntrada;
import com.platea.server.reservas.ReservasManager;
import com.platea.server.xml.CatalogoXmlReader;
import java.util.Collection;
import java.util.List;
import java.io.IOException;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

public class MessageHandler {
    public static String handleMessage(String message, CatalogoXmlReader catalogoXmlReader,
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
            if (!request.get("type").isJsonPrimitive() || !request.get("type").getAsJsonPrimitive().isString()) {
                return createErrorResponse("El valor type no es un tipo primitivo o no es una cadena de texto.");
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
                Collection<Evento> values = catalogoXmlReader.getEventos().values();
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

                Evento evento = catalogoXmlReader.getEventos().get(eventoId);

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
                    } catch (IOException ex) {
                        return createErrorResponse("No se ha podido consultar la disponibilidad.");
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
                // Este comprueba la compra, genera los localizadores y guarda el JSON
                return reservasManager.comprarEntradas(request);
            }

            // Si no es ni type ni bounce
            else {
                return createErrorResponse("Tipo de mensaje desconocido.");
            }
        }

        catch (JsonParseException e) {
            return createErrorResponse("El mensaje recibido no contiene un JSON válido.");
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