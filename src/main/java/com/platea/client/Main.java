package com.platea.client;

import java.net.URI;
import java.util.Scanner;

import com.google.gson.JsonObject;
import com.platea.client.websocket.AlteaClient;

public class Main {
    public static void main(String[] args) {

        // URI
        URI uri = URI.create("ws://localhost:3000");

        // Client
        AlteaClient alteaClient = new AlteaClient(uri);
        boolean conexionExitosa;
        // Probamos la conexión
        // connectBlocking devuelve true o false parar saber si se ha conectado o no
        try {
            conexionExitosa = alteaClient.connectBlocking();
            if (conexionExitosa) {
                System.out.println("Conectado con éxito.");
            } else {
                System.out.println("Ha habido un error de conexión.");
                return;
            }
        } catch (InterruptedException e) {
            System.out.println("Se ha interrumpido el intento de conexión.");
            return;
        }

        // PROGRAMA CLIENTE
        Scanner sc = new Scanner(System.in);
        String opcion = "";

        while (!opcion.equals("0")) {
            System.out.println("========== ALTEA CLI ==========\n");
            System.out.println(
                    "\n1. Consultar eventos\n2. Consultar tipos de entrada\n0. Salir\n\nSelecciona una opción: ");
            opcion = sc.nextLine();

            if (opcion.equals("1")) {
                alteaClient.send("{\"type\":\"getEventos\"}");
            }

            else if (opcion.equals("2")) {
                System.out.println("Introduce un identificador de evento:");
                String eventoId = sc.nextLine();
                JsonObject evento = new JsonObject();
                evento.addProperty("type", "getTiposEntrada");
                evento.addProperty("eventoId", eventoId);
                alteaClient.send(evento.toString());
            }

            else if (opcion.equals("0")) {
                System.out.println("Saliendo de Altea...");
                alteaClient.close();
                sc.close();
            }

            else {
                System.out.println("No has elegido una opción válida.");
            }
        }
    }
}