package com.practica.backend.dto;

public record DescansoActualResponse(
        Long descansoId,
        Long registroId,
        String horaInicio,
        Integer minutosTranscurridos,
        Integer limiteMinutos,
        Boolean excedioTiempoLimite) {
}
