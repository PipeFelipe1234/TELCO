package com.practica.backend.dto;

public record ResumenAsistenciaResponse(
        Integer totalPersonal,
        Integer noIniciados,
        Integer enTurno,
        Integer finalizados) {
}
