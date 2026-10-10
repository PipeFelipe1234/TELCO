package com.practica.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PersonalEnTurno(
        Long registroId,
        String identificacion,
        String nombre,
        String cargo,
        String horaEntrada,
        String ciudad,
        String picture,
        Integer cantidadReportes,
        @JsonProperty("isInBreak") boolean isInBreak,
        String breakStartTime,
        boolean breakExceeded,
        int cantidadDescansosHoy) {
}
