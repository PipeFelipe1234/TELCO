package com.practica.backend.dto;

import com.practica.backend.entity.RastreoZona;
import java.time.LocalDateTime;

/**
 * DTO para mostrar el estado de rastreo de un empleado.
 * 
 * - ZONA: Indica si está dentro o fuera de la zona asignada
 * - RESIDENCIA: El estado (BIEN/NORMAL/PREOCUPANTE) se basa en el tiempo en la
 * misma residencia
 * - minutosEnResidencia: Tiempo que lleva en el mismo punto/residencia (se
 * resetea al moverse)
 */
public record RastreoZonaResponse(
        Long empleadoId,
        String empleadoNombre,
        String empleadoIdentificacion,
        Long zonaId,
        String zonaNombre,
        boolean dentroDeZona,
        String estadoTiempo,
        Integer minutosEnResidencia,
        Double latitud,
        Double longitud,
        LocalDateTime timestampEntradaResidencia,
        LocalDateTime ultimaActualizacion) {
    public static RastreoZonaResponse fromEntity(RastreoZona rastreo, int minutosEnResidencia) {
        return new RastreoZonaResponse(
                rastreo.getEmpleado().getId(),
                rastreo.getEmpleado().getNombre(),
                rastreo.getEmpleado().getIdentificacion(),
                rastreo.getZonaActual() != null ? rastreo.getZonaActual().getId() : null,
                rastreo.getZonaActual() != null ? rastreo.getZonaActual().getNombre() : "Fuera de zona",
                rastreo.getZonaActual() != null, // dentroDeZona
                rastreo.getEstadoTiempo().name(),
                minutosEnResidencia,
                rastreo.getUltimaLatitud(),
                rastreo.getUltimaLongitud(),
                rastreo.getTimestampEntradaResidencia(),
                rastreo.getUltimaActualizacion());
    }
}
