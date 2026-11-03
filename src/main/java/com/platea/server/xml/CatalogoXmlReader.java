package com.platea.server.xml;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.HashMap;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.platea.server.model.Evento;
import com.platea.server.model.Recinto;
import com.platea.server.model.TipoEntrada;
import com.platea.server.model.Zona;

public class CatalogoXmlReader {
    private Map<String, Recinto> recintos = new HashMap<>();
    private Map<String, Evento> eventos = new HashMap<>();

    public void loadCatalogo() {
        Map<String, Recinto> recintosTemp = new HashMap<>();
        Map<String, Evento> eventosTemp = new HashMap<>();
        // Cogemos el archivo XML
        try (InputStream input = CatalogoXmlReader.class.getResourceAsStream("/ESDEVENIMENTS.XML");) {
            // Comprobamos si el archivo existe.
            if (input == null) {
                throw new FileNotFoundException("No se ha encontrado ESDEVENIMENTS.XML");
            }

            // Creamos la factoria de constructor de docs
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            // Creamos el constructor de docs
            DocumentBuilder db = dbf.newDocumentBuilder();
            // Analiza el fichero XML
            Document doc = db.parse(input);

            // ********** RECINTOS ***********

            // Guardamos los nodos de recinto. Equivale a algo parecido a lista con indices
            // donde cada indice almacena toda la info del recinto.
            NodeList recintosNodes = doc.getElementsByTagName("recinte");

            for (int i = 0; i < recintosNodes.getLength(); i++) {
                // Convertimos el Nodo a Elemento mediante casting
                Element elmRecinto = (Element) recintosNodes.item(i);
                // Sacamos los atributos que nos interesan del recinto
                String id = elmRecinto.getAttribute("id"); // ID
                String nom = elmRecinto.getAttribute("nom"); // Nombre

                // Creamos un objeto recinto con su respectivo constructor
                Recinto recinto = new Recinto(id, nom);

                // Sacamos todas las zonas de cada recinto como una NodeList
                // Igual que antes, esto almacena toda la info de cada zona
                NodeList zonasRecinto = elmRecinto.getElementsByTagName("zona");

                // Recorremos la lista de zonas del recinto
                for (int j = 0; j < zonasRecinto.getLength(); j++) {
                    Element elmZona = (Element) zonasRecinto.item(j);
                    String idZona = elmZona.getAttribute("id");
                    String nomZona = elmZona.getAttribute("nom");
                    String tipusZona = elmZona.getAttribute("tipus");
                    int aforamentZona = Integer.parseInt(elmZona.getAttribute("aforament"));
                    // Creamos un objeto de tipo Zona y lo añadimos con el métod addZona al recinto
                    Zona zona = new Zona(idZona, nomZona, tipusZona, aforamentZona);
                    recinto.addZona(zona);
                }

                // Metemos en el HashMap de recintos el/los recintos encontrados en el XML
                recintosTemp.put(id, recinto);
            }
            // ---------------------------------------------------------------------------------

            // ********** EVENTOS **********
            NodeList eventosNodeList = doc.getElementsByTagName("esdeveniment");

            for (int i = 0; i < eventosNodeList.getLength(); i++) {
                Element elmEvento = (Element) eventosNodeList.item(i);
                String id = elmEvento.getAttribute("id");
                String recinte = elmEvento.getAttribute("recinte");
                String nom = elmEvento.getElementsByTagName("nom").item(0).getTextContent();
                LocalDateTime dataHora = LocalDateTime
                        .parse(elmEvento.getElementsByTagName("dataHora").item(0).getTextContent());
                String categoria = elmEvento.getElementsByTagName("categoria").item(0).getTextContent();
                String descripcio = elmEvento.getElementsByTagName("descripcio").item(0).getTextContent();
                String imatge = elmEvento.getElementsByTagName("imatge").item(0).getTextContent();

                // Creamos el objeto de tipo evento
                Evento evento = new Evento(id, recinte, nom, dataHora, categoria, descripcio, imatge);

                // Aquí va el for de Entrades
                NodeList tipusEntrada = elmEvento.getElementsByTagName("tipusEntrada");
                for (int j = 0; j < tipusEntrada.getLength(); j++) {
                    Element elmtipusEntrada = (Element) tipusEntrada.item(j);
                    String idEntrada = elmtipusEntrada.getAttribute("id");
                    String nomEntrada = elmtipusEntrada.getAttribute("nom");
                    String idZona = elmtipusEntrada.getAttribute("zona");
                    BigDecimal preu = new BigDecimal(elmtipusEntrada.getAttribute("preu"));
                    int quota = Integer.parseInt(elmtipusEntrada.getAttribute("quota"));

                    // Creamos un objeto de TipoEntrada y lo añadimos con el métod addTipoEntrada al
                    // recinto
                    TipoEntrada tipoEntrada = new TipoEntrada(idEntrada, nomEntrada, idZona, preu, quota);
                    evento.addTipoEntrada(tipoEntrada);
                }

                // Lo metemos en el HashMap de Eventos
                eventosTemp.put(id, evento);
            }

            recintos = recintosTemp;
            eventos = eventosTemp;
        }

        catch (Exception e) {
            throw new IllegalStateException("No se ha podido cargar el catálogo XML.", e);
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
