package com.practica.backend.dto;

public record DescansoHistorialResponse(
        Long descansoId,
        Long registroId,
        String identificacion,
        String nombre,
        String cargo,
        String fecha,
        String horaInicio,
        String horaFin,
        Integer minutosDuracion,
        Boolean enCurso,
        Boolean excedioTiempoLimite,
        Boolean cerradoAutomaticamente) {
}
