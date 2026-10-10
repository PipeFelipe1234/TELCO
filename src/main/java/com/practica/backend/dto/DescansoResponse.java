package com.practica.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

// Los campos null se omiten: iniciar solo devuelve descansoId y horaInicio
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DescansoResponse(
        Long descansoId,
        String horaInicio,
        String horaFin,
        Integer minutosDuracion,
        Boolean excedioTiempoLimite) {
}
