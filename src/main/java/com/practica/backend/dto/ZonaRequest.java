package com.practica.backend.dto;

import java.util.List;

/**
 * DTO para crear o actualizar una zona
 */
public record ZonaRequest(
                String nombre,
                String nodo,
                List<CoordenadaDTO> coordenadas,
                String color) {
        public record CoordenadaDTO(
                        Double lat,
                        Double lng) {
        }
}
