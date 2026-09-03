package com.practica.backend.dto;

public record ReporteTurnoResponse(
        Long id,
        String fechaHora,
        String fechaHoraColombia,
        Double latitud,
        Double longitud,
        Double precisionMetros,
        String reporte,
        String picture,
        String ubicacion,
        Boolean esSalida,
        Long novedadId) {
}
