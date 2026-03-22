package com.practica.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "solicitudes_ubicacion")
public class SolicitudUbicacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "admin_id", nullable = false)
    private Usuario admin; // Quién pidió la ubicación

    @ManyToOne
    @JoinColumn(name = "empleado_id", nullable = false)
    private Usuario empleado; // A quién se le pide

    @Column(length = 20)
    private String estado = "PENDIENTE"; // PENDIENTE | RESPONDIDA | EXPIRADA | ERROR

    @Column(columnDefinition = "TEXT")
    private String mensajeError; // Error cuando no se pudo obtener ubicación (ej: GPS desactivado)

    private Double latitud; // Se llena cuando el empleado responde
    private Double longitud;
    private Double precisionMetros;

    @Column(columnDefinition = "TEXT")
    private String ubicacion; // Dirección legible (reverse geocoding)

    @Column(name = "fecha_solicitud")
    private LocalDateTime fechaSolicitud;

    @Column(name = "fecha_respuesta")
    private LocalDateTime fechaRespuesta;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // 🤖 Indica si la solicitud fue generada automáticamente por el sistema
    @Column(name = "es_automatica", columnDefinition = "boolean default false")
    private Boolean esAutomatica = false;

    // Zona horaria de Colombia
    private static final ZoneId ZONA_COLOMBIA = ZoneId.of("America/Bogota");

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now(ZONA_COLOMBIA);
        fechaSolicitud = LocalDateTime.now(ZONA_COLOMBIA);
        if (estado == null) {
            estado = "PENDIENTE";
        }
    }

    // Constructors
    public SolicitudUbicacion() {
    }

    public SolicitudUbicacion(Usuario admin, Usuario empleado) {
        this.admin = admin;
        this.empleado = empleado;
        this.estado = "PENDIENTE";
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Usuario getAdmin() {
        return admin;
    }

    public void setAdmin(Usuario admin) {
        this.admin = admin;
    }

    public Usuario getEmpleado() {
        return empleado;
    }

    public void setEmpleado(Usuario empleado) {
        this.empleado = empleado;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public Double getLatitud() {
        return latitud;
    }

    public void setLatitud(Double latitud) {
        this.latitud = latitud;
    }

    public Double getLongitud() {
        return longitud;
    }

    public void setLongitud(Double longitud) {
        this.longitud = longitud;
    }

    public Double getPrecisionMetros() {
        return precisionMetros;
    }

    public void setPrecisionMetros(Double precisionMetros) {
        this.precisionMetros = precisionMetros;
    }

    public String getUbicacion() {
        return ubicacion;
    }

    public void setUbicacion(String ubicacion) {
        this.ubicacion = ubicacion;
    }

    public LocalDateTime getFechaSolicitud() {
        return fechaSolicitud;
    }

    public void setFechaSolicitud(LocalDateTime fechaSolicitud) {
        this.fechaSolicitud = fechaSolicitud;
    }

    public LocalDateTime getFechaRespuesta() {
        return fechaRespuesta;
    }

    public void setFechaRespuesta(LocalDateTime fechaRespuesta) {
        this.fechaRespuesta = fechaRespuesta;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getMensajeError() {
        return mensajeError;
    }

    public void setMensajeError(String mensajeError) {
        this.mensajeError = mensajeError;
    }

    public Boolean getEsAutomatica() {
        return esAutomatica;
    }

    public void setEsAutomatica(Boolean esAutomatica) {
        this.esAutomatica = esAutomatica;
    }
}
