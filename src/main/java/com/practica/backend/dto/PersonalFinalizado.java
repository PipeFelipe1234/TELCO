package com.practica.backend.dto;

public record PersonalFinalizado(
        Long registroId,
        String identificacion,
        String nombre,
        String cargo,
        String horaEntrada,
        String horaSalida,
        String ciudad) {
}
