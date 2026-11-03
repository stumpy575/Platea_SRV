package com.platea.server;

import com.platea.server.websocket.AlteaServer;
import com.platea.server.xml.CatalogoXmlReader;

public class Main {
    public static void main(String[] args) {

        // Cargamos el catálogo
        CatalogoXmlReader catalogoXmlReader = new CatalogoXmlReader();
        // Si falla la lectura del catálogo, se parará el server y no funcionará.
        try {
            catalogoXmlReader.loadCatalogo();

        } catch (IllegalStateException e) {
            System.out.println(e.getMessage());
            return; // Para que no se abra el servidor si falla el catálogo
        }

        // Server Config
        int port = 3000;
        AlteaServer alteaServer = new AlteaServer(port);
        System.out.println("Iniciando servidor Altea...");
        alteaServer.start();

    }

}