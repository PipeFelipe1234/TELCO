package com.practica.backend.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Entity
@Table(name = "registros")
public class Registro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(nullable = false)
    private LocalTime horaEntrada;
    private LocalTime horaSalida;

    // 📍 GPS SALIDA (viejos campos)
    private Double latitud;
    private Double longitud;
    private Double precisionMetros;

    // 📍 GPS ENTRADA (Check-in)
    private Double latitudCheckin;
    private Double longitudCheckin;
    private Double precisionMetrosCheckin;

    // 📝 Reporte del día
    @Column(columnDefinition = "TEXT")
    private String reporte;

    // 📷 Foto/URL de imagen
    private String picture;

    // 📍 Direcciones de texto legibles
    @Column(columnDefinition = "TEXT")
    private String ubicacionEntrada;

    @Column(columnDefinition = "TEXT")
    private String ubicacionSalida;

    // ⏱️ Horas trabajadas (solo horas enteras)
    private Integer horasTrabajadas;

    // ⏱️ Minutos trabajados (total en minutos)
    private Integer minutosTrabajados;

    // 📋 Relación con RegistroReporte
    @OneToMany(mappedBy = "registro", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<RegistroReporte> reportes;

    public Registro() {
    }

    public Registro(Long id, Usuario usuario, LocalDate fecha, LocalTime horaEntrada, LocalTime horaSalida,
            Double latitud, Double longitud, Double precisionMetros,
            Double latitudCheckin, Double longitudCheckin, Double precisionMetrosCheckin,
            String reporte, String picture) {
        this.id = id;
        this.usuario = usuario;
        this.fecha = fecha;
        this.horaEntrada = horaEntrada;
        this.horaSalida = horaSalida;
        this.latitud = latitud;
        this.longitud = longitud;
        this.precisionMetros = precisionMetros;
        this.latitudCheckin = latitudCheckin;
        this.longitudCheckin = longitudCheckin;
        this.precisionMetrosCheckin = precisionMetrosCheckin;
        this.reporte = reporte;
        this.picture = picture;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }

    public LocalTime getHoraEntrada() {
        return horaEntrada;
    }

    public void setHoraEntrada(LocalTime horaEntrada) {
        this.horaEntrada = horaEntrada;
    }

    public LocalTime getHoraSalida() {
        return horaSalida;
    }

    public void setHoraSalida(LocalTime horaSalida) {
        this.horaSalida = horaSalida;
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

    public Double getLatitudCheckin() {
        return latitudCheckin;
    }

    public void setLatitudCheckin(Double latitudCheckin) {
        this.latitudCheckin = latitudCheckin;
    }

    public Double getLongitudCheckin() {
        return longitudCheckin;
    }

    public void setLongitudCheckin(Double longitudCheckin) {
        this.longitudCheckin = longitudCheckin;
    }

    public Double getPrecisionMetrosCheckin() {
        return precisionMetrosCheckin;
    }

    public void setPrecisionMetrosCheckin(Double precisionMetrosCheckin) {
        this.precisionMetrosCheckin = precisionMetrosCheckin;
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

    public Integer getHorasTrabajadas() {
        return horasTrabajadas;
    }

    public void setHorasTrabajadas(Integer horasTrabajadas) {
        this.horasTrabajadas = horasTrabajadas;
    }

    public Integer getMinutosTrabajados() {
        return minutosTrabajados;
    }

    public void setMinutosTrabajados(Integer minutosTrabajados) {
        this.minutosTrabajados = minutosTrabajados;
    }

    public String getUbicacionEntrada() {
        return ubicacionEntrada;
    }

    public void setUbicacionEntrada(String ubicacionEntrada) {
        this.ubicacionEntrada = ubicacionEntrada;
    }

    public String getUbicacionSalida() {
        return ubicacionSalida;
    }

    public void setUbicacionSalida(String ubicacionSalida) {
        this.ubicacionSalida = ubicacionSalida;
    }

    public List<RegistroReporte> getReportes() {
        return reportes;
    }

    public void setReportes(List<RegistroReporte> reportes) {
        this.reportes = reportes;
    }
}
