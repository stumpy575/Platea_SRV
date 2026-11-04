package com.platea.server.reservas;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.platea.server.model.Evento;
import com.platea.server.model.Recinto;
import com.platea.server.model.TipoEntrada;
import com.platea.server.model.Zona;
import com.platea.server.xml.CatalogoXmlReader;

public class ReservasManager {

    // Catálogo de eventos cargado desde el XML
    private CatalogoXmlReader catalogoXmlReader;

    // Gson para guardar los archivos JSON
    private Gson gson = new GsonBuilder().setPrettyPrinting().create();

    // Constructor
    public ReservasManager(CatalogoXmlReader catalogoXmlReader) {
        this.catalogoXmlReader = catalogoXmlReader;
    }

    // Método para devolver un error de compra
    private String crearError(String mensaje) {

        JsonObject respuesta = new JsonObject();
        respuesta.addProperty("type", "compraError");
        respuesta.addProperty("message", mensaje);

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

    // Comprobamos si el tipo de entrada pertenece a una zona general
    private boolean esGeneral(Evento evento, TipoEntrada tipoEntrada) {

        Recinto recinto = catalogoXmlReader.getRecintos().get(evento.getRecinte());

        if (recinto == null) {
            return false;
        }

        for (Zona zona : recinto.getZones()) {

            if (zona.getId().equals(tipoEntrada.getZona())) {
                return zona.getTipus().equalsIgnoreCase("general");
            }
        }

        return false;
    }

    // Método para leer las reservas de un evento
    private JsonObject leerReservas(String eventoId) throws IOException {

        File carpeta = new File("reservas");
        File archivo = new File(carpeta, eventoId + ".json");

        // Si todavía no existen reservas, devolvemos un JSON vacío
        if (!archivo.exists()) {

            JsonObject datos = new JsonObject();
            datos.addProperty("eventoId", eventoId);
            datos.add("reservas", new JsonArray());

            return datos;
        }

        // Leemos el archivo JSON
        try (FileReader lector = new FileReader(archivo)) {

            JsonObject datos = JsonParser.parseReader(lector).getAsJsonObject();

            if (!datos.has("reservas") || !datos.get("reservas").isJsonArray()) {
                throw new IOException("El archivo de reservas no es válido.");
            }

            return datos;
        }
    }

    // Contamos las entradas reservadas de un tipo
    private int contarReservadas(String eventoId, String tipoEntradaId) throws IOException {

        JsonObject datos = leerReservas(eventoId);
        JsonArray reservas = datos.getAsJsonArray("reservas");

        int total = 0;

        for (JsonElement elemento : reservas) {

            JsonObject reserva = elemento.getAsJsonObject();

            if (reserva.get("tipoEntradaId").getAsString().equals(tipoEntradaId)) {
                total++;
            }
        }

        return total;
    }

    // Calculamos las entradas que quedan disponibles
    public int getDisponibles(String eventoId, TipoEntrada tipoEntrada) throws IOException {

        int reservadas = contarReservadas(eventoId, tipoEntrada.getId());

        return tipoEntrada.getQuota() - reservadas;
    }

    // Método para guardar las reservas de un evento
    private void guardarReservas(String eventoId, JsonObject datos) throws IOException {

        File carpeta = new File("reservas");

        // Creamos la carpeta si todavía no existe
        if (!carpeta.exists()) {
            carpeta.mkdirs();
        }

        File archivo = new File(carpeta, eventoId + ".json");

        // Guardamos los datos en el archivo JSON
        try (FileWriter escritor = new FileWriter(archivo)) {
            gson.toJson(datos, escritor);
        }
    }

    // Método principal para realizar la compra
    public String comprarEntradas(JsonObject request) {

        try {

            // ********** COMPROBAR DATOS DEL COMPRADOR **********

            if (!request.has("nombre") || !request.has("correo")
                    || !request.get("nombre").isJsonPrimitive()
                    || !request.get("correo").isJsonPrimitive()
                    || !request.get("nombre").getAsJsonPrimitive().isString()
                    || !request.get("correo").getAsJsonPrimitive().isString()) {

                return crearError("Faltan datos del comprador.");
            }

            String nombre = request.get("nombre").getAsString();
            String correo = request.get("correo").getAsString();

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

            // Guardamos los eventos que aparecen en la compra
            ArrayList<String> eventosCompra = new ArrayList<>();

            // ********** VALIDAR TODA LA COMPRA **********

            for (JsonElement elemento : entradas) {

                if (!elemento.isJsonObject()) {
                    return crearError("Los datos de una entrada no son válidos.");
                }

                JsonObject entrada = elemento.getAsJsonObject();

                // Comprobamos los campos obligatorios
                if (!entrada.has("eventoId") || !entrada.has("tipoEntradaId")
                        || !entrada.has("cantidad")
                        || !entrada.get("eventoId").isJsonPrimitive()
                        || !entrada.get("tipoEntradaId").isJsonPrimitive()
                        || !entrada.get("eventoId").getAsJsonPrimitive().isString()
                        || !entrada.get("tipoEntradaId").getAsJsonPrimitive().isString()
                        || !entrada.get("cantidad").isJsonPrimitive()
                        || !entrada.get("cantidad").getAsJsonPrimitive().isNumber()) {

                    return crearError("Faltan datos de una entrada.");
                }

                String eventoId = entrada.get("eventoId").getAsString();
                String tipoEntradaId = entrada.get("tipoEntradaId").getAsString();

                int cantidad;

                try {
                    cantidad = entrada.get("cantidad").getAsInt();
                } catch (Exception e) {
                    return crearError("La cantidad no es válida.");
                }

                if (cantidad <= 0) {
                    return crearError("La cantidad debe ser mayor que cero.");
                }

                // Buscamos el evento en el catálogo XML
                Evento evento = catalogoXmlReader.getEventos().get(eventoId);

                if (evento == null) {
                    return crearError("No existe el evento " + eventoId);
                }

                // Buscamos el tipo de entrada
                TipoEntrada tipoEntrada = buscarTipoEntrada(evento, tipoEntradaId);

                if (tipoEntrada == null) {
                    return crearError("No existe el tipo de entrada " + tipoEntradaId);
                }

                // Solo permitimos entradas generales
                if (!esGeneral(evento, tipoEntrada)) {
                    return crearError("Solo se pueden comprar entradas generales.");
                }

                // Comprobamos la disponibilidad actual
                int disponibles = getDisponibles(eventoId, tipoEntrada);

                // Sumamos las cantidades si el mismo tipo aparece varias veces
                int totalSolicitado = 0;

                for (JsonElement otroElemento : entradas) {

                    if (!otroElemento.isJsonObject()) {
                        continue;
                    }

                    JsonObject otraEntrada = otroElemento.getAsJsonObject();

                    if (otraEntrada.has("eventoId") && otraEntrada.has("tipoEntradaId")
                            && otraEntrada.get("eventoId").isJsonPrimitive()
                            && otraEntrada.get("tipoEntradaId").isJsonPrimitive()
                            && otraEntrada.get("eventoId").getAsString().equals(eventoId)
                            && otraEntrada.get("tipoEntradaId").getAsString().equals(tipoEntradaId)) {

                        if (otraEntrada.has("cantidad")
                                && otraEntrada.get("cantidad").isJsonPrimitive()
                                && otraEntrada.get("cantidad").getAsJsonPrimitive().isNumber()) {

                            try {
                                totalSolicitado += otraEntrada.get("cantidad").getAsInt();
                            } catch (Exception e) {
                                return crearError("La cantidad no es válida.");
                            }
                        }
                    }
                }

                if (totalSolicitado > disponibles) {
                    return crearError("No hay suficientes entradas disponibles.");
                }

                // Guardamos el ID del evento para escribir su archivo después
                if (!eventosCompra.contains(eventoId)) {
                    eventosCompra.add(eventoId);
                }
            }

            // ********** GENERAR LAS RESERVAS **********

            JsonArray localizadores = new JsonArray();

            // Preparamos las reservas de cada evento
            ArrayList<JsonObject> datosEventos = new ArrayList<>();

            for (String eventoId : eventosCompra) {
                datosEventos.add(leerReservas(eventoId));
            }

            // Recorremos todas las entradas de la cesta
            for (JsonElement elemento : entradas) {

                JsonObject entrada = elemento.getAsJsonObject();

                String eventoId = entrada.get("eventoId").getAsString();
                String tipoEntradaId = entrada.get("tipoEntradaId").getAsString();
                int cantidad = entrada.get("cantidad").getAsInt();

                // Buscamos los datos del evento correspondiente
                JsonObject datos = null;

                for (JsonObject datosEvento : datosEventos) {

                    if (datosEvento.get("eventoId").getAsString().equals(eventoId)) {
                        datos = datosEvento;
                        break;
                    }
                }

                JsonArray reservas = datos.getAsJsonArray("reservas");

                // Generamos un localizador por cada entrada comprada
                for (int i = 0; i < cantidad; i++) {

                    String localizador = "ALT-" + UUID.randomUUID().toString();

                    // Reserva que guardaremos en el servidor
                    JsonObject reserva = new JsonObject();
                    reserva.addProperty("tipoEntradaId", tipoEntradaId);
                    reserva.addProperty("localizador", localizador);
                    reserva.addProperty("nombre", nombre);
                    reserva.addProperty("correo", correo);

                    reservas.add(reserva);

                    // Localizador que devolveremos a la APP
                    JsonObject localizadorRespuesta = new JsonObject();
                    localizadorRespuesta.addProperty("eventoId", eventoId);
                    localizadorRespuesta.addProperty("tipoEntradaId", tipoEntradaId);
                    localizadorRespuesta.addProperty("localizador", localizador);

                    localizadores.add(localizadorRespuesta);
                }
            }

            // ********** GUARDAR LOS ARCHIVOS JSON **********

            for (JsonObject datos : datosEventos) {

                String eventoId = datos.get("eventoId").getAsString();

                guardarReservas(eventoId, datos);
            }

            // ********** RESPUESTA CORRECTA **********

            JsonObject respuesta = new JsonObject();
            respuesta.addProperty("type", "compraOK");
            respuesta.add("localizadores", localizadores);

            return respuesta.toString();

        } catch (Exception e) {

            // Si ocurre algún error, devolvemos una respuesta de compra
            return crearError("No se ha podido completar la compra: " + e.getMessage());
        }
    }
}