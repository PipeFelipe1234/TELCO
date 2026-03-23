package com.practica.backend.dto;

import com.practica.backend.entity.RastreoZona;
import java.time.LocalDateTime;

/**
 * DTO para mostrar el estado de rastreo de un empleado
 */
public record RastreoZonaResponse(
        Long empleadoId,
        String empleadoNombre,
        String empleadoIdentificacion,
        Long zonaId,
        String zonaNombre,
        String estadoTiempo,
        Integer minutosEnZona,
        Double latitud,
        Double longitud,
        LocalDateTime timestampEntradaZona,
        LocalDateTime ultimaActualizacion) {
    public static RastreoZonaResponse fromEntity(RastreoZona rastreo, int minutosEnZona) {
        return new RastreoZonaResponse(
                rastreo.getEmpleado().getId(),
                rastreo.getEmpleado().getNombre(),
                rastreo.getEmpleado().getIdentificacion(),
                rastreo.getZonaActual() != null ? rastreo.getZonaActual().getId() : null,
                rastreo.getZonaActual() != null ? rastreo.getZonaActual().getNombre() : "Sin zona",
                rastreo.getEstadoTiempo().name(),
                minutosEnZona,
                rastreo.getUltimaLatitud(),
                rastreo.getUltimaLongitud(),
                rastreo.getTimestampEntradaZona(),
                rastreo.getUltimaActualizacion());
    }
}
