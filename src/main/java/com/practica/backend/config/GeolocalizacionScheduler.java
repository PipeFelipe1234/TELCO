package com.practica.backend.config;

import com.practica.backend.service.GeolocalizacionService;
import com.practica.backend.service.RastreoZonaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler para tareas programadas del sistema
 * - Expirar solicitudes de ubicación que no fueron respondidas en 90 segundos
 * - 🤖 Rastreo automático en tiempo real cada 1 minuto a empleados en turno
 */
@Component
@EnableScheduling
public class GeolocalizacionScheduler {

    private static final Logger logger = LoggerFactory.getLogger(GeolocalizacionScheduler.class);

    private final GeolocalizacionService geolocalizacionService;
    private final RastreoZonaService rastreoZonaService;

    public GeolocalizacionScheduler(GeolocalizacionService geolocalizacionService,
            RastreoZonaService rastreoZonaService) {
        this.geolocalizacionService = geolocalizacionService;
        this.rastreoZonaService = rastreoZonaService;
    }

    /**
     * Ejecuta cada 30 segundos para revisar y expirar solicitudes pendientes
     * que superen los 90 segundos sin respuesta.
     * 
     * El admin recibirá notificación cuando una solicitud expire.
     */
    @Scheduled(fixedRate = 30000) // cada 30 segundos
    public void revisarSolicitudesExpiradas() {
        try {
            int expiradas = geolocalizacionService.expirarSolicitudesAntiguas();
            if (expiradas > 0) {
                logger.info("⏰ Scheduler: {} solicitudes expiradas", expiradas);
            }
        } catch (Exception e) {
            logger.error("❌ Error en scheduler de expiración: {}", e.getMessage());
        }
    }

    /**
     * 🤖 RASTREO AUTOMÁTICO EN TIEMPO REAL
     * 
     * Ejecuta cada 1 minuto (60000 ms) para enviar solicitudes de ubicación
     * automáticas a todos los empleados que están EN TURNO (marcaron entrada
     * pero no han marcado salida).
     * 
     * Esto permite monitoreo en tiempo real de la ubicación de los empleados.
     * 
     * No genera logs si no hay empleados en turno (para evitar spam en logs).
     */
    @Scheduled(fixedRate = 60000) // cada 1 minuto
    public void rastreoAutomaticoTiempoReal() {
        try {
            int huerfanos = rastreoZonaService.limpiarRastreosHuerfanos();
            if (huerfanos > 0) {
                logger.info("🧹 Scheduler: {} rastreos huérfanos eliminados", huerfanos);
            }

            int enviadas = geolocalizacionService.enviarSolicitudesAutomaticas();
            // Solo loguear si hubo solicitudes enviadas
            if (enviadas > 0) {
                logger.info("🤖 Scheduler: {} solicitudes automáticas enviadas", enviadas);
            }
        } catch (Exception e) {
            logger.error("❌ Error en scheduler de rastreo automático: {}", e.getMessage());
        }
    }

    /**
     * Limpia solicitudes automáticas antiguas cada 1 hora.
     * Mantiene la tabla solicitudes_ubicacion ligera eliminando solo las de rastreo
     * automático (cada 60 segundos).
     */
    @Scheduled(fixedRate = 3600000) // cada 1 hora
    public void limpiarSolicitudesAutomaticasAntiguas() {
        try {
            int eliminadas = geolocalizacionService.eliminarSolicitudesAutomaticasAntiguas();
            if (eliminadas > 0) {
                logger.info("🧹 Scheduler diario: {} solicitudes automáticas eliminadas", eliminadas);
            }
        } catch (Exception e) {
            logger.error("❌ Error en limpieza diaria de solicitudes automáticas: {}", e.getMessage());
        }
    }
}
