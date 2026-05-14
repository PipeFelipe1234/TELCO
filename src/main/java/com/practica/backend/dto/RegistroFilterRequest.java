package com.practica.backend.dto;

import java.time.LocalDate;

public class RegistroFilterRequest {

    private LocalDate fecha;
    private String identificacion;
    private String nombres;
    private Integer novedadId;

    public RegistroFilterRequest() {
    }

    public RegistroFilterRequest(LocalDate fecha, String identificacion, String nombres) {
        this.fecha = fecha;
        this.identificacion = identificacion;
        this.nombres = nombres;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }

    public String getIdentificacion() {
        return identificacion;
    }

    public void setIdentificacion(String identificacion) {
        this.identificacion = identificacion;
    }

    public String getNombres() {
        return nombres;
    }

    public void setNombres(String nombres) {
        this.nombres = nombres;
    }

    public Integer getNovedadId() {
        return novedadId;
    }

    public void setNovedadId(Integer novedadId) {
        this.novedadId = novedadId;
    }
}
