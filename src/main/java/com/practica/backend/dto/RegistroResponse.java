package com.practica.backend.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record RegistroResponse(
        Long id,
        LocalDate fecha,
        LocalTime horaEntrada,
        LocalTime horaSalida,
        Double latitud,
        Double longitud,
        Double precisionMetros,
        Double latitudCheckin,
        Double longitudCheckin,
        Double precisionMetrosCheckin,
        String reporte,
        String picture,
        String identificacion,
        String nombre,
        String foto,
        String telefono,
        String cargo,
        Integer horasTrabajadas, // Total de horas trabajadas (entero)
        Integer minutosTrabajados, // Total de minutos trabajados
        Boolean enCurso, // true si aún no ha marcado salida
        String ubicacionEntrada, // Dirección de texto del check-in
        String ubicacionSalida, // Dirección de texto del check-out
        List<ReporteTurnoResponse> reportes // Reportes intermedios y final del turno
) {
}