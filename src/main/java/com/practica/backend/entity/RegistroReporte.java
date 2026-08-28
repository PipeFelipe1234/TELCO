package com.practica.backend.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "registro_reportes", uniqueConstraints = {
        @UniqueConstraint(name = "uq_registro_reporte_fecha", columnNames = { "registro_id", "fecha_hora" })
})
public class RegistroReporte {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registro_id", nullable = false)
    private Registro registro;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora;

    private Double latitud;
    private Double longitud;
    private Double precisionMetros;

    @Column(columnDefinition = "TEXT")
    private String reporte;

    private String picture;

    @Column(columnDefinition = "TEXT")
    private String ubicacion;

    @Column(nullable = false)
    private Boolean esSalida = false;

    @Column(name = "novedad_id")
    private Long novedadId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Registro getRegistro() {
        return registro;
    }

    public void setRegistro(Registro registro) {
        this.registro = registro;
    }

    public LocalDateTime getFechaHora() {
        return fechaHora;
    }

    public void setFechaHora(LocalDateTime fechaHora) {
        this.fechaHora = fechaHora;
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

    public String getReporte() {
        return reporte;
    }

    public void setReporte(String reporte) {
        this.reporte = reporte;
    }

    public String getPicture() {
        return picture;
    }

    public void setPicture(String picture) {
        this.picture = picture;
    }

    public String getUbicacion() {
        return ubicacion;
    }

    public void setUbicacion(String ubicacion) {
        this.ubicacion = ubicacion;
    }

    public Boolean getEsSalida() {
        return esSalida;
    }

    public void setEsSalida(Boolean esSalida) {
        this.esSalida = esSalida;
    }

    public Long getNovedadId() {
        return novedadId;
    }

    public void setNovedadId(Long novedadId) {
        this.novedadId = novedadId;
    }
}
