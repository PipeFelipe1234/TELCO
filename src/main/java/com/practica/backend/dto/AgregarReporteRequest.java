package com.practica.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AgregarReporteRequest(
                Double latitud,
                Double longitud,
                Double precisionMetros,
                String reporte,
                String picture,
                String ubicacion,
                @JsonProperty("fechaCreacion") String fechaCreacion,
                Long novedadId,
                String cliente, // Nombre completo del usuario visitado (OBLIGATORIO para COBRADOR y TEC)
                String ccCliente, // CC/Cédula del usuario visitado (OBLIGATORIO para COBRADOR y TEC)
                String estadoVisita) { // Estado de la visita (OBLIGATORIO solo para COBRADOR)
}
