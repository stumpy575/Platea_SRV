package com.platea.server;

import com.platea.server.websocket.AlteaServer;
import com.platea.server.database.CatalogoDBReader;

public class Main {
    public static void main(String[] args) {

        // Cargamos el catálogo desde SQLite
        CatalogoDBReader catalogoDBReader = new CatalogoDBReader();

        // Si falla la lectura del catálogo, se parará el server y no funcionará.
        try {
            catalogoDBReader.loadCatalogo();

        } catch (IllegalStateException e) {
            System.out.println("Error al cargar el catálogo desde SQLite: " + e.getMessage());
            return; // Para que no se abra el servidor si falla el catálogo
        }

        // Server Config
        int port = 3000;
        AlteaServer alteaServer = new AlteaServer(port, catalogoDBReader);
        System.out.println("Iniciando servidor Altea...");
        alteaServer.start();

    }

}