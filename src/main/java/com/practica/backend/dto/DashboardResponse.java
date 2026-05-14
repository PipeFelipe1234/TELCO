package com.practica.backend.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * DTO de respuesta para el endpoint GET /api/admin/dashboard.
 * Agrupa métricas en cuatro secciones: tiempo real, productividad, KPIs y
 * solicitudes.
 */
public record DashboardResponse(
        PeriodoInfo periodo,
        TiempoRealMetrics tiempoReal,
        ProductividadMetrics productividad,
        KpisMetrics kpis,
        SolicitudesMetrics solicitudes) {

    // ─────────────────────────────────────────
    // PERIODO
    // ─────────────────────────────────────────

    public record PeriodoInfo(
            String tipo, // HOY | SEMANA | MES | RANGO
            LocalDate inicio,
            LocalDate fin) {
    }

    // ─────────────────────────────────────────
    // TIEMPO REAL
    // ─────────────────────────────────────────

    public record TiempoRealMetrics(
            int totalEquipo,
            int enTurnoAhora,
            int yaSalieronHoy,
            int sinEntrarHoy,
            List<TecnicoPreocupante> tecnicosPreocupantes,
            List<EmpleadosPorZona> distribucionPorZona) {
    }

    public record TecnicoPreocupante(
            Long usuarioId,
            String nombre,
            String cargo,
            int minutosEnResidencia,
            String estadoTiempo, // BIEN | NORMAL | PREOCUPANTE
            String zonaActual,
            Double latitud,
            Double longitud) {
    }

    public record EmpleadosPorZona(
            String zona,
            int cantidad) {
    }

    // ─────────────────────────────────────────
    // PRODUCTIVIDAD
    // ─────────────────────────────────────────

    public record ProductividadMetrics(
            List<RankingReportes> rankingReportes,
            List<HorasTrabajadas> horasTrabajadas,
            List<AsistenciaDiaria> asistenciaDiaria,
            Puntualidad puntualidad) {
    }

    public record RankingReportes(
            Long usuarioId,
            String nombre,
            long totalReportes) {
    }

    public record HorasTrabajadas(
            Long usuarioId,
            String nombre,
            long totalMinutos,
            double totalHoras,
            long diasTrabajados) {
    }

    public record AsistenciaDiaria(
            LocalDate fecha,
            long cantidad) {
    }

    public record Puntualidad(
            long totalTurnos,
            long turnosConHoraSalida,
            double porcentajeCompletados) {
    }

    // ─────────────────────────────────────────
    // KPIs
    // ─────────────────────────────────────────

    public record KpisMetrics(
            long totalReportesEnPeriodo,
            long totalTurnosEnPeriodo,
            double promedioHorasPorTurno,
            double porcentajeAsistencia,
            KpiTop topReportador,
            KpiTop topHoras) {
    }

    public record KpiTop(
            Long usuarioId,
            String nombre,
            String valor) {
    }

    // ─────────────────────────────────────────
    // SOLICITUDES DE UBICACIÓN
    // ─────────────────────────────────────────

    public record SolicitudesMetrics(
            long totalSolicitudes,
            long respondidas,
            long expiradas,
            long errores,
            double porcentajeRespuesta,
            double tiempoPromedioRespuestaMinutos,
            List<TecnicoFallos> tecnicosFallos,
            List<SolicitudDiaria> solicitudesPorDia) {
    }

    public record TecnicoFallos(
            Long usuarioId,
            String nombre,
            long totalSolicitudes,
            long fallos) {
    }

    public record SolicitudDiaria(
            LocalDate fecha,
            long total,
            long respondidas) {
    }
}
