package com.practica.backend.service;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.practica.backend.dto.RastreoZonaResponse;
import com.practica.backend.entity.RastreoZona;
import com.practica.backend.entity.RastreoZona.EstadoTiempo;
import com.practica.backend.entity.TokenDispositivo;
import com.practica.backend.entity.Usuario;
import com.practica.backend.entity.Zona;
import com.practica.backend.repository.RastreoZonaRepository;
import com.practica.backend.repository.TokenDispositivoRepository;
import com.practica.backend.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Servicio para rastrear empleados con dos niveles:
 * 
 * 1. ZONA: Detecta si el empleado está dentro o fuera de su zona asignada.
 * - Envía notificación cuando SALE de la zona.
 * 
 * 2. RESIDENCIA/PUNTO: Detecta si el empleado está mucho tiempo en el mismo
 * punto.
 * - Estados: BIEN (0-3 min), NORMAL (3-10 min), PREOCUPANTE (10+ min)
 * - Envía notificación cuando llega a PREOCUPANTE en una residencia.
 * - Los minutos se resetean cuando cambia de residencia.
 */
@Service
public class RastreoZonaService {

    private static final Logger logger = LoggerFactory.getLogger(RastreoZonaService.class);
    private static final ZoneId ZONA_COLOMBIA = ZoneId.of("America/Bogota");

    // Umbrales de tiempo en minutos (para residencia)
    private static final int UMBRAL_NORMAL = 3; // Después de 3 min -> NORMAL
    private static final int UMBRAL_PREOCUPANTE = 10; // Después de 10 min -> PREOCUPANTE

    // Radio en metros para considerar que es la misma residencia
    private static final double RADIO_RESIDENCIA_METROS = 50.0;

    private final RastreoZonaRepository rastreoRepository;
    private final ZonaService zonaService;
    private final UsuarioRepository usuarioRepository;
    private final TokenDispositivoRepository tokenDispositivoRepository;

    public RastreoZonaService(
            RastreoZonaRepository rastreoRepository,
            ZonaService zonaService,
            UsuarioRepository usuarioRepository,
            TokenDispositivoRepository tokenDispositivoRepository) {
        this.rastreoRepository = rastreoRepository;
        this.zonaService = zonaService;
        this.usuarioRepository = usuarioRepository;
        this.tokenDispositivoRepository = tokenDispositivoRepository;
    }

    /**
     * Procesa una nueva ubicación de un empleado.
     * 
     * Dos niveles de rastreo:
     * 1. ZONA: Detecta si salió de la zona → envía notificación
     * 2. RESIDENCIA: Detecta tiempo en el mismo punto → estados
     * BIEN/NORMAL/PREOCUPANTE
     */
    @Transactional
    public RastreoZonaResponse procesarUbicacion(Usuario empleado, double latitud, double longitud) {
        logger.info("📍 Procesando ubicación de {} en ({}, {})", empleado.getNombre(), latitud, longitud);

        // Buscar o crear el rastreo del empleado
        RastreoZona rastreo = rastreoRepository.findByEmpleado(empleado)
                .orElseGet(() -> {
                    RastreoZona nuevo = new RastreoZona(empleado);
                    return rastreoRepository.save(nuevo);
                });

        // Actualizar última ubicación
        rastreo.setUltimaLatitud(latitud);
        rastreo.setUltimaLongitud(longitud);

        LocalDateTime ahora = LocalDateTime.now(ZONA_COLOMBIA);

        // ==========================================
        // 1. RASTREO DE ZONA (detectar si salió)
        // ==========================================
        Zona zonaActual = zonaService.encontrarZonaPorPunto(latitud, longitud);
        procesarRastreoZona(rastreo, zonaActual, empleado, ahora);

        // ==========================================
        // 2. RASTREO DE RESIDENCIA (tiempo en punto)
        // ==========================================
        int minutosEnResidencia = procesarRastreoResidencia(rastreo, latitud, longitud, empleado, ahora);

        rastreo = rastreoRepository.save(rastreo);

        return RastreoZonaResponse.fromEntity(rastreo, minutosEnResidencia);
    }

    /**
     * Procesa el rastreo de zona: detecta si el empleado salió de su zona
     */
    private void procesarRastreoZona(RastreoZona rastreo, Zona zonaActual, Usuario empleado, LocalDateTime ahora) {
        Zona zonaAnterior = rastreo.getZonaActual();

        if (zonaActual == null) {
            // No está en ninguna zona
            if (zonaAnterior != null && !Boolean.TRUE.equals(rastreo.getNotificacionSalioZonaEnviada())) {
                // Acaba de salir de la zona - enviar notificación
                enviarNotificacionSalioDeZona(empleado, zonaAnterior);
                rastreo.setNotificacionSalioZonaEnviada(true);
            }
            rastreo.setZonaActual(null);
            rastreo.setTimestampEntradaZona(null);
        } else {
            // Está en una zona
            if (zonaAnterior == null || !zonaAnterior.getId().equals(zonaActual.getId())) {
                // Entró a una nueva zona
                logger.info("👤 {} entró a la zona {}", empleado.getNombre(), zonaActual.getNombre());
                rastreo.setZonaActual(zonaActual);
                rastreo.setTimestampEntradaZona(ahora);
                rastreo.setNotificacionSalioZonaEnviada(false); // Resetear flag
            }
        }
    }

    /**
     * Procesa el rastreo de residencia: detecta tiempo en el mismo punto
     * Retorna los minutos en la residencia actual
     */
    private int procesarRastreoResidencia(RastreoZona rastreo, double latitud, double longitud,
            Usuario empleado, LocalDateTime ahora) {
        int minutosEnResidencia = 0;

        // Verificar si es la misma residencia
        boolean mismaResidencia = esMismaResidencia(
                rastreo.getLatitudResidencia(), rastreo.getLongitudResidencia(),
                latitud, longitud);

        if (!mismaResidencia) {
            // Cambió de residencia - resetear todo
            logger.info("🏠 {} se movió a nueva residencia", empleado.getNombre());
            rastreo.setLatitudResidencia(latitud);
            rastreo.setLongitudResidencia(longitud);
            rastreo.setTimestampEntradaResidencia(ahora);
            rastreo.setEstadoTiempo(EstadoTiempo.BIEN);
            rastreo.setNotificacionPreocupanteEnviada(false);
        } else {
            // Sigue en la misma residencia - calcular tiempo
            if (rastreo.getTimestampEntradaResidencia() != null) {
                minutosEnResidencia = (int) ChronoUnit.MINUTES.between(
                        rastreo.getTimestampEntradaResidencia(), ahora);

                // Determinar nuevo estado
                EstadoTiempo nuevoEstado = calcularEstado(minutosEnResidencia);
                EstadoTiempo estadoAnterior = rastreo.getEstadoTiempo();

                rastreo.setEstadoTiempo(nuevoEstado);

                // Si cambió a PREOCUPANTE y no se ha notificado, enviar alerta
                if (nuevoEstado == EstadoTiempo.PREOCUPANTE &&
                        !Boolean.TRUE.equals(rastreo.getNotificacionPreocupanteEnviada())) {

                    enviarAlertaPreocupanteResidencia(empleado, minutosEnResidencia, latitud, longitud);
                    rastreo.setNotificacionPreocupanteEnviada(true);
                }

                if (nuevoEstado != estadoAnterior) {
                    logger.info("⏱️ {} cambió a estado {} después de {} min en residencia",
                            empleado.getNombre(), nuevoEstado, minutosEnResidencia);
                }
            } else {
                // Primera vez en esta residencia
                rastreo.setTimestampEntradaResidencia(ahora);
            }
        }

        return minutosEnResidencia;
    }

    /**
     * Determina si dos puntos GPS están en la misma residencia (dentro del radio)
     */
    private boolean esMismaResidencia(Double lat1, Double lon1, Double lat2, Double lon2) {
        if (lat1 == null || lon1 == null) {
            return false; // Primera ubicación
        }
        double distancia = calcularDistanciaMetros(lat1, lon1, lat2, lon2);
        return distancia <= RADIO_RESIDENCIA_METROS;
    }

    /**
     * Calcula la distancia en metros entre dos puntos GPS usando la fórmula de
     * Haversine
     */
    private double calcularDistanciaMetros(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371000; // Radio de la Tierra en metros

        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        double deltaLat = Math.toRadians(lat2 - lat1);
        double deltaLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                        Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c;
    }

    /**
     * Calcula el estado de tiempo según los minutos en residencia
     */
    private EstadoTiempo calcularEstado(int minutos) {
        if (minutos >= UMBRAL_PREOCUPANTE) {
            return EstadoTiempo.PREOCUPANTE;
        } else if (minutos >= UMBRAL_NORMAL) {
            return EstadoTiempo.NORMAL;
        } else {
            return EstadoTiempo.BIEN;
        }
    }

    /**
     * Envía notificación cuando el empleado SALE de la zona
     */
    private void enviarNotificacionSalioDeZona(Usuario empleado, Zona zona) {
        logger.warn("🚨 ALERTA: {} salió de la zona {}", empleado.getNombre(), zona.getNombre());

        List<Usuario> admins = usuarioRepository.findByRol("ADMIN");

        for (Usuario admin : admins) {
            List<TokenDispositivo> tokens = tokenDispositivoRepository.findTokensActivosByUsuario(admin);

            for (TokenDispositivo tokenDispositivo : tokens) {
                try {
                    Message message = Message.builder()
                            .setToken(tokenDispositivo.getToken())
                            .setNotification(Notification.builder()
                                    .setTitle("⚠️ Empleado Fuera de Zona")
                                    .setBody("El usuario " + empleado.getNombre() + " salió de su zona.")
                                    .build())
                            .putData("type", "ALERTA_SALIO_ZONA")
                            .putData("empleadoId", String.valueOf(empleado.getId()))
                            .putData("empleadoNombre", empleado.getNombre())
                            .putData("zonaId", String.valueOf(zona.getId()))
                            .putData("zonaNombre", zona.getNombre())
                            .setAndroidConfig(AndroidConfig.builder()
                                    .setPriority(AndroidConfig.Priority.HIGH)
                                    .build())
                            .build();

                    FirebaseMessaging.getInstance().send(message);
                    logger.info("📱 Notificación 'salió de zona' enviada al admin {}", admin.getNombre());

                } catch (FirebaseMessagingException e) {
                    logger.error("❌ Error al enviar notificación a {}: {}", admin.getNombre(), e.getMessage());
                }
            }
        }
    }

    /**
     * Envía notificación cuando el empleado lleva demasiado tiempo en la misma
     * RESIDENCIA
     */
    private void enviarAlertaPreocupanteResidencia(Usuario empleado, int minutos, double latitud, double longitud) {
        logger.warn("🚨 ALERTA: {} lleva {} minutos en la misma residencia ({}, {})",
                empleado.getNombre(), minutos, latitud, longitud);

        List<Usuario> admins = usuarioRepository.findByRol("ADMIN");

        for (Usuario admin : admins) {
            List<TokenDispositivo> tokens = tokenDispositivoRepository.findTokensActivosByUsuario(admin);

            for (TokenDispositivo tokenDispositivo : tokens) {
                try {
                    Message message = Message.builder()
                            .setToken(tokenDispositivo.getToken())
                            .setNotification(Notification.builder()
                                    .setTitle("⚠️ Alerta de Tiempo en Residencia")
                                    .setBody(empleado.getNombre() + " lleva " + minutos +
                                            " minutos en la misma ubicación")
                                    .build())
                            .putData("type", "ALERTA_TIEMPO_RESIDENCIA")
                            .putData("empleadoId", String.valueOf(empleado.getId()))
                            .putData("empleadoNombre", empleado.getNombre())
                            .putData("latitud", String.valueOf(latitud))
                            .putData("longitud", String.valueOf(longitud))
                            .putData("minutos", String.valueOf(minutos))
                            .putData("estado", "PREOCUPANTE")
                            .setAndroidConfig(AndroidConfig.builder()
                                    .setPriority(AndroidConfig.Priority.HIGH)
                                    .build())
                            .build();

                    FirebaseMessaging.getInstance().send(message);
                    logger.info("📱 Alerta de residencia enviada al admin {}", admin.getNombre());

                } catch (FirebaseMessagingException e) {
                    logger.error("❌ Error al enviar alerta a {}: {}", admin.getNombre(), e.getMessage());
                }
            }
        }
    }

    // ============================
    // 📊 CONSULTAS
    // ============================

    /**
     * Obtiene el estado de rastreo de todos los empleados.
     * Los minutos se calculan basándose en la RESIDENCIA, no en la zona.
     */
    public List<RastreoZonaResponse> obtenerTodosLosRastreos() {
        return rastreoRepository.findAllOrderByEstado().stream()
                .map(r -> {
                    int minutos = 0;
                    // Calcular minutos en RESIDENCIA (no en zona)
                    if (r.getTimestampEntradaResidencia() != null) {
                        minutos = (int) ChronoUnit.MINUTES.between(
                                r.getTimestampEntradaResidencia(),
                                LocalDateTime.now(ZONA_COLOMBIA));
                    }
                    return RastreoZonaResponse.fromEntity(r, minutos);
                })
                .toList();
    }

    /**
     * Obtiene los rastreos en estado PREOCUPANTE
     */
    public List<RastreoZonaResponse> obtenerRastreosPreocupantes() {
        return rastreoRepository.findByEstadoPreocupante().stream()
                .map(r -> {
                    int minutos = 0;
                    // Calcular minutos en RESIDENCIA
                    if (r.getTimestampEntradaResidencia() != null) {
                        minutos = (int) ChronoUnit.MINUTES.between(
                                r.getTimestampEntradaResidencia(),
                                LocalDateTime.now(ZONA_COLOMBIA));
                    }
                    return RastreoZonaResponse.fromEntity(r, minutos);
                })
                .toList();
    }

    /**
     * Obtiene el rastreo de un empleado específico
     */
    public RastreoZonaResponse obtenerRastreoPorEmpleado(Long empleadoId) {
        RastreoZona rastreo = rastreoRepository.findByEmpleadoId(empleadoId)
                .orElseThrow(() -> new RuntimeException("No hay rastreo para el empleado con ID: " + empleadoId));

        int minutos = 0;
        // Calcular minutos en RESIDENCIA
        if (rastreo.getTimestampEntradaResidencia() != null) {
            minutos = (int) ChronoUnit.MINUTES.between(
                    rastreo.getTimestampEntradaResidencia(),
                    LocalDateTime.now(ZONA_COLOMBIA));
        }

        return RastreoZonaResponse.fromEntity(rastreo, minutos);
    }

    /**
     * Elimina el rastreo de un empleado (cuando marca salida).
     * El registro se elimina completamente de la BD para que no aparezca en el
     * array de rastreos.
     */
    @Transactional
    public void eliminarRastreo(Usuario empleado) {
        rastreoRepository.findByEmpleado(empleado).ifPresent(rastreo -> {
            rastreoRepository.delete(rastreo);
            logger.info("🗑️ Rastreo eliminado para {} (marcó salida)", empleado.getNombre());
        });
    }
}
