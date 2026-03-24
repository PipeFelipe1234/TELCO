package com.practica.backend.dto;

import java.util.List;

/**
 * DTO para representar una zona con sus coordenadas parseadas
 */
public record ZonaResponse(
        Long id,
        String nombre,
        List<CoordenadaDTO> coordenadas,
        String color,
        Boolean activa) {
    public record CoordenadaDTO(
            Double lat,
            Double lng) {
    }
}
