package com.practica.backend.dto;

public record PersonalEnTurno(
                Long registroId,
                String identificacion,
                String nombre,
                String cargo,
                String horaEntrada,
                String ciudad,
                String picture,
                Integer cantidadReportes) {
}
