package com.platea.server.database;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.HashMap;

import com.platea.server.model.Evento;
import com.platea.server.model.Recinto;
import com.platea.server.model.TipoEntrada;
import com.platea.server.model.Zona;

public class CatalogoDBReader {

    private Map<String, Recinto> recintos = new HashMap<>();
    private Map<String, Evento> eventos = new HashMap<>();

    // Método para cargar el catálogo desde SQLite
    public void loadCatalogo() {

        Map<String, Recinto> recintosTemp = new HashMap<>();
        Map<String, Evento> eventosTemp = new HashMap<>();

        // Abrimos la conexión con SQLite
        try (Connection conn = DatabaseConnection.getConnection()) {

            // ********** RECINTOS **********
            String sqlRecintos = "SELECT id, nombre FROM RECINTOS";

            try (PreparedStatement ps = conn.prepareStatement(sqlRecintos);
                    ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {

                    String id = rs.getString("id");
                    String nom = rs.getString("nombre");

                    Recinto recinto = new Recinto(id, nom);
                    recintosTemp.put(id, recinto);
                }
            }

            // ********** ZONAS **********
            String sqlZonas = "SELECT id, recinto_id, nombre, tipo, aforo, filas, asientos_por_fila FROM ZONAS";

            try (PreparedStatement ps = conn.prepareStatement(sqlZonas);
                    ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {

                    String id = rs.getString("id");
                    String recintoId = rs.getString("recinto_id");
                    String nom = rs.getString("nombre");
                    String tipus = rs.getString("tipo");
                    int aforament = rs.getInt("aforo");
                    int filas = rs.getInt("filas");
                    int asientosPorFila = rs.getInt("asientos_por_fila");

                    Zona zona = new Zona(id, nom, tipus, aforament, filas, asientosPorFila);

                    // Añadimos la zona a su recinto
                    Recinto recinto = recintosTemp.get(recintoId);
                    if (recinto == null) {
                        throw new SQLException("No existe el recinto de la zona: " + id);
                    }

                    recinto.addZona(zona);
                }
            }

            // ********** EVENTOS **********
            String sqlEventos = "SELECT id, recinto_id, nombre, fecha_hora, categoria, descripcion, imagen FROM EVENTOS";

            try (PreparedStatement ps = conn.prepareStatement(sqlEventos);
                    ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {

                    String id = rs.getString("id");
                    String recinte = rs.getString("recinto_id");
                    String nom = rs.getString("nombre");
                    LocalDateTime dataHora = LocalDateTime.parse(rs.getString("fecha_hora"));
                    String categoria = rs.getString("categoria");
                    String descripcio = rs.getString("descripcion");
                    String imatge = rs.getString("imagen");

                    Evento evento = new Evento(id, recinte, nom, dataHora, categoria, descripcio, imatge);
                    eventosTemp.put(id, evento);
                }
            }

            // ********** TIPOS DE ENTRADA **********
            String sqlTipos = "SELECT id, evento_id, zona_id, nombre, precio, cuota FROM TIPOS_ENTRADA";

            try (PreparedStatement ps = conn.prepareStatement(sqlTipos);
                    ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {

                    String id = rs.getString("id");
                    String eventoId = rs.getString("evento_id");
                    String nom = rs.getString("nombre");
                    String idZona = rs.getString("zona_id");
                    BigDecimal preu = BigDecimal.valueOf(rs.getDouble("precio"));
                    int quota = rs.getInt("cuota");

                    TipoEntrada tipoEntrada = new TipoEntrada(id, nom, idZona, preu, quota);

                    // Añadimos el tipo de entrada a su evento
                    Evento evento = eventosTemp.get(eventoId);
                    if (evento == null) {
                        throw new SQLException("No existe el evento del tipo de entrada: " + id);
                    }

                    evento.addTipoEntrada(tipoEntrada);
                }
            }

            // Guardamos los datos cuando todas las consultas han terminado
            recintos = recintosTemp;
            eventos = eventosTemp;

        } catch (Exception e) {
            throw new IllegalStateException("No se ha podido cargar el catálogo desde SQLite.", e);
        }
    }

    // GETTERS
    public Map<String, Recinto> getRecintos() {
        return recintos;
    }

    public Map<String, Evento> getEventos() {
        return eventos;
    }
}