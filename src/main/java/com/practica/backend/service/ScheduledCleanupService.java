package com.practica.backend.service;

import com.practica.backend.dto.CleanupInfoResponse;
import com.practica.backend.entity.Registro;
import com.practica.backend.repository.RegistroRepository;
import com.practica.backend.repository.SolicitudUbicacionRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.time.Duration;
import java.util.Locale;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Servicio que maneja la limpieza automática de registros y geolocalizaciones
 * antiguos.
 * 
 * REGLA: Después de 2 meses, se elimina automáticamente el mes más antiguo.
 * Ejemplo: Si estamos en Abril, se eliminan los registros de Febrero.
 * 
 * ADVERTENCIA: 4 días hábiles antes de la eliminación, se muestra un mensaje de
 * advertencia.
 */
@Service
public class ScheduledCleanupService {

    private static final Logger logger = LoggerFactory.getLogger(ScheduledCleanupService.class);
    private static final java.time.ZoneId ZONA_COLOMBIA = java.time.ZoneId.of("America/Bogota");
    private static final LocalTime HORA_CIERRE_AUTOMATICO = LocalTime.of(23, 55);

    private final RegistroRepository registroRepository;
    private final SolicitudUbicacionRepository solicitudRepository;
    private final NotificacionService notificacionService;
    private static final Locale LOCALE_ES = new Locale("es", "ES");

    // Días hábiles de advertencia antes de la eliminación
    private static final int DIAS_HABILES_ADVERTENCIA = 4;

    public ScheduledCleanupService(
            RegistroRepository registroRepository,
            SolicitudUbicacionRepository solicitudRepository,
            NotificacionService notificacionService) {
        this.registroRepository = registroRepository;
        this.solicitudRepository = solicitudRepository;
        this.notificacionService = notificacionService;
    }

    /**
     * Cierra automáticamente los turnos abiertos del día a las 23:55 hora Colombia.
     * Solo aplica a usuarios con rol USER.
     */
    @Scheduled(cron = "0 55 23 * * *", zone = "America/Bogota")
    @Transactional
    public void cerrarTurnosAutomaticamente() {
        LocalDate hoy = LocalDate.now(ZONA_COLOMBIA);
        List<Registro> registrosAbiertos = registroRepository.findByFechaAndHoraSalidaIsNull(hoy);

        if (registrosAbiertos.isEmpty()) {
            logger.info("🕘 Cierre automático 23:55: no hay registros abiertos para cerrar");
            return;
        }

        Set<Long> usuariosNotificados = new HashSet<>();
        int cerrados = 0;

        for (Registro registro : registrosAbiertos) {
            if (registro.getUsuario() == null || !"USER".equals(registro.getUsuario().getRol())) {
                continue;
            }

            LocalDateTime fechaHoraEntrada = LocalDateTime.of(registro.getFecha(), registro.getHoraEntrada());
            LocalDateTime fechaHoraCierre = LocalDateTime.of(hoy, HORA_CIERRE_AUTOMATICO);
            Duration duracion = Duration.between(fechaHoraEntrada, fechaHoraCierre);
            if (duracion.isNegative()) {
                duracion = Duration.ZERO;
            }

            registro.setHoraSalida(HORA_CIERRE_AUTOMATICO);
            registro.setHorasTrabajadas((int) duracion.toHours());
            registro.setMinutosTrabajados((int) duracion.toMinutes());
            registro.setUbicacionSalida("Salida automática 23:55");
            registroRepository.save(registro);

            cerrados++;
            logger.info("⏰ Cierre automático aplicado a {} (registro #{})", registro.getUsuario().getNombre(),
                    registro.getId());

            if (usuariosNotificados.add(registro.getUsuario().getId())) {
                notificacionService.enviarNotificacionAUsuario(
                        registro.getUsuario(),
                        "Salida marcada automáticamente",
                        "Salida marcada automaticamente. Te olvidaste de hacerlo",
                        Map.of(
                                "tipo", "SALIDA_AUTOMATICA",
                                "registroId", String.valueOf(registro.getId()),
                                "usuarioId", String.valueOf(registro.getUsuario().getId()),
                                "fecha", registro.getFecha().toString(),
                                "hora", HORA_CIERRE_AUTOMATICO.toString()));
            }
        }

        logger.info("🕘 Cierre automático 23:55 finalizado. Registros cerrados: {}", cerrados);
    }

    /**
     * Tarea programada que se ejecuta todos los días a las 00:05 AM
     * Verifica si hay registros y geolocalizaciones que deben ser eliminados
     */
    @Scheduled(cron = "0 5 0 * * *") // Todos los días a las 00:05
    public void ejecutarLimpiezaAutomatica() {
        LocalDate hoy = LocalDate.now();

        // Calcular el mes que debe ser eliminado (2 meses atrás)
        LocalDate mesAEliminar = hoy.minusMonths(2);
        int mes = mesAEliminar.getMonthValue();
        int anio = mesAEliminar.getYear();

        // Solo ejecutar el primer día del mes
        if (hoy.getDayOfMonth() != 1) {
            return;
        }

        String nombreMes = getNombreMes(mes);
        StringBuilder mensajeNotificacion = new StringBuilder();
        boolean hayEliminaciones = false;

        // Verificar y eliminar registros de asistencia
        long cantidadRegistros = registroRepository.countByMesYAnio(mes, anio);
        if (cantidadRegistros > 0) {
            int eliminadosRegistros = registroRepository.deleteByMesYAnio(mes, anio);
            mensajeNotificacion.append("Se eliminaron ").append(eliminadosRegistros)
                    .append(" registros de asistencia");
            hayEliminaciones = true;
        }

        // Verificar y eliminar geolocalizaciones
        long cantidadGeolocalizaciones = solicitudRepository.countByMesYAnio(mes, anio);
        if (cantidadGeolocalizaciones > 0) {
            int eliminadosGeo = solicitudRepository.deleteByMesYAnio(mes, anio);
            if (hayEliminaciones) {
                mensajeNotificacion.append(" y ");
            } else {
                mensajeNotificacion.append("Se eliminaron ");
            }
            mensajeNotificacion.append(eliminadosGeo).append(" geolocalizaciones");
            hayEliminaciones = true;
        }

        // Enviar notificación si hubo eliminaciones
        if (hayEliminaciones) {
            mensajeNotificacion.append(" del mes de ").append(nombreMes).append(" ").append(anio)
                    .append(". Recuerde exportar sus datos antes de que sean eliminados.");

            notificacionService.enviarNotificacionATodos(
                    "Limpieza automática completada",
                    mensajeNotificacion.toString());
        }
    }

    /**
     * Tarea programada para enviar advertencias
     * Se ejecuta todos los días a las 09:00 AM
     */
    @Scheduled(cron = "0 0 9 * * *") // Todos los días a las 09:00
    public void enviarAdvertenciasEliminacion() {
        CleanupInfoResponse info = obtenerInfoLimpieza();

        if (info.hayAdvertencia() && info.diasRestantes() != null && info.diasRestantes() <= DIAS_HABILES_ADVERTENCIA) {
            notificacionService.enviarNotificacionATodos(
                    "⚠️ Advertencia de eliminación",
                    info.mensaje());
        }
    }

    /**
     * Obtiene información sobre la próxima limpieza automática
     */
    public CleanupInfoResponse obtenerInfoLimpieza() {
        LocalDate hoy = LocalDate.now();

        // El primer día del próximo mes es cuando se ejecuta la eliminación
        LocalDate fechaEliminacion = hoy.withDayOfMonth(1).plusMonths(1);

        // El mes que será eliminado es 2 meses antes de la fecha de eliminación
        LocalDate mesAEliminar = fechaEliminacion.minusMonths(2);
        int mes = mesAEliminar.getMonthValue();
        int anio = mesAEliminar.getYear();

        // Contar registros que serán eliminados
        long cantidadRegistros = registroRepository.countByMesYAnio(mes, anio);

        // Si no hay registros del mes antiguo, no hay advertencia
        if (cantidadRegistros == 0) {
            return new CleanupInfoResponse(
                    false,
                    "No hay registros programados para eliminación automática.",
                    null,
                    null,
                    null,
                    null,
                    0L,
                    false);
        }

        // Calcular días hábiles restantes
        int diasHabiles = calcularDiasHabilesHasta(hoy, fechaEliminacion);

        String nombreMes = getNombreMes(mes);
        boolean hayAdvertencia = diasHabiles <= DIAS_HABILES_ADVERTENCIA;

        String mensaje;
        if (hayAdvertencia) {
            mensaje = "⚠️ Se eliminarán automáticamente los registros del Mes: " + nombreMes + " " + anio +
                    " en " + diasHabiles + " día(s) hábil(es). Por favor exporte los registros de ese mes.";
        } else {
            mensaje = "Los registros del mes de " + nombreMes + " " + anio +
                    " serán eliminados el "
                    + fechaEliminacion.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) +
                    ". Tiene " + diasHabiles + " días hábiles para exportarlos.";
        }

        return new CleanupInfoResponse(
                hayAdvertencia,
                mensaje,
                nombreMes,
                anio,
                diasHabiles,
                fechaEliminacion.toString(),
                cantidadRegistros,
                true);
    }

    /**
     * Obtiene información sobre qué meses están disponibles para exportar
     */
    public java.util.List<java.util.Map<String, Object>> obtenerMesesDisponibles() {
        java.util.List<java.util.Map<String, Object>> meses = new java.util.ArrayList<>();

        LocalDate fechaMasAntigua = registroRepository.findFechaMasAntigua();
        if (fechaMasAntigua == null) {
            return meses;
        }

        LocalDate hoy = LocalDate.now();
        LocalDate fecha = fechaMasAntigua.withDayOfMonth(1);

        while (!fecha.isAfter(hoy)) {
            int mes = fecha.getMonthValue();
            int anio = fecha.getYear();
            long cantidad = registroRepository.countByMesYAnio(mes, anio);

            if (cantidad > 0) {
                java.util.Map<String, Object> mesInfo = new java.util.HashMap<>();
                mesInfo.put("mes", mes);
                mesInfo.put("anio", anio);
                mesInfo.put("nombreMes", getNombreMes(mes));
                mesInfo.put("cantidadRegistros", cantidad);

                // Marcar si está próximo a eliminarse
                LocalDate fechaEliminacion = hoy.withDayOfMonth(1).plusMonths(1);
                LocalDate mesAEliminar = fechaEliminacion.minusMonths(2);
                boolean proximoAEliminar = (mes == mesAEliminar.getMonthValue() && anio == mesAEliminar.getYear());
                mesInfo.put("proximoAEliminar", proximoAEliminar);

                meses.add(mesInfo);
            }

            fecha = fecha.plusMonths(1);
        }

        return meses;
    }

    /**
     * Calcula los días hábiles entre dos fechas (excluyendo sábados y domingos)
     */
    private int calcularDiasHabilesHasta(LocalDate desde, LocalDate hasta) {
        if (desde.isAfter(hasta)) {
            return 0;
        }

        int diasHabiles = 0;
        LocalDate fecha = desde;

        while (fecha.isBefore(hasta)) {
            DayOfWeek dia = fecha.getDayOfWeek();
            if (dia != DayOfWeek.SATURDAY && dia != DayOfWeek.SUNDAY) {
                diasHabiles++;
            }
            fecha = fecha.plusDays(1);
        }

        return diasHabiles;
    }

    /**
     * Obtiene el nombre del mes en español
     */
    private String getNombreMes(int mes) {
        return LocalDate.of(2024, mes, 1)
                .getMonth()
                .getDisplayName(TextStyle.FULL, LOCALE_ES)
                .toUpperCase();
    }

    /**
     * Fuerza la eliminación de un mes específico (solo para ADMIN)
     * Útil para pruebas o eliminación manual
     */
    public int forzarEliminacionMes(int mes, int anio) {
        return registroRepository.deleteByMesYAnio(mes, anio);
    }
}
