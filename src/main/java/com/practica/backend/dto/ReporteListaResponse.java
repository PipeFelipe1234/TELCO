package com.practica.backend.dto;

import java.time.LocalDateTime;

public record ReporteListaResponse(
        Long id, // ID del reporte
        String tipoUsuario, // "USER_TEC" o "USER_COO" (del campo cargo)
        Long usuarioId, // ID del colaborador
        String usuarioNombre, // Nombre del colaborador
        String usuarioIdentificacion, // Cédula del colaborador
        String reporte, // Descripción del reporte
        String picture, // URL de imagen
        String ubicacion, // Dirección visitada
        LocalDateTime fechaCreacion, // Fecha del reporte
        Long novedadId, // ID de novedad
        String cliente, // Nombre cliente visitado
        String ccCliente, // Cédula cliente visitado
        String estadoVisita, // PAGO_COMPLETO, NO_PAGO, etc.
        Boolean esSalida // Si es reporte de salida
) {
}
