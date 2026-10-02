package com.practica.backend.dto;

import java.time.LocalDate;

public record AsistenciaResponse(
                LocalDate fecha,
                ResumenAsistenciaResponse resumen,
                DetallesAsistenciaResponse detalles) {
}
