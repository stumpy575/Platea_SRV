package com.platea.server.handler;

import com.google.gson.JsonParser;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

public class MessageHandler {
    public static String handleMessage(String message) {
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
