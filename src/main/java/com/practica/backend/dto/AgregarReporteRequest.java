package com.practica.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AgregarReporteRequest(
        Double latitud,
        Double longitud,
        Double precisionMetros,
        String reporte,
        String picture,
        String ubicacion,
        @JsonProperty("fechaCreacion") String fechaCreacion,
        Integer novedadId) {
}
Long novedadId)
{
