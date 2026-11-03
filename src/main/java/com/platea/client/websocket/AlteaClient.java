package com.platea.client.websocket;

import java.net.URI;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

public class AlteaClient extends WebSocketClient {

    // Constructor
    public AlteaClient(URI uri) {
        super(uri);
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        System.out.println("Conectado al servidor Altea");
    }

    @Override
    public void onMessage(String message) {
        System.out.println(message);
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        System.out.println("Desconectado del servidor.");
    }

    @Override
    public void onError(Exception ex) {
        System.out.println(ex.getMessage());
    }

}
