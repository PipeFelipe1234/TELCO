package com.practica.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PersonalFinalizado(
        Long registroId,
        String identificacion,
        String nombre,
        String cargo,
        String horaEntrada,
        String horaSalida,
        String ciudad,
        String picture,
        Integer cantidadReportes,
        @JsonProperty("isInBreak") boolean isInBreak,
        String breakStartTime,
        boolean breakExceeded,
        int cantidadDescansosHoy) {
}
