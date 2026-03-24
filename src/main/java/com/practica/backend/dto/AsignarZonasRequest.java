package com.practica.backend.dto;

import java.util.List;

/**
 * DTO para asignar zonas a un usuario
 */
public record AsignarZonasRequest(
        List<Long> zonaIds) {
}
