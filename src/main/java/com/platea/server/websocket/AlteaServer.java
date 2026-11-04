package com.platea.server.websocket;

import java.net.InetSocketAddress;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import com.platea.server.handler.MessageHandler;
import com.platea.server.reservas.ReservasManager;
import com.platea.server.xml.CatalogoXmlReader;

public class AlteaServer extends WebSocketServer {
    private int port;
    private CatalogoXmlReader catalogoXmlReader;

    // Gestor de reservas de entradas
    private ReservasManager reservasManager;

    public AlteaServer(int port, CatalogoXmlReader catalogoXmlReader) {

        super(new InetSocketAddress(port));
        this.port = port;
        this.catalogoXmlReader = catalogoXmlReader;

        // Creamos el gestor de reservas utilizando el catálogo XML
        this.reservasManager = new ReservasManager(catalogoXmlReader);

    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        System.out.println("Cliente desconectado: " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        String response = MessageHandler.handleMessage(message, catalogoXmlReader, reservasManager);
        conn.send(response);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        System.out.println("Nuevo cliente conectado: " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onStart() {
        System.out.println("Servidor Altea iniciado correctamente en el puerto " + port);
    }

}