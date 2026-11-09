package com.platea.server.model;

public class Zona {
    private String id; // Ej: Z1
    private String nom; // Ej: Platea
    private String tipus; // Ej: numerada / general
    private int aforament; // Ej: 40 / 60
    private int filas; // Ej: 6
    private int asientosPorFila; // Ej: 10

    // Constructor
    public Zona(String id, String nom, String tipus, int aforament, int filas, int asientosPorFila) {
        this.id = id;
        this.nom = nom;
        this.tipus = tipus;
        this.aforament = aforament;
        this.filas = filas;
        this.asientosPorFila = asientosPorFila;
    }

    public String getId() {
        return id;
    }

    public String getNom() {
        return nom;
    }

    public String getTipus() {
        return tipus;
    }

    public int getAforament() {
        return aforament;
    }

    public int getFilas() {
        return filas;
    }

    public int getAsientosPorFila() {
        return asientosPorFila;
    }

}