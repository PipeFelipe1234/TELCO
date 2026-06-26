package com.practica.backend.service;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import com.practica.backend.dto.ResponderUbicacionRequest;
import com.practica.backend.dto.SolicitudUbicacionResponse;
import com.practica.backend.entity.SolicitudUbicacion;
import com.practica.backend.entity.TokenDispositivo;
import com.practica.backend.entity.Usuario;
import com.practica.backend.repository.RegistroRepository;
import com.practica.backend.repository.SolicitudUbicacionRepository;
import com.practica.backend.repository.TokenDispositivoRepository;
import com.practica.backend.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class GeolocalizacionService {

    // Zona horaria de Colombia
    private static final ZoneId ZONA_COLOMBIA = ZoneId.of("America/Bogota");

    private static final Logger logger = LoggerFactory.getLogger(GeolocalizacionService.class);
    private static final int SEGUNDOS_EXPIRACION = 90; // 90 segundos para expiración

    private final SolicitudUbicacionRepository solicitudRepository;
    private final UsuarioRepository usuarioRepository;
    private final TokenDispositivoRepository tokenDispositivoRepository;
    private final RegistroRepository registroRepository;
    private final GeocodingService geocodingService;
    private final RastreoZonaService rastreoZonaService;

    public GeolocalizacionService(
            SolicitudUbicacionRepository solicitudRepository,
            UsuarioRepository usuarioRepository,
            TokenDispositivoRepository tokenDispositivoRepository,
            RegistroRepository registroRepository,
            GeocodingService geocodingService,
            RastreoZonaService rastreoZonaService) {
        this.solicitudRepository = solicitudRepository;
        this.usuarioRepository = usuarioRepository;
        this.tokenDispositivoRepository = tokenDispositivoRepository;
        this.registroRepository = registroRepository;
        this.geocodingService = geocodingService;
        this.rastreoZonaService = rastreoZonaService;
    }

    /**
     * Admin solicita la ubicación de un empleado
     * 1. Crea registro en solicitudes_ubicacion con estado PENDIENTE
     * 2. Envía notificación silenciosa (data-only) al empleado
     * 3. Retorna el solicitudId
     */
    @Transactional
    public SolicitudUbicacionResponse solicitarUbicacion(Usuario admin, Long empleadoId) {
        logger.info("📍 Admin {} solicita ubicación del empleado ID: {}", admin.getNombre(), empleadoId);

        // Buscar el empleado
        Usuario empleado = usuarioRepository.findById(empleadoId)
                .orElseThrow(() -> new RuntimeException("Empleado no encontrado con ID: " + empleadoId));

        // Validar que no sea el mismo usuario
        if (admin.getId().equals(empleadoId)) {
            throw new RuntimeException("No puedes solicitar tu propia ubicación");
        }

        // Validar que el empleado esté en turno (con entrada registrada y sin salida)
        boolean enTurno = registroRepository.findUltimoRegistroSinSalida(empleado).isPresent();
        if (!enTurno) {
            throw new RuntimeException("El empleado " + empleado.getNombre()
                    + " no está en turno. Solo puedes geolocalizar empleados con entrada registrada y sin salida.");
        }

        // Crear la solicitud
        SolicitudUbicacion solicitud = new SolicitudUbicacion(admin, empleado);
        solicitud = solicitudRepository.save(solicitud);

        logger.info("✅ Solicitud creada con ID: {}", solicitud.getId());

        // Enviar notificación silenciosa al empleado
        boolean notificacionEnviada = enviarNotificacionSilenciosa(empleado, solicitud.getId());

        String mensaje = notificacionEnviada
                ? "Solicitud enviada. El empleado tiene " + SEGUNDOS_EXPIRACION + " segundos para responder."
                : "Solicitud creada, pero el empleado no tiene dispositivos registrados";

        return new SolicitudUbicacionResponse(
                solicitud.getId(),
                solicitud.getEstado(),
                mensaje);
    }

    /**
     * Empleado responde a una solicitud de ubicación
     * - Si todo bien: guarda coordenadas y notifica al admin
     * - Si hay error: guarda el error y notifica al admin del problema
     */
    @Transactional
    public void responderSolicitud(Usuario empleado, ResponderUbicacionRequest request) {
        logger.info("📍 Empleado {} responde solicitud ID: {}", empleado.getNombre(), request.solicitudId());

        // Buscar la solicitud y validar que pertenece al empleado
        SolicitudUbicacion solicitud = solicitudRepository.findByIdAndEmpleado(request.solicitudId(), empleado)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada o no te pertenece"));

        // Validar que esté pendiente
        if (!"PENDIENTE".equals(solicitud.getEstado())) {
            throw new RuntimeException("Esta solicitud ya fue respondida o expiró");
        }

        solicitud.setFechaRespuesta(LocalDateTime.now(ZONA_COLOMBIA));

        // Verificar si es una respuesta con error (GPS desactivado, permiso denegado,
        // etc.)
        if (Boolean.TRUE.equals(request.error())) {
            solicitud.setEstado("ERROR");
            solicitud.setMensajeError(request.mensajeError() != null ? request.mensajeError() : "Error desconocido");
            solicitudRepository.save(solicitud);

            logger.warn("⚠️ Solicitud {} respondida con ERROR: {}", request.solicitudId(), request.mensajeError());

            // Notificar al admin del error (solo si NO es automática para evitar spam)
            if (!Boolean.TRUE.equals(solicitud.getEsAutomatica())) {
                enviarNotificacionAlAdmin(
                        solicitud.getAdmin(),
                        "Error al obtener ubicación",
                        "El empleado " + empleado.getNombre() + " reportó: " + solicitud.getMensajeError(),
                        "UBICACION_ERROR",
                        solicitud.getId());
            }
        } else {
            // Respuesta exitosa con coordenadas
            solicitud.setLatitud(request.latitud());
            solicitud.setLongitud(request.longitud());
            solicitud.setPrecisionMetros(request.precisionMetros());

            // 📍 Reverse geocoding: si el frontend envía ubicación la usamos, si no,
            // llamamos a Google API
            String ubicacion = request.ubicacion();
            if (ubicacion == null || ubicacion.trim().isEmpty()) {
                ubicacion = geocodingService.obtenerDireccion(
                        request.latitud(),
                        request.longitud());
            }
            solicitud.setUbicacion(ubicacion);
            solicitud.setEstado("RESPONDIDA");
            solicitudRepository.save(solicitud);

            logger.info("✅ Solicitud {} respondida exitosamente", request.solicitudId());

            // 📍 Si es solicitud automática, procesar rastreo de zona
            if (Boolean.TRUE.equals(solicitud.getEsAutomatica())) {
                try {
                    rastreoZonaService.procesarUbicacion(empleado, request.latitud(), request.longitud());
                } catch (Exception e) {
                    logger.error("❌ Error al procesar rastreo de zona: {}", e.getMessage());
                }
            }

            // Notificar al admin que ya tiene la ubicación (solo si NO es automática para
            // evitar spam)
            if (!Boolean.TRUE.equals(solicitud.getEsAutomatica())) {
                String ubicacionTexto = ubicacion != null
                        ? ubicacion
                        : String.format("Lat: %.6f, Lon: %.6f", request.latitud(), request.longitud());

                enviarNotificacionAlAdmin(
                        solicitud.getAdmin(),
                        "Ubicación recibida",
                        "Empleado " + empleado.getNombre() + ": " + ubicacionTexto,
                        "UBICACION_RECIBIDA",
                        solicitud.getId());
            }
        }
    }

    /**
     * Admin consulta el resultado de una solicitud (polling)
     */
    public SolicitudUbicacionResponse obtenerResultado(Long solicitudId) {
        SolicitudUbicacion solicitud = solicitudRepository.findById(solicitudId)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada con ID: " + solicitudId));

        if ("ERROR".equals(solicitud.getEstado())) {
            return new SolicitudUbicacionResponse(
                    solicitud.getId(),
                    solicitud.getEstado(),
                    solicitud.getEmpleado().getId(),
                    solicitud.getEmpleado().getNombre(),
                    solicitud.getFechaSolicitud(),
                    solicitud.getFechaRespuesta(),
                    solicitud.getMensajeError());
        }

        return new SolicitudUbicacionResponse(
                solicitud.getId(),
                solicitud.getEstado(),
                solicitud.getEmpleado().getId(),
                solicitud.getEmpleado().getNombre(),
                solicitud.getLatitud(),
                solicitud.getLongitud(),
                solicitud.getPrecisionMetros(),
                solicitud.getUbicacion(),
                solicitud.getFechaSolicitud(),
                solicitud.getFechaRespuesta());
    }

    /**
     * Obtener solicitudes pendientes de un empleado
     * (para que la app sepa si tiene solicitudes por responder)
     */
    public List<SolicitudUbicacionResponse> obtenerSolicitudesPendientes(Usuario empleado) {
        return solicitudRepository.findSolicitudesPendientesByEmpleado(empleado)
                .stream()
                .map(s -> new SolicitudUbicacionResponse(
                        s.getId(),
                        s.getEstado(),
                        s.getEmpleado().getId(),
                        s.getEmpleado().getNombre(),
                        null, null, null, null,
                        s.getFechaSolicitud(),
                        null))
                .toList();
    }

    /**
     * Envía notificación SILENCIOSA (data-only, sin título ni body)
     * al dispositivo del empleado.
     * 
     * Configuración según requisitos:
     * - Data-only (sin notification) para que Flutter background handler se ejecute
     * - priority: HIGH para despertar el dispositivo inmediatamente
     * - ttl: 60 segundos - si no se entrega en ese tiempo, la solicitud ya no tiene
     * sentido
     * - APNS config para iOS: content-available para background wake
     */
    private boolean enviarNotificacionSilenciosa(Usuario empleado, Long solicitudId) {
        List<TokenDispositivo> tokens = tokenDispositivoRepository.findTokensActivosByUsuario(empleado);

        if (tokens.isEmpty()) {
            logger.warn("⚠️ El empleado {} no tiene dispositivos registrados", empleado.getNombre());
            return false;
        }

        int exitosos = 0;
        int fallidos = 0;

        for (TokenDispositivo tokenDispositivo : tokens) {
            String fcmToken = tokenDispositivo.getToken();

            try {
                // Crear mensaje DATA-ONLY con todas las configuraciones requeridas
                Message message = Message.builder()
                        .setToken(fcmToken)
                        // Solo datos, SIN .setNotification() para que sea silenciosa
                        .putData("type", "SOLICITUD_UBICACION")
                        .putData("solicitudId", String.valueOf(solicitudId))
                        .putData("timeout", String.valueOf(SEGUNDOS_EXPIRACION))
                        // Android: priority HIGH + TTL 60 segundos
                        .setAndroidConfig(AndroidConfig.builder()
                                .setPriority(AndroidConfig.Priority.HIGH)
                                .setTtl(60 * 1000L) // 60 segundos en milisegundos
                                .build())
                        // iOS: content-available para despertar la app en background
                        .setApnsConfig(ApnsConfig.builder()
                                .putHeader("apns-priority", "10") // Máxima prioridad
                                .putHeader("apns-push-type", "background")
                                .setAps(Aps.builder()
                                        .setContentAvailable(true)
                                        .build())
                                .build())
                        .build();

                String messageId = FirebaseMessaging.getInstance().send(message);
                logger.info("✅ Notificación silenciosa enviada a {}: {}", empleado.getNombre(), messageId);
                exitosos++;

            } catch (FirebaseMessagingException e) {
                fallidos++;
                MessagingErrorCode errorCode = e.getMessagingErrorCode();

                // Token inválido: el usuario desinstaló la app o el token expiró
                if (errorCode == MessagingErrorCode.UNREGISTERED ||
                        errorCode == MessagingErrorCode.INVALID_ARGUMENT) {
                    logger.warn("⚠️ Token FCM inválido para {}, eliminando de la BD", empleado.getNombre());
                    tokenDispositivoRepository.delete(tokenDispositivo);
                } else {
                    logger.error("❌ Error FCM al enviar a {}: {} - {}",
                            empleado.getNombre(), errorCode, e.getMessage());
                }
            } catch (Exception e) {
                fallidos++;
                logger.error("❌ Error inesperado al enviar notificación: {}", e.getMessage());
            }
        }

        logger.info("📊 Notificaciones silenciosas: {} exitosas, {} fallidas", exitosos, fallidos);
        return exitosos > 0;
    }

    /**
     * Envía notificación VISIBLE al admin
     * (con título y body - se muestra en el dispositivo)
     */
    private void enviarNotificacionAlAdmin(Usuario admin, String titulo, String cuerpo, String tipo, Long solicitudId) {
        try {
            List<TokenDispositivo> tokens = tokenDispositivoRepository.findTokensActivosByUsuario(admin);

            if (tokens.isEmpty()) {
                logger.warn("⚠️ El admin {} no tiene dispositivos registrados", admin.getNombre());
                return;
            }

            for (TokenDispositivo tokenDispositivo : tokens) {
                String fcmToken = tokenDispositivo.getToken();

                Message message = Message.builder()
                        .setToken(fcmToken)
                        .setNotification(Notification.builder()
                                .setTitle(titulo)
                                .setBody(cuerpo)
                                .build())
                        .putData("type", tipo)
                        .putData("solicitudId", String.valueOf(solicitudId))
                        .setAndroidConfig(AndroidConfig.builder()
                                .setPriority(AndroidConfig.Priority.HIGH)
                                .build())
                        .build();

                String messageId = FirebaseMessaging.getInstance().send(message);
                logger.info("✅ Notificación al admin {} enviada: {}", admin.getNombre(), messageId);
            }
        } catch (Exception e) {
            logger.error("❌ Error al enviar notificación al admin: {}", e.getMessage());
        }
    }

    /**
     * Marcar solicitudes antiguas como expiradas
     * Expira solicitudes PENDIENTES de más de 90 segundos
     * Notifica al admin de cada expiración
     */
    @Transactional
    public int expirarSolicitudesAntiguas() {
        LocalDateTime fechaLimite = LocalDateTime.now(ZONA_COLOMBIA).minusSeconds(SEGUNDOS_EXPIRACION);
        List<SolicitudUbicacion> expiradas = solicitudRepository.findSolicitudesExpiradas(fechaLimite);

        for (SolicitudUbicacion solicitud : expiradas) {
            solicitud.setEstado("EXPIRADA");
            solicitud.setFechaRespuesta(LocalDateTime.now(ZONA_COLOMBIA));
            solicitudRepository.save(solicitud);

            // Notificar al admin que expiró (solo si NO es automática para evitar spam)
            if (!Boolean.TRUE.equals(solicitud.getEsAutomatica())) {
                enviarNotificacionAlAdmin(
                        solicitud.getAdmin(),
                        "Solicitud expirada",
                        "El empleado " + solicitud.getEmpleado().getNombre() + " no respondió en " + SEGUNDOS_EXPIRACION
                                + " segundos",
                        "UBICACION_EXPIRADA",
                        solicitud.getId());
            }
        }

        if (!expiradas.isEmpty()) {
            logger.info("⏰ {} solicitudes marcadas como expiradas", expiradas.size());
        }

        return expiradas.size();
    }

    // ============================
    // 🤖 RASTREO AUTOMÁTICO EN TIEMPO REAL
    // ============================

    /**
     * Envía solicitudes de ubicación automáticas a todos los empleados EN TURNO
     * (que marcaron entrada pero no han marcado salida).
     * 
     * Este método es llamado por el scheduler cada 1 minuto para monitoreo en
     * tiempo real.
     * 
     * @return número de solicitudes enviadas exitosamente
     */
    @Transactional
    public int enviarSolicitudesAutomaticas() {
        // Obtener todos los registros activos (empleados en turno)
        List<com.practica.backend.entity.Registro> registrosEnTurno = registroRepository.findAllRegistrosEnTurno();

        // Si no hay empleados en turno, salir silenciosamente (sin logs innecesarios)
        if (registrosEnTurno.isEmpty()) {
            return 0;
        }

        logger.info("🤖 Iniciando rastreo automático: {} empleados en turno", registrosEnTurno.size());

        // Obtener el primer admin del sistema para asignar las solicitudes automáticas
        List<Usuario> admins = usuarioRepository.findByRolOrderByIdAsc("ADMIN");
        if (admins.isEmpty()) {
            logger.warn("⚠️ No hay administradores en el sistema para asignar solicitudes automáticas");
            return 0;
        }
        Usuario adminSistema = admins.get(0);

        int solicitudesEnviadas = 0;

        for (com.practica.backend.entity.Registro registro : registrosEnTurno) {
            Usuario empleado = registro.getUsuario();

            // No enviar solicitudes a usuarios con rol ADMIN
            if ("ADMIN".equals(empleado.getRol())) {
                continue;
            }

            try {
                // Crear la solicitud automática
                SolicitudUbicacion solicitud = new SolicitudUbicacion(adminSistema, empleado);
                solicitud.setEsAutomatica(true);
                solicitud = solicitudRepository.save(solicitud);

                // Enviar notificación silenciosa al empleado
                boolean enviada = enviarNotificacionSilenciosa(empleado, solicitud.getId());

                if (enviada) {
                    solicitudesEnviadas++;
                    logger.info("🤖 Solicitud automática #{} enviada a: {}", solicitud.getId(), empleado.getNombre());
                } else {
                    logger.warn("⚠️ No se pudo enviar notificación automática a: {} (sin dispositivos)",
                            empleado.getNombre());
                }
            } catch (Exception e) {
                logger.error("❌ Error al enviar solicitud automática a {}: {}", empleado.getNombre(), e.getMessage());
            }
        }

        logger.info("🤖 Rastreo automático completado: {} solicitudes enviadas de {} empleados en turno",
                solicitudesEnviadas, registrosEnTurno.size());

        return solicitudesEnviadas;
    }

    // ============================
    // 🗑️ LIMPIEZA Y ELIMINACIÓN
    // ============================

    /**
     * Elimina geolocalizaciones de un mes y año específico
     */
    @Transactional
    public long eliminarPorMesYAnio(int mes, int anio) {
        long cantidad = solicitudRepository.countByMesYAnio(mes, anio);
        if (cantidad > 0) {
            solicitudRepository.deleteByMesYAnio(mes, anio);
            logger.info("🗑️ Eliminadas {} geolocalizaciones del mes {}/{}", cantidad, mes, anio);
        }
        return cantidad;
    }

    /**
     * Elimina solicitudes AUTOMÁTICAS antiguas (rastreo cada 60s)
     * para evitar crecimiento infinito de la tabla.
     *
     * @return cantidad de registros eliminados
     */
    @Transactional
    public int eliminarSolicitudesAutomaticasAntiguas() {
        LocalDateTime fechaLimite = LocalDateTime.now(ZONA_COLOMBIA).minusHours(24);
        int eliminadas = solicitudRepository.deleteAutomaticasAnterioresA(fechaLimite);

        if (eliminadas > 0) {
            logger.info("🧹 Eliminadas {} solicitudes automáticas antiguas (>{} horas)", eliminadas, 24);
        }

        return eliminadas;
    }

    /**
     * Obtiene información sobre la próxima limpieza automática de geolocalizaciones
     * Muestra solo las MANUALES que se perderán (las exportables)
     */
    public java.util.Map<String, Object> obtenerInfoLimpieza() {
        java.time.LocalDate hoy = java.time.LocalDate.now();

        // El primer día del próximo mes es cuando se ejecuta la eliminación
        java.time.LocalDate fechaEliminacion = hoy.withDayOfMonth(1).plusMonths(1);

        // El mes que será eliminado es 2 meses antes de la fecha de eliminación
        java.time.LocalDate mesAEliminar = fechaEliminacion.minusMonths(2);
        int mes = mesAEliminar.getMonthValue();
        int anio = mesAEliminar.getYear();

        // Contar solo geolocalizaciones MANUALES que serán eliminadas (las exportables)
        long cantidad = solicitudRepository.countManualesByMesYAnio(mes, anio);

        // Si no hay geolocalizaciones manuales del mes antiguo, no hay advertencia
        if (cantidad == 0) {
            return java.util.Map.of(
                    "hayAdvertencia", false,
                    "mensaje", "No hay geolocalizaciones programadas para eliminación automática.",
                    "cantidadRegistros", 0L,
                    "puedeExportar", false);
        }

        // Calcular días restantes
        long diasRestantes = java.time.temporal.ChronoUnit.DAYS.between(hoy, fechaEliminacion);

        String nombreMes = java.time.Month.of(mes)
                .getDisplayName(java.time.format.TextStyle.FULL, new java.util.Locale("es", "ES"))
                .toUpperCase();

        boolean hayAdvertencia = diasRestantes <= 4;

        String mensaje;
        if (hayAdvertencia) {
            mensaje = "⚠️ Se eliminarán automáticamente " + cantidad + " geolocalizaciones del Mes: " + nombreMes + " "
                    + anio +
                    " en " + diasRestantes + " día(s). Por favor exporte las geolocalizaciones de ese mes.";
        } else {
            mensaje = "Las geolocalizaciones del mes de " + nombreMes + " " + anio +
                    " serán eliminadas el "
                    + fechaEliminacion.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) +
                    ". Tiene " + diasRestantes + " días para exportarlas.";
        }

        return java.util.Map.of(
                "hayAdvertencia", hayAdvertencia,
                "mensaje", mensaje,
                "mesAEliminar", nombreMes,
                "anioAEliminar", anio,
                "diasRestantes", diasRestantes,
                "fechaEliminacion", fechaEliminacion.toString(),
                "cantidadRegistros", cantidad,
                "puedeExportar", true);
    }
}
