package com.practica.backend.dto;

import java.time.LocalDateTime;

public record ReporteTurnoResponse(
                Long id,
                LocalDateTime fechaHora,
                Double latitud,
                Double longitud,
                Double precisionMetros,
                String reporte,
                String picture,
                String ubicacion,
                Boolean esSalida,
                Long novedadId,
                String cliente, // Nombre completo del usuario visitado
                String ccCliente, // CC/Cédula del usuario visitado
                String estadoVisita) { // Estado de la visita (COBRADOR)
}
