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
import com.practica.backend.repository.RegistroRepository;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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

    // Porcentajes del límite de tiempo configurable por usuario
    private static final double PORCENTAJE_NORMAL = 0.70; // 70% del límite → NORMAL (Amarillo)
    private static final double PORCENTAJE_PREOCUPANTE = 1.00; // 100% del límite → PREOCUPANTE (Rojo)
    private static final int TIEMPO_LIMITE_DEFECTO = 10; // Minutos por defecto si el usuario no tiene límite

    // Radio en metros para considerar que es la misma residencia
    private static final double RADIO_RESIDENCIA_METROS = 15.0;

    private final RastreoZonaRepository rastreoRepository;
    private final RegistroRepository registroRepository;
    private final ZonaService zonaService;
    private final UsuarioRepository usuarioRepository;
    private final TokenDispositivoRepository tokenDispositivoRepository;

    public RastreoZonaService(
            RastreoZonaRepository rastreoRepository,
            RegistroRepository registroRepository,
            ZonaService zonaService,
            UsuarioRepository usuarioRepository,
            TokenDispositivoRepository tokenDispositivoRepository) {
        this.rastreoRepository = rastreoRepository;
        this.registroRepository = registroRepository;
        this.zonaService = zonaService;
        this.usuarioRepository = usuarioRepository;
        this.tokenDispositivoRepository = tokenDispositivoRepository;
    }

    /**
     * Procesa una nueva ubicación de un empleado.
     * 
     * Dos niveles de rastreo:
     * 1. NODO ASIGNADO: Detecta si salió de SUS nodos asignados → envía
     * notificación
     * 2. RESIDENCIA: Detecta tiempo en el mismo punto → estados
     * BIEN/NORMAL/PREOCUPANTE
     */
    @Transactional
    public RastreoZonaResponse procesarUbicacion(Usuario empleado, double latitud, double longitud) {
        logger.info("📍 Procesando ubicación de {} en ({}, {})", empleado.getNombre(), latitud, longitud);

        // Cargar el usuario con sus zonas asignadas (evitar problemas de LAZY loading)
        Usuario empleadoConZonas = usuarioRepository.findById(empleado.getId())
                .orElse(empleado);
        // Forzar carga de zonas asignadas
        empleadoConZonas.getZonasAsignadas().size();

        // Buscar o crear el rastreo del empleado
        RastreoZona rastreo = rastreoRepository.findByEmpleado(empleadoConZonas)
                .orElseGet(() -> {
                    RastreoZona nuevo = new RastreoZona(empleadoConZonas);
                    return rastreoRepository.save(nuevo);
                });

        // Actualizar última ubicación
        rastreo.setUltimaLatitud(latitud);
        rastreo.setUltimaLongitud(longitud);

        LocalDateTime ahora = LocalDateTime.now(ZONA_COLOMBIA);

        // ==========================================
        // 1. RASTREO DE NODOS ASIGNADOS (detectar si salió de SUS nodos)
        // ==========================================
        procesarRastreoNodosAsignados(rastreo, empleadoConZonas, latitud, longitud, ahora);

        // ==========================================
        // 2. RASTREO DE RESIDENCIA (tiempo en punto)
        // ==========================================
        int minutosEnResidencia = procesarRastreoResidencia(rastreo, latitud, longitud, empleadoConZonas, ahora);

        rastreo = rastreoRepository.save(rastreo);

        return RastreoZonaResponse.fromEntity(rastreo, minutosEnResidencia);
    }

    /**
     * Procesa el rastreo por NODOS asignados al usuario.
     * Si el usuario no tiene nodos configurados en sus zonas, usa la lógica legacy
     * por
     * zonas asignadas.
     */
    private void procesarRastreoNodosAsignados(RastreoZona rastreo, Usuario empleado,
            double latitud, double longitud, LocalDateTime ahora) {
        // Verificar si el usuario tiene zonas asignadas
        if (!empleado.tieneZonasAsignadas()) {
            // Sin zonas asignadas, no hay nada que rastrear
            rastreo.setZonaActual(null);
            rastreo.setTimestampEntradaZona(null);
            return;
        }

        Set<String> nodosAsignados = obtenerNodosAsignados(empleado);

        // Buscar si está dentro de alguno de SUS nodos asignados
        Zona zonaActual = nodosAsignados.isEmpty()
                ? encontrarZonaAsignadaParaEmpleado(empleado, latitud, longitud)
                : encontrarZonaEnNodosAsignados(nodosAsignados, latitud, longitud);

        Zona zonaAnterior = rastreo.getZonaActual();

        if (zonaActual == null) {
            // No está en NINGUNO de sus nodos asignados
            if (zonaAnterior != null && !Boolean.TRUE.equals(rastreo.getNotificacionSalioZonaEnviada())) {
                // Estaba dentro de un nodo/zona permitido y ahora salió
                if (nodosAsignados.isEmpty()) {
                    enviarNotificacionSalioDeZona(empleado, zonaAnterior);
                } else {
                    enviarNotificacionSalioDeNodo(empleado, zonaAnterior.getNodo());
                }
                rastreo.setNotificacionSalioZonaEnviada(true);
            } else if (zonaAnterior == null && !Boolean.TRUE.equals(rastreo.getNotificacionSalioZonaEnviada())) {
                // Primera ubicación y ya está fuera de todos sus nodos - notificar
                if (nodosAsignados.isEmpty()) {
                    logger.warn("⚠️ {} está fuera de todas sus zonas asignadas", empleado.getNombre());
                    enviarNotificacionFueraDeZonasAsignadas(empleado);
                } else {
                    logger.warn("⚠️ {} está fuera de todos sus nodos asignados", empleado.getNombre());
                    enviarNotificacionFueraDeNodosAsignados(empleado);
                }
                rastreo.setNotificacionSalioZonaEnviada(true);
            }
            rastreo.setZonaActual(null);
            rastreo.setTimestampEntradaZona(null);
        } else {
            // Está en uno de sus nodos permitidos
            if (zonaAnterior == null || !zonaAnterior.getId().equals(zonaActual.getId())) {
                logger.info("👤 {} entró a zona {} del nodo {}",
                        empleado.getNombre(),
                        zonaActual.getNombre(),
                        zonaActual.getNodo());
                rastreo.setZonaActual(zonaActual);
                rastreo.setTimestampEntradaZona(ahora);
                rastreo.setNotificacionSalioZonaEnviada(false); // Resetear flag
            }
        }
    }

    private Set<String> obtenerNodosAsignados(Usuario empleado) {
        Set<String> nodos = new HashSet<>();
        for (Zona zona : empleado.getZonasAsignadas()) {
            if (zona.getActiva()) {
                String nodoNormalizado = normalizarNodo(zona.getNodo());
                if (nodoNormalizado != null) {
                    nodos.add(nodoNormalizado);
                }
            }
        }
        return nodos;
    }

    private Zona encontrarZonaEnNodosAsignados(Set<String> nodosAsignados, double latitud, double longitud) {
        for (Zona zona : zonaService.obtenerEntidadesZonasActivas()) {
            String nodoNormalizado = normalizarNodo(zona.getNodo());
            if (nodoNormalizado != null
                    && nodosAsignados.contains(nodoNormalizado)
                    && zonaService.estaDentroDeZona(latitud, longitud, zona)) {
                return zona;
            }
        }
        return null;
    }

    private String normalizarNodo(String nodo) {
        if (nodo == null) {
            return null;
        }
        String valor = nodo.trim();
        return valor.isEmpty() ? null : valor.toUpperCase();
    }

    /**
     * Busca si el empleado está en alguna de SUS zonas asignadas
     */
    private Zona encontrarZonaAsignadaParaEmpleado(Usuario empleado, double latitud, double longitud) {
        for (Zona zona : empleado.getZonasAsignadas()) {
            if (zona.getActiva() && zonaService.estaDentroDeZona(latitud, longitud, zona)) {
                return zona;
            }
        }
        return null;
    }

    /**
     * Envía notificación cuando el empleado está fuera de TODAS sus zonas
     * asignadas.
     * Mensaje: "El [cargo] [nombre] está fuera de sus zonas asignadas"
     */
    private void enviarNotificacionFueraDeZonasAsignadas(Usuario empleado) {
        String cargo = empleado.getCargo() != null ? empleado.getCargo() : "Empleado";
        String mensajeBody = "El " + cargo + " " + empleado.getNombre() + " está fuera de sus zonas asignadas";

        logger.warn("🚨 ALERTA: {}", mensajeBody);

        // Obtener admins según tipo de usuario
        List<Usuario> admins = obtenerAdminsParaNotificacion(empleado);

        for (Usuario admin : admins) {
            List<TokenDispositivo> tokens = tokenDispositivoRepository.findTokensActivosByUsuario(admin);

            for (TokenDispositivo tokenDispositivo : tokens) {
                try {
                    Message message = Message.builder()
                            .setToken(tokenDispositivo.getToken())
                            .setNotification(Notification.builder()
                                    .setTitle("⚠️ Empleado Fuera de Zona")
                                    .setBody(mensajeBody)
                                    .build())
                            .putData("type", "ALERTA_FUERA_ZONAS_ASIGNADAS")
                            .putData("empleadoId", String.valueOf(empleado.getId()))
                            .putData("empleadoNombre", empleado.getNombre())
                            .putData("empleadoCargo", cargo)
                            .setAndroidConfig(AndroidConfig.builder()
                                    .setPriority(AndroidConfig.Priority.HIGH)
                                    .build())
                            .build();

                    FirebaseMessaging.getInstance().send(message);
                    logger.info("📱 Notificación 'fuera de zonas asignadas' enviada al admin {}", admin.getNombre());

                } catch (FirebaseMessagingException e) {
                    logger.error("❌ Error al enviar notificación a {}: {}", admin.getNombre(), e.getMessage());
                }
            }
        }
    }

    /**
     * Envía notificación cuando el empleado está fuera de TODOS sus nodos
     * asignados.
     */
    private void enviarNotificacionFueraDeNodosAsignados(Usuario empleado) {
        String cargo = empleado.getCargo() != null ? empleado.getCargo() : "Empleado";
        String mensajeBody = "El " + cargo + " " + empleado.getNombre() + " está fuera de sus nodos asignados";

        logger.warn("🚨 ALERTA: {}", mensajeBody);

        List<Usuario> admins = obtenerAdminsParaNotificacion(empleado);

        for (Usuario admin : admins) {
            List<TokenDispositivo> tokens = tokenDispositivoRepository.findTokensActivosByUsuario(admin);

            for (TokenDispositivo tokenDispositivo : tokens) {
                try {
                    Message message = Message.builder()
                            .setToken(tokenDispositivo.getToken())
                            .setNotification(Notification.builder()
                                    .setTitle("⚠️ Empleado Fuera de Nodo")
                                    .setBody(mensajeBody)
                                    .build())
                            .putData("type", "ALERTA_FUERA_ZONAS_ASIGNADAS")
                            .putData("scope", "NODO")
                            .putData("empleadoId", String.valueOf(empleado.getId()))
                            .putData("empleadoNombre", empleado.getNombre())
                            .putData("empleadoCargo", cargo)
                            .setAndroidConfig(AndroidConfig.builder()
                                    .setPriority(AndroidConfig.Priority.HIGH)
                                    .build())
                            .build();

                    FirebaseMessaging.getInstance().send(message);
                    logger.info("📱 Notificación 'fuera de nodos asignados' enviada al admin {}", admin.getNombre());

                } catch (FirebaseMessagingException e) {
                    logger.error("❌ Error al enviar notificación a {}: {}", admin.getNombre(), e.getMessage());
                }
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
                EstadoTiempo nuevoEstado = calcularEstado(minutosEnResidencia, empleado.getTiempoLimiteMinutos());
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
     * Calcula el estado de tiempo según los minutos en residencia y el límite del
     * usuario.
     * Verde (BIEN): 0%–70% del límite. Amarillo (NORMAL): 70%–100%. Rojo
     * (PREOCUPANTE): >100%.
     */
    private EstadoTiempo calcularEstado(int minutos, Integer tiempoLimiteMinutos) {
        int limite = (tiempoLimiteMinutos != null && tiempoLimiteMinutos > 0)
                ? tiempoLimiteMinutos
                : TIEMPO_LIMITE_DEFECTO;
        double porcentaje = (double) minutos / limite;
        if (porcentaje >= PORCENTAJE_PREOCUPANTE) {
            return EstadoTiempo.PREOCUPANTE; // Rojo: > 100% del límite
        } else if (porcentaje >= PORCENTAJE_NORMAL) {
            return EstadoTiempo.NORMAL; // Amarillo: 70%–100% del límite
        } else {
            return EstadoTiempo.BIEN; // Verde: 0%–70% del límite
        }
    }

    /**
     * Envía notificación cuando el empleado SALE de la zona.
     * Mensaje: "El [cargo] [nombre] está fuera de [zona]"
     */
    private void enviarNotificacionSalioDeZona(Usuario empleado, Zona zona) {
        String cargo = empleado.getCargo() != null ? empleado.getCargo() : "Empleado";
        String mensajeBody = "El " + cargo + " " + empleado.getNombre() + " está fuera de " + zona.getNombre();

        logger.warn("🚨 ALERTA: {}", mensajeBody);

        // Obtener admins según tipo de usuario
        List<Usuario> admins = obtenerAdminsParaNotificacion(empleado);

        for (Usuario admin : admins) {
            List<TokenDispositivo> tokens = tokenDispositivoRepository.findTokensActivosByUsuario(admin);

            for (TokenDispositivo tokenDispositivo : tokens) {
                try {
                    Message message = Message.builder()
                            .setToken(tokenDispositivo.getToken())
                            .setNotification(Notification.builder()
                                    .setTitle("⚠️ Empleado Fuera de Zona")
                                    .setBody(mensajeBody)
                                    .build())
                            .putData("type", "ALERTA_SALIO_ZONA")
                            .putData("empleadoId", String.valueOf(empleado.getId()))
                            .putData("empleadoNombre", empleado.getNombre())
                            .putData("empleadoCargo", cargo)
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
     * Envía notificación cuando el empleado sale del nodo permitido.
     */
    private void enviarNotificacionSalioDeNodo(Usuario empleado, String nodo) {
        String cargo = empleado.getCargo() != null ? empleado.getCargo() : "Empleado";
        String nodoLabel = (nodo == null || nodo.isBlank()) ? "su nodo asignado" : nodo;
        String mensajeBody = "El " + cargo + " " + empleado.getNombre() + " está fuera del nodo " + nodoLabel;

        logger.warn("🚨 ALERTA: {}", mensajeBody);

        List<Usuario> admins = obtenerAdminsParaNotificacion(empleado);

        for (Usuario admin : admins) {
            List<TokenDispositivo> tokens = tokenDispositivoRepository.findTokensActivosByUsuario(admin);

            for (TokenDispositivo tokenDispositivo : tokens) {
                try {
                    Message message = Message.builder()
                            .setToken(tokenDispositivo.getToken())
                            .setNotification(Notification.builder()
                                    .setTitle("⚠️ Empleado Fuera de Nodo")
                                    .setBody(mensajeBody)
                                    .build())
                            .putData("type", "ALERTA_SALIO_ZONA")
                            .putData("scope", "NODO")
                            .putData("empleadoId", String.valueOf(empleado.getId()))
                            .putData("empleadoNombre", empleado.getNombre())
                            .putData("empleadoCargo", cargo)
                            .putData("nodo", nodoLabel)
                            .setAndroidConfig(AndroidConfig.builder()
                                    .setPriority(AndroidConfig.Priority.HIGH)
                                    .build())
                            .build();

                    FirebaseMessaging.getInstance().send(message);
                    logger.info("📱 Notificación 'salió de nodo' enviada al admin {}", admin.getNombre());

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

        // Obtener admins según tipo de usuario
        List<Usuario> admins = obtenerAdminsParaNotificacion(empleado);

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
     * Filtrado según el cargo del admin autenticado:
     * - ADMIN (super admin) ve todos los empleados
     * - ADMIN_TEC ve solo empleados USER_TEC
     * - ADMIN_COO ve solo empleados USER_COO
     */
    public List<RastreoZonaResponse> obtenerTodosLosRastreosFiltrados(String cargoAdmin) {
        List<RastreoZonaResponse> rastreos = obtenerTodosLosRastreos();

        if ("ADMIN_TEC".equals(cargoAdmin)) {
            return rastreos.stream()
                    .filter(r -> "USER_TEC".equals(r.empleadoCargo()))
                    .toList();
        } else if ("ADMIN_COO".equals(cargoAdmin)) {
            return rastreos.stream()
                    .filter(r -> "USER_COO".equals(r.empleadoCargo()))
                    .toList();
        }
        // ADMIN (super admin) ve todos
        return rastreos;
    }

    /**
     * Obtiene el estado de rastreo de todos los empleados.
     * Los minutos se calculan basándose en la RESIDENCIA, no en la zona.
     */
    public List<RastreoZonaResponse> obtenerTodosLosRastreos() {
        Set<Long> empleadosEnTurnoIds = registroRepository.findAllRegistrosEnTurno().stream()
                .map(r -> r.getUsuario().getId())
                .collect(Collectors.toSet());

        return rastreoRepository.findAllOrderByEstado().stream()
                .filter(r -> empleadosEnTurnoIds.contains(r.getEmpleado().getId()))
                .map(r -> {
                    int minutos = 0;
                    // Calcular minutos en RESIDENCIA (no en zona)
                    if (r.getTimestampEntradaResidencia() != null) {
                        minutos = (int) ChronoUnit.MINUTES.between(
                                r.getTimestampEntradaResidencia(),
                                LocalDateTime.now(ZONA_COLOMBIA));
                    }
                    return construirRespuestaConEstadoActual(r, minutos);
                })
                .toList();
    }

    /**
     * Elimina rastreos huérfanos: empleados que aparecen en rastreo_zona
     * pero ya no tienen un turno activo (entrada sin salida).
     */
    @Transactional
    public int limpiarRastreosHuerfanos() {
        Set<Long> empleadosEnTurnoIds = registroRepository.findAllRegistrosEnTurno().stream()
                .map(r -> r.getUsuario().getId())
                .collect(Collectors.toSet());

        List<RastreoZona> huerfanos = rastreoRepository.findAll().stream()
                .filter(r -> !empleadosEnTurnoIds.contains(r.getEmpleado().getId()))
                .toList();

        if (!huerfanos.isEmpty()) {
            rastreoRepository.deleteAll(huerfanos);
            logger.info("🧹 Limpieza de rastreo_zona: {} registros huérfanos eliminados", huerfanos.size());
        }

        return huerfanos.size();
    }

    /**
     * Obtiene los rastreos en estado PREOCUPANTE
     * Filtrado según el cargo del admin autenticado
     */
    public List<RastreoZonaResponse> obtenerRastreosPreocupantesFiltrados(String cargoAdmin) {
        List<RastreoZonaResponse> rastreos = obtenerRastreosPreocupantes();

        if ("ADMIN_TEC".equals(cargoAdmin)) {
            return rastreos.stream()
                    .filter(r -> "USER_TEC".equals(r.empleadoCargo()))
                    .toList();
        } else if ("ADMIN_COO".equals(cargoAdmin)) {
            return rastreos.stream()
                    .filter(r -> "USER_COO".equals(r.empleadoCargo()))
                    .toList();
        }
        // ADMIN (super admin) ve todos
        return rastreos;
    }

    /**
     * Obtiene los rastreos en estado PREOCUPANTE
     */
    public List<RastreoZonaResponse> obtenerRastreosPreocupantes() {
        // Se calcula en tiempo real para evitar estados desactualizados en BD.
        return obtenerTodosLosRastreos().stream()
                .filter(r -> EstadoTiempo.PREOCUPANTE.name().equals(r.estadoTiempo()))
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

        return construirRespuestaConEstadoActual(rastreo, minutos);
    }

    /**
     * Construye la respuesta recalculando el estado en tiempo real según minutos
     * transcurridos y límite configurado del usuario.
     */
    private RastreoZonaResponse construirRespuestaConEstadoActual(RastreoZona rastreo, int minutosEnResidencia) {
        EstadoTiempo estadoActual = calcularEstado(minutosEnResidencia, rastreo.getEmpleado().getTiempoLimiteMinutos());

        return new RastreoZonaResponse(
                rastreo.getEmpleado().getId(),
                rastreo.getEmpleado().getNombre(),
                rastreo.getEmpleado().getIdentificacion(),
                rastreo.getEmpleado().getCargo(),
                rastreo.getZonaActual() != null ? rastreo.getZonaActual().getId() : null,
                rastreo.getZonaActual() != null ? rastreo.getZonaActual().getNombre() : "Fuera de zona",
                rastreo.getZonaActual() != null,
                estadoActual.name(),
                minutosEnResidencia,
                rastreo.getUltimaLatitud(),
                rastreo.getUltimaLongitud(),
                rastreo.getTimestampEntradaResidencia(),
                rastreo.getUltimaActualizacion());
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

    // ============================
    // 🔔 MÉTODO HELPER - FILTRAR ADMINS
    // ============================

    /**
     * Obtiene los admins a quienes enviar notificación según el cargo del usuario.
     * - Si el usuario tiene cargo USER_TEC → envía a ADMIN + ADMIN_TEC
     * - Si el usuario tiene cargo USER_COO → envía a ADMIN + ADMIN_COO
     */
    private List<Usuario> obtenerAdminsParaNotificacion(Usuario empleado) {
        List<String> ciudadesEmpleado = empleado.getCiudades();

        if ("USER_TEC".equals(empleado.getCargo())) {
            List<Usuario> adminsTec = usuarioRepository.findAllAdminsTecnicos().stream()
                    .filter(a -> adminCubreEmpleado(a, ciudadesEmpleado))
                    .toList();
            List<Usuario> result = new java.util.ArrayList<>(usuarioRepository.findAllSuperAdmins());
            result.addAll(adminsTec);
            return result;
        } else if ("USER_COO".equals(empleado.getCargo())) {
            List<Usuario> adminsCoo = usuarioRepository.findAllAdminsCoobradores().stream()
                    .filter(a -> adminCubreEmpleado(a, ciudadesEmpleado))
                    .toList();
            List<Usuario> result = new java.util.ArrayList<>(usuarioRepository.findAllSuperAdmins());
            result.addAll(adminsCoo);
            return result;
        }
        return usuarioRepository.findAllAdmins();
    }

    private boolean adminCubreEmpleado(Usuario admin, List<String> ciudadesEmpleado) {
        List<String> ciudadesAdmin = admin.getCiudades();
        if (ciudadesAdmin == null || ciudadesAdmin.isEmpty()) {
            return true;
        }
        if (ciudadesEmpleado == null || ciudadesEmpleado.isEmpty()) {
            return false;
        }
        return ciudadesAdmin.stream().anyMatch(ciudadesEmpleado::contains);
    }
}
