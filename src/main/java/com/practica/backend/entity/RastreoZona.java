package com.practica.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Entidad para rastrear el estado de tiempo de un empleado en una zona.
 * Guarda cuándo entró a la zona actual y su estado de tiempo.
 */
@Entity
@Table(name = "rastreo_zona")
public class RastreoZona {

    private static final ZoneId ZONA_COLOMBIA = ZoneId.of("America/Bogota");

    /**
     * Estados de tiempo en zona:
     * - BIEN: 0-3 minutos (verde)
     * - NORMAL: 3-10 minutos (amarillo)
     * - PREOCUPANTE: 10+ minutos (rojo)
     */
    public enum EstadoTiempo {
        BIEN,
        NORMAL,
        PREOCUPANTE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "empleado_id", nullable = false, unique = true)
    private Usuario empleado;

    /**
     * Zona actual donde se encuentra el empleado (null si no está en ninguna zona)
     */
    @ManyToOne
    @JoinColumn(name = "zona_actual_id")
    private Zona zonaActual;

    /**
     * Timestamp de cuando entró a la zona actual
     */
    @Column(name = "timestamp_entrada_zona")
    private LocalDateTime timestampEntradaZona;

    /**
     * Estado actual de tiempo en la zona
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private EstadoTiempo estadoTiempo = EstadoTiempo.BIEN;

    /**
     * Última ubicación conocida
     */
    private Double ultimaLatitud;
    private Double ultimaLongitud;

    /**
     * Timestamp de la última actualización
     */
    @Column(name = "ultima_actualizacion")
    private LocalDateTime ultimaActualizacion;

    /**
     * Indica si ya se envió notificación de estado PREOCUPANTE
     * (para no enviar múltiples notificaciones)
     */
    @Column(name = "notificacion_preocupante_enviada")
    private Boolean notificacionPreocupanteEnviada = false;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        ultimaActualizacion = LocalDateTime.now(ZONA_COLOMBIA);
    }

    // Constructors
    public RastreoZona() {
    }

    public RastreoZona(Usuario empleado) {
        this.empleado = empleado;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Usuario getEmpleado() {
        return empleado;
    }

    public void setEmpleado(Usuario empleado) {
        this.empleado = empleado;
    }

    public Zona getZonaActual() {
        return zonaActual;
    }

    public void setZonaActual(Zona zonaActual) {
        this.zonaActual = zonaActual;
    }

    public LocalDateTime getTimestampEntradaZona() {
        return timestampEntradaZona;
    }

    public void setTimestampEntradaZona(LocalDateTime timestampEntradaZona) {
        this.timestampEntradaZona = timestampEntradaZona;
    }

    public EstadoTiempo getEstadoTiempo() {
        return estadoTiempo;
    }

    public void setEstadoTiempo(EstadoTiempo estadoTiempo) {
        this.estadoTiempo = estadoTiempo;
    }

    public Double getUltimaLatitud() {
        return ultimaLatitud;
    }

    public void setUltimaLatitud(Double ultimaLatitud) {
        this.ultimaLatitud = ultimaLatitud;
    }

    public Double getUltimaLongitud() {
        return ultimaLongitud;
    }

    public void setUltimaLongitud(Double ultimaLongitud) {
        this.ultimaLongitud = ultimaLongitud;
    }

    public LocalDateTime getUltimaActualizacion() {
        return ultimaActualizacion;
    }

    public void setUltimaActualizacion(LocalDateTime ultimaActualizacion) {
        this.ultimaActualizacion = ultimaActualizacion;
    }

    public Boolean getNotificacionPreocupanteEnviada() {
        return notificacionPreocupanteEnviada;
    }

    public void setNotificacionPreocupanteEnviada(Boolean notificacionPreocupanteEnviada) {
        this.notificacionPreocupanteEnviada = notificacionPreocupanteEnviada;
    }
}
