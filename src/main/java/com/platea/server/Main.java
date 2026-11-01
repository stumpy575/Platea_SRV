package com.platea.server;

import com.platea.server.websocket.AlteaServer;

public class Main {
    public static void main(String[] args) {

        int port = 3000;
        AlteaServer alteaServer = new AlteaServer(port);
        System.out.println("Iniciando servidor Altea...");
        alteaServer.start();

    }

}