package com.practica.backend.service;

import com.practica.backend.dto.DashboardResponse;
import com.practica.backend.dto.DashboardResponse.*;
import com.practica.backend.entity.RastreoZona;
import com.practica.backend.entity.Registro;
import com.practica.backend.entity.SolicitudUbicacion;
import com.practica.backend.repository.RegistroReporteRepository;
import com.practica.backend.repository.RegistroRepository;
import com.practica.backend.repository.RastreoZonaRepository;
import com.practica.backend.repository.SolicitudUbicacionRepository;
import com.practica.backend.repository.UsuarioRepository;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private static final ZoneId ZONA_COLOMBIA = ZoneId.of("America/Bogota");

    private final UsuarioRepository usuarioRepository;
    private final RegistroRepository registroRepository;
    private final RegistroReporteRepository registroReporteRepository;
    private final RastreoZonaRepository rastreoZonaRepository;
    private final SolicitudUbicacionRepository solicitudUbicacionRepository;

    public DashboardService(
            UsuarioRepository usuarioRepository,
            RegistroRepository registroRepository,
            RegistroReporteRepository registroReporteRepository,
            RastreoZonaRepository rastreoZonaRepository,
            SolicitudUbicacionRepository solicitudUbicacionRepository) {
        this.usuarioRepository = usuarioRepository;
        this.registroRepository = registroRepository;
        this.registroReporteRepository = registroReporteRepository;
        this.rastreoZonaRepository = rastreoZonaRepository;
        this.solicitudUbicacionRepository = solicitudUbicacionRepository;
    }

    public DashboardResponse obtenerDashboard(String periodo, LocalDate fechaInicio, LocalDate fechaFin) {
        LocalDate hoy = LocalDate.now(ZONA_COLOMBIA);

        LocalDate inicio;
        LocalDate fin;

        switch (periodo != null ? periodo.toUpperCase() : "HOY") {
            case "SEMANA" -> {
                inicio = hoy.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                fin = hoy;
            }
            case "MES" -> {
                inicio = hoy.with(TemporalAdjusters.firstDayOfMonth());
                fin = hoy;
            }
            case "RANGO" -> {
                inicio = fechaInicio != null ? fechaInicio : hoy;
                fin = fechaFin != null ? fechaFin : hoy;
            }
            default -> { // HOY
                inicio = hoy;
                fin = hoy;
            }
        }

        PeriodoInfo periodoInfo = new PeriodoInfo(periodo != null ? periodo.toUpperCase() : "HOY", inicio, fin);

        TiempoRealMetrics tiempoReal = buildTiempoReal(hoy);
        ProductividadMetrics productividad = buildProductividad(inicio, fin);
        KpisMetrics kpis = buildKpis(productividad, inicio, fin);
        SolicitudesMetrics solicitudes = buildSolicitudes(inicio, fin);

        return new DashboardResponse(periodoInfo, tiempoReal, productividad, kpis, solicitudes);
    }

    // ─────────────────────────────────────────
    // TIEMPO REAL
    // ─────────────────────────────────────────

    private TiempoRealMetrics buildTiempoReal(LocalDate hoy) {
        int totalEquipo = usuarioRepository.findByRol("USER").size();

        List<Registro> registrosHoy = registroRepository.findByFechaRange(hoy, hoy);
        int enTurnoAhora = (int) registrosHoy.stream().filter(r -> r.getHoraSalida() == null).count();
        int yaSalieronHoy = (int) registrosHoy.stream().filter(r -> r.getHoraSalida() != null).count();
        int sinEntrarHoy = Math.max(0, totalEquipo - enTurnoAhora - yaSalieronHoy);

        List<RastreoZona> rastreos = rastreoZonaRepository.findAll();

        // Técnicos/empleados con estado preocupante
        List<TecnicoPreocupante> preocupantes = rastreos.stream()
                .filter(r -> r.getEstadoTiempo() != null
                        && r.getEstadoTiempo().name().equals("PREOCUPANTE"))
                .map(r -> {
                    int minutos = 0;
                    if (r.getTimestampEntradaResidencia() != null) {
                        minutos = (int) ChronoUnit.MINUTES.between(
                                r.getTimestampEntradaResidencia(),
                                LocalDateTime.now(ZONA_COLOMBIA));
                    }
                    String zona = r.getZonaActual() != null ? r.getZonaActual().getNombre() : null;
                    return new TecnicoPreocupante(
                            r.getEmpleado().getId(),
                            r.getEmpleado().getNombre(),
                            r.getEmpleado().getCargo(),
                            minutos,
                            r.getEstadoTiempo().name(),
                            zona,
                            r.getUltimaLatitud(),
                            r.getUltimaLongitud());
                })
                .toList();

        // Distribución por zona (empleados con zona actual asignada)
        Map<String, Long> porZona = rastreos.stream()
                .filter(r -> r.getZonaActual() != null)
                .collect(Collectors.groupingBy(
                        r -> r.getZonaActual().getNombre(),
                        Collectors.counting()));

        List<EmpleadosPorZona> distribucion = porZona.entrySet().stream()
                .map(e -> new EmpleadosPorZona(e.getKey(), e.getValue().intValue()))
                .toList();

        return new TiempoRealMetrics(totalEquipo, enTurnoAhora, yaSalieronHoy, sinEntrarHoy,
                preocupantes, distribucion);
    }

    // ─────────────────────────────────────────
    // PRODUCTIVIDAD
    // ─────────────────────────────────────────

    private ProductividadMetrics buildProductividad(LocalDate inicio, LocalDate fin) {
        LocalDateTime inicioDateTime = inicio.atStartOfDay();
        LocalDateTime finDateTime = fin.atTime(23, 59, 59);

        // Ranking de reportes por usuario
        List<Object[]> reportesRaw = registroReporteRepository
                .countReportesByUsuarioInRange(inicioDateTime, finDateTime);
        List<RankingReportes> ranking = reportesRaw.stream()
                .map(row -> new RankingReportes(
                        (Long) row[0],
                        (String) row[1],
                        (Long) row[2]))
                .toList();

        // Horas trabajadas por usuario
        List<Object[]> horasRaw = registroRepository.sumMinutosByUsuarioInRange(inicio, fin);
        List<HorasTrabajadas> horas = horasRaw.stream()
                .map(row -> {
                    long minutos = row[2] != null ? ((Number) row[2]).longValue() : 0L;
                    long dias = row[3] != null ? ((Number) row[3]).longValue() : 0L;
                    return new HorasTrabajadas(
                            (Long) row[0],
                            (String) row[1],
                            minutos,
                            Math.round((minutos / 60.0) * 100.0) / 100.0,
                            dias);
                })
                .toList();

        // Asistencia diaria (registros por día)
        List<Object[]> asistenciaRaw = registroRepository.countRegistrosByFechaInRange(inicio, fin);
        List<AsistenciaDiaria> asistencia = asistenciaRaw.stream()
                .map(row -> new AsistenciaDiaria(
                        (LocalDate) row[0],
                        ((Number) row[1]).longValue()))
                .toList();

        // Puntualidad (turnos completados vs total)
        List<Registro> registrosPeriodo = registroRepository.findByFechaRange(inicio, fin);
        long totalTurnos = registrosPeriodo.size();
        long turnosConSalida = registrosPeriodo.stream()
                .filter(r -> r.getHoraSalida() != null).count();
        double pctCompletados = totalTurnos > 0
                ? Math.round((turnosConSalida * 100.0 / totalTurnos) * 100.0) / 100.0
                : 0.0;
        Puntualidad puntualidad = new Puntualidad(totalTurnos, turnosConSalida, pctCompletados);

        return new ProductividadMetrics(ranking, horas, asistencia, puntualidad);
    }

    // ─────────────────────────────────────────
    // KPIs (derivados de productividad sin queries extra)
    // ─────────────────────────────────────────

    private KpisMetrics buildKpis(ProductividadMetrics prod, LocalDate inicio, LocalDate fin) {
        long totalReportes = prod.rankingReportes().stream()
                .mapToLong(RankingReportes::totalReportes).sum();

        long totalTurnos = prod.puntualidad().totalTurnos();
        long diasPeriodo = ChronoUnit.DAYS.between(inicio, fin) + 1;

        double promedioHoras = 0.0;
        if (!prod.horasTrabajadas().isEmpty()) {
            long totalMinutos = prod.horasTrabajadas().stream()
                    .mapToLong(HorasTrabajadas::totalMinutos).sum();
            long totalDias = prod.horasTrabajadas().stream()
                    .mapToLong(HorasTrabajadas::diasTrabajados).sum();
            promedioHoras = totalDias > 0
                    ? Math.round((totalMinutos / 60.0 / totalDias) * 100.0) / 100.0
                    : 0.0;
        }

        // Porcentaje de asistencia: días con al menos un registro vs días del periodo
        long diasConAsistencia = prod.asistenciaDiaria().stream()
                .filter(a -> a.cantidad() > 0).count();
        double pctAsistencia = diasPeriodo > 0
                ? Math.round((diasConAsistencia * 100.0 / diasPeriodo) * 100.0) / 100.0
                : 0.0;

        // Top reportador
        KpiTop topReportador = prod.rankingReportes().isEmpty() ? null
                : new KpiTop(
                        prod.rankingReportes().get(0).usuarioId(),
                        prod.rankingReportes().get(0).nombre(),
                        prod.rankingReportes().get(0).totalReportes() + " reportes");

        // Top horas
        KpiTop topHoras = prod.horasTrabajadas().isEmpty() ? null
                : new KpiTop(
                        prod.horasTrabajadas().get(0).usuarioId(),
                        prod.horasTrabajadas().get(0).nombre(),
                        prod.horasTrabajadas().get(0).totalHoras() + " h");

        return new KpisMetrics(totalReportes, totalTurnos, promedioHoras, pctAsistencia,
                topReportador, topHoras);
    }

    // ─────────────────────────────────────────
    // SOLICITUDES DE UBICACIÓN
    // ─────────────────────────────────────────

    private SolicitudesMetrics buildSolicitudes(LocalDate inicio, LocalDate fin) {
        List<SolicitudUbicacion> solicitudes = solicitudUbicacionRepository.findByFechaRange(inicio, fin);

        long total = solicitudes.size();
        long respondidas = solicitudes.stream().filter(s -> "RESPONDIDA".equals(s.getEstado())).count();
        long expiradas = solicitudes.stream().filter(s -> "EXPIRADA".equals(s.getEstado())).count();
        long errores = solicitudes.stream().filter(s -> "ERROR".equals(s.getEstado())).count();

        double pctRespuesta = total > 0
                ? Math.round((respondidas * 100.0 / total) * 100.0) / 100.0
                : 0.0;

        // Tiempo promedio de respuesta en minutos (solo respondidas con ambas fechas)
        double tiempoPromedio = solicitudes.stream()
                .filter(s -> "RESPONDIDA".equals(s.getEstado())
                        && s.getFechaSolicitud() != null
                        && s.getFechaRespuesta() != null)
                .mapToLong(s -> ChronoUnit.MINUTES.between(s.getFechaSolicitud(), s.getFechaRespuesta()))
                .average()
                .orElse(0.0);
        tiempoPromedio = Math.round(tiempoPromedio * 100.0) / 100.0;

        // Técnicos con fallos (solicitudes expiradas o con error agrupadas por
        // empleado)
        Map<Long, TecnicoFallosAcc> fallosMap = new LinkedHashMap<>();
        for (SolicitudUbicacion s : solicitudes) {
            Long uid = s.getEmpleado().getId();
            fallosMap.putIfAbsent(uid, new TecnicoFallosAcc(
                    uid, s.getEmpleado().getNombre(), 0L, 0L));
            TecnicoFallosAcc acc = fallosMap.get(uid);
            acc.total++;
            if ("EXPIRADA".equals(s.getEstado()) || "ERROR".equals(s.getEstado())) {
                acc.fallos++;
            }
        }
        List<TecnicoFallos> tecnicosFallos = fallosMap.values().stream()
                .filter(a -> a.fallos > 0)
                .map(a -> new TecnicoFallos(a.id, a.nombre, a.total, a.fallos))
                .toList();

        // Solicitudes por día
        Map<LocalDate, long[]> porDia = new LinkedHashMap<>();
        for (SolicitudUbicacion s : solicitudes) {
            if (s.getFechaSolicitud() == null)
                continue;
            LocalDate fecha = s.getFechaSolicitud().toLocalDate();
            porDia.putIfAbsent(fecha, new long[] { 0L, 0L });
            porDia.get(fecha)[0]++;
            if ("RESPONDIDA".equals(s.getEstado())) {
                porDia.get(fecha)[1]++;
            }
        }
        List<SolicitudDiaria> porDiaList = porDia.entrySet().stream()
                .map(e -> new SolicitudDiaria(e.getKey(), e.getValue()[0], e.getValue()[1]))
                .sorted((a, b) -> a.fecha().compareTo(b.fecha()))
                .toList();

        return new SolicitudesMetrics(total, respondidas, expiradas, errores, pctRespuesta,
                tiempoPromedio, tecnicosFallos, porDiaList);
    }

    /** Acumulador mutable interno para cálculo de fallos por técnico */
    private static class TecnicoFallosAcc {
        Long id;
        String nombre;
        long total;
        long fallos;

        TecnicoFallosAcc(Long id, String nombre, long total, long fallos) {
            this.id = id;
            this.nombre = nombre;
            this.total = total;
            this.fallos = fallos;
        }
    }
}
