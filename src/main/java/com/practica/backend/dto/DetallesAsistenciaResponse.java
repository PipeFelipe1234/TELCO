package com.practica.backend.dto;

import java.util.List;

public record DetallesAsistenciaResponse(
        List<PersonalNoIniciado> noIniciados,
        List<PersonalEnTurno> enTurno,
        List<PersonalFinalizado> finalizados) {
}
