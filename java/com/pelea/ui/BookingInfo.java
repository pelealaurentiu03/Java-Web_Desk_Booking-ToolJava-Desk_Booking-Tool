package com.pelea.ui;

import java.io.Serializable;

/**
 * Această clasă transportă datele despre rezervări între 
 * Baza de Date (MongoDB) și Interfața Web (Vaadin).
 */
public class BookingInfo implements Serializable {
    private static final long serialVersionUID = 1L;

    private String deskId;
    private String email;
    private String name;
    private String photo;
    private String date;
    private String interval;

    // Constructorul rămâne la fel
    public BookingInfo(String deskId, String email, String name, String photo, String date, String interval) {
        this.deskId = deskId;
        this.email = email;
        this.name = name;
        this.photo = photo;
        this.date = date;
        this.interval = interval;
    }

    // Getters - Foarte importanți pentru tabelele din Vaadin
    public String getDeskId() { return deskId; }
    public String getEmail() { return email; }
    public String getName() { return name; }
    public String getPhoto() { return photo; }
    public String getDate() { return date; }
    public String getInterval() { return interval; }

    // Setters - Utili dacă vrei să modifici datele direct în UI
    public void setDeskId(String deskId) { this.deskId = deskId; }
    public void setEmail(String email) { this.email = email; }
    public void setName(String name) { this.name = name; }
    public void setPhoto(String photo) { this.photo = photo; }
    public void setDate(String date) { this.date = date; }
    public void setInterval(String interval) { this.interval = interval; }
}