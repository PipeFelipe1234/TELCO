package com.practica.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Entidad para almacenar zonas geográficas (geocercas).
 * Las coordenadas se guardan como JSON para simplicidad y portabilidad.
 */
@Entity
@Table(name = "zonas")
public class Zona {

    private static final ZoneId ZONA_COLOMBIA = ZoneId.of("America/Bogota");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nombre;

    /**
     * Nodo lógico al que pertenece la zona.
     * Un nodo puede tener múltiples zonas.
     */
    @Column(length = 100)
    private String nodo;

    /**
     * Coordenadas del polígono en formato JSON.
     * Formato: [[lng1,lat1],[lng2,lat2],...,[lng1,lat1]]
     * (El último punto debe ser igual al primero para cerrar el polígono)
     */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String coordenadas;

    /**
     * Color para visualización en el mapa (hex)
     */
    @Column(length = 20)
    private String color = "#FF0000";

    /**
     * Indica si la zona está activa para el rastreo
     */
    @Column(nullable = false)
    private Boolean activa = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now(ZONA_COLOMBIA);
        updatedAt = LocalDateTime.now(ZONA_COLOMBIA);
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now(ZONA_COLOMBIA);
    }

    // Constructors
    public Zona() {
    }

    public Zona(String nombre, String coordenadas) {
        this.nombre = nombre;
        this.coordenadas = coordenadas;
    }

    public Zona(String nombre, String coordenadas, String color) {
        this.nombre = nombre;
        this.coordenadas = coordenadas;
        this.color = color;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getNodo() {
        return nodo;
    }

    public void setNodo(String nodo) {
        this.nodo = nodo;
    }

    public String getCoordenadas() {
        return coordenadas;
    }

    public void setCoordenadas(String coordenadas) {
        this.coordenadas = coordenadas;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public Boolean getActiva() {
        return activa;
    }

    public void setActiva(Boolean activa) {
        this.activa = activa;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
