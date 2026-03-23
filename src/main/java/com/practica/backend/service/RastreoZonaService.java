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
 * Servicio para rastrear el tiempo que un empleado permanece en una zona.
 * Detecta estados: BIEN (0-3 min), NORMAL (3-10 min), PREOCUPANTE (10+ min)
 * Envía notificaciones al admin cuando un empleado llega a estado PREOCUPANTE.
 */
@Service
public class RastreoZonaService {

    private static final Logger logger = LoggerFactory.getLogger(RastreoZonaService.class);
    private static final ZoneId ZONA_COLOMBIA = ZoneId.of("America/Bogota");

    // Umbrales de tiempo en minutos
    private static final int UMBRAL_NORMAL = 3; // Después de 3 min -> NORMAL
    private static final int UMBRAL_PREOCUPANTE = 10; // Después de 10 min -> PREOCUPANTE

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
     * - Determina en qué zona está
     * - Calcula el tiempo en esa zona
     * - Actualiza el estado
     * - Notifica si es necesario
     * 
     * Este método debe llamarse cada vez que se recibe una ubicación automática.
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

        // Encontrar en qué zona está el empleado
        Zona zonaActual = zonaService.encontrarZonaPorPunto(latitud, longitud);

        // Actualizar ubicación
        rastreo.setUltimaLatitud(latitud);
        rastreo.setUltimaLongitud(longitud);

        LocalDateTime ahora = LocalDateTime.now(ZONA_COLOMBIA);
        int minutosEnZona = 0;

        if (zonaActual == null) {
            // No está en ninguna zona - reiniciar
            if (rastreo.getZonaActual() != null) {
                logger.info("👤 {} salió de la zona {}", empleado.getNombre(), rastreo.getZonaActual().getNombre());
            }
            rastreo.setZonaActual(null);
            rastreo.setTimestampEntradaZona(null);
            rastreo.setEstadoTiempo(EstadoTiempo.BIEN);
            rastreo.setNotificacionPreocupanteEnviada(false);
        } else {
            // Está en una zona
            if (rastreo.getZonaActual() == null || !rastreo.getZonaActual().getId().equals(zonaActual.getId())) {
                // Entró a una nueva zona (o cambió de zona)
                logger.info("👤 {} entró a la zona {}", empleado.getNombre(), zonaActual.getNombre());
                rastreo.setZonaActual(zonaActual);
                rastreo.setTimestampEntradaZona(ahora);
                rastreo.setEstadoTiempo(EstadoTiempo.BIEN);
                rastreo.setNotificacionPreocupanteEnviada(false);
            } else {
                // Sigue en la misma zona - calcular tiempo
                if (rastreo.getTimestampEntradaZona() != null) {
                    minutosEnZona = (int) ChronoUnit.MINUTES.between(rastreo.getTimestampEntradaZona(), ahora);

                    // Determinar nuevo estado
                    EstadoTiempo nuevoEstado = calcularEstado(minutosEnZona);
                    EstadoTiempo estadoAnterior = rastreo.getEstadoTiempo();

                    rastreo.setEstadoTiempo(nuevoEstado);

                    // Si cambió a PREOCUPANTE y no se ha notificado, enviar alerta
                    if (nuevoEstado == EstadoTiempo.PREOCUPANTE &&
                            !Boolean.TRUE.equals(rastreo.getNotificacionPreocupanteEnviada())) {

                        enviarAlertaPreocupante(empleado, zonaActual, minutosEnZona);
                        rastreo.setNotificacionPreocupanteEnviada(true);
                    }

                    if (nuevoEstado != estadoAnterior) {
                        logger.info("⏱️ {} cambió a estado {} después de {} min en {}",
                                empleado.getNombre(), nuevoEstado, minutosEnZona, zonaActual.getNombre());
                    }
                }
            }
        }

        rastreo = rastreoRepository.save(rastreo);

        return RastreoZonaResponse.fromEntity(rastreo, minutosEnZona);
    }

    /**
     * Calcula el estado de tiempo según los minutos en zona
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
     * Envía notificación de alerta a todos los administradores
     */
    private void enviarAlertaPreocupante(Usuario empleado, Zona zona, int minutos) {
        logger.warn("🚨 ALERTA: {} lleva {} minutos en {}", empleado.getNombre(), minutos, zona.getNombre());

        // Obtener todos los administradores
        List<Usuario> admins = usuarioRepository.findByRol("ADMIN");

        for (Usuario admin : admins) {
            List<TokenDispositivo> tokens = tokenDispositivoRepository.findTokensActivosByUsuario(admin);

            for (TokenDispositivo tokenDispositivo : tokens) {
                try {
                    Message message = Message.builder()
                            .setToken(tokenDispositivo.getToken())
                            .setNotification(Notification.builder()
                                    .setTitle("⚠️ Alerta de Tiempo en Zona")
                                    .setBody(empleado.getNombre() + " lleva " + minutos +
                                            " minutos en " + zona.getNombre())
                                    .build())
                            .putData("type", "ALERTA_TIEMPO_ZONA")
                            .putData("empleadoId", String.valueOf(empleado.getId()))
                            .putData("empleadoNombre", empleado.getNombre())
                            .putData("zonaId", String.valueOf(zona.getId()))
                            .putData("zonaNombre", zona.getNombre())
                            .putData("minutos", String.valueOf(minutos))
                            .putData("estado", "PREOCUPANTE")
                            .setAndroidConfig(AndroidConfig.builder()
                                    .setPriority(AndroidConfig.Priority.HIGH)
                                    .build())
                            .build();

                    FirebaseMessaging.getInstance().send(message);
                    logger.info("📱 Alerta enviada al admin {}", admin.getNombre());

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
     * Obtiene el estado de rastreo de todos los empleados
     */
    public List<RastreoZonaResponse> obtenerTodosLosRastreos() {
        return rastreoRepository.findAllOrderByEstado().stream()
                .map(r -> {
                    int minutos = 0;
                    if (r.getTimestampEntradaZona() != null) {
                        minutos = (int) ChronoUnit.MINUTES.between(
                                r.getTimestampEntradaZona(),
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
                    if (r.getTimestampEntradaZona() != null) {
                        minutos = (int) ChronoUnit.MINUTES.between(
                                r.getTimestampEntradaZona(),
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
        if (rastreo.getTimestampEntradaZona() != null) {
            minutos = (int) ChronoUnit.MINUTES.between(
                    rastreo.getTimestampEntradaZona(),
                    LocalDateTime.now(ZONA_COLOMBIA));
        }

        return RastreoZonaResponse.fromEntity(rastreo, minutos);
    }

    /**
     * Reinicia el rastreo de un empleado (cuando marca salida)
     */
    @Transactional
    public void reiniciarRastreo(Usuario empleado) {
        rastreoRepository.findByEmpleado(empleado).ifPresent(rastreo -> {
            rastreo.setZonaActual(null);
            rastreo.setTimestampEntradaZona(null);
            rastreo.setEstadoTiempo(EstadoTiempo.BIEN);
            rastreo.setNotificacionPreocupanteEnviada(false);
            rastreoRepository.save(rastreo);
            logger.info("🔄 Rastreo reiniciado para {}", empleado.getNombre());
        });
    }
}
