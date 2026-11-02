package com.platea.server.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Evento {
    private String id; // Ej: E1
    private String recinte; // Ej: R1
    private String nom; // Coses que no es diuen...
    private LocalDateTime dataHora; // 2026-11-21T21:30
    private String categoria; // Monòleg
    private String descripcio; // Una hora d'humor...
    private String imatge; // monoleg-sopar.jpg
    private List<TipoEntrada> tipusEntrades;

    public Evento(String id, String recinte, String nom, LocalDateTime dataHora, String categoria, String descripcio,
            String imatge) {
        this.id = id;
        this.recinte = recinte;
        this.nom = nom;
        this.dataHora = dataHora;
        this.categoria = categoria;
        this.descripcio = descripcio;
        this.imatge = imatge;
        this.tipusEntrades = new ArrayList<TipoEntrada>();
    }

    public void addTipoEntrada(TipoEntrada tipoEntrada) {
        tipusEntrades.add(tipoEntrada);
    }

    public String getId() {
        return id;
    }

    public String getRecinte() {
        return recinte;
    }

    public String getNom() {
        return nom;
    }

    public LocalDateTime getDataHora() {
        return dataHora;
    }

    public String getCategoria() {
        return categoria;
    }

    public String getDescripcio() {
        return descripcio;
    }

    public String getImatge() {
        return imatge;
    }

    public List<TipoEntrada> getTipusEntrades() {
        return tipusEntrades;
    }

}
