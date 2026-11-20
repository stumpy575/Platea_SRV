package com.platea.server.model;

import java.util.ArrayList;
import java.util.List;

// Constructor
public class Recinto {
    private String id;
    private String nom;
    private List<Zona> zones;

    public Recinto(String id, String nom) {
        this.id = id;
        this.nom = nom;
        this.zones = new ArrayList<Zona>();
    }

    public void addZona(Zona zona) {
        zones.add(zona);
    }

    public String getId() {
        return id;
    }

    public String getNom() {
        return nom;
    }

    public List<Zona> getZones() {
        return zones;
    }

}
