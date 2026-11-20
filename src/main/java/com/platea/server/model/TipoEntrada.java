package com.platea.server.model;

import java.math.BigDecimal;

public class TipoEntrada {
    private String id;
    private String nom;
    private String zona;
    private BigDecimal preu; // BigDecimal es mas preciso para temas de precios
    private int quota;

    // Constructor
    public TipoEntrada(String id, String nom, String zona, BigDecimal preu, int quota) {
        this.id = id;
        this.nom = nom;
        this.zona = zona;
        this.preu = preu;
        this.quota = quota;
    }

    public String getId() {
        return id;
    }

    public String getNom() {
        return nom;
    }

    public String getZona() {
        return zona;
    }

    public BigDecimal getPreu() {
        return preu;
    }

    public int getQuota() {
        return quota;
    }

}
