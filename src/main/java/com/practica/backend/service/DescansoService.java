package com.practica.backend.service;

import com.practica.backend.dto.DescansoActualResponse;
import com.practica.backend.dto.DescansoHistorialResponse;
import com.practica.backend.dto.DescansoRequest;
import com.practica.backend.dto.DescansoResponse;
import com.practica.backend.entity.Descanso;
import com.practica.backend.entity.Registro;
import com.practica.backend.entity.TipoDescanso;
import com.practica.backend.entity.Usuario;
import com.practica.backend.repository.DescansoRepository;
import com.practica.backend.repository.RegistroRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class DescansoService {

    private static final Logger logger = LoggerFactory.getLogger(DescansoService.class);
    private static final ZoneId ZONA_COLOMBIA = ZoneId.of("America/Bogota");
    private static final int LIMITE_GLOBAL_MINUTOS = 120;

    private final DescansoRepository descansoRepository;
    private final RegistroRepository registroRepository;
    private final NotificacionService notificacionService;

    public DescansoService(DescansoRepository descansoRepository,
            RegistroRepository registroRepository,
            NotificacionService notificacionService) {
        this.descansoRepository = descansoRepository;
        this.registroRepository = registroRepository;
        this.notificacionService = notificacionService;
    }

    // ============================
    // Colaborador
    // ============================

    @Transactional
    public DescansoResponse iniciar(Usuario usuario, DescansoRequest request) {
        Registro registro = registroRepository.findUltimoRegistroSinSalida(usuario)
                .orElseThrow(() -> new DescansoException(HttpStatus.CONFLICT,
                        "No tienes un turno en curso para iniciar el descanso"));

        if (descansoRepository.findFirstByRegistroAndHoraFinIsNull(registro).isPresent()) {
            throw new DescansoException(HttpStatus.CONFLICT, "Ya tienes un descanso en curso");
        }

        Descanso descanso = new Descanso();
        descanso.setRegistro(registro);
        descanso.setIdentificacion(usuario.getIdentificacion());
        descanso.setTipo(TipoDescanso.ALMUERZO);
        descanso.setHoraInicio(resolverMomento(request));
        if (request != null) {
            descanso.setLatitudInicio(request.latitud());
            descanso.setLongitudInicio(request.longitud());
        }

        Descanso guardado = descansoRepository.save(descanso);
        logger.info("🍽️ Descanso iniciado: {} (registro #{})", usuario.getNombre(), registro.getId());

        return new DescansoResponse(guardado.getId(), aIso(guardado.getHoraInicio()), null, null, null);
    }

    @Transactional
    public DescansoResponse finalizar(Usuario usuario, DescansoRequest request) {
        Descanso descanso = buscarAbierto(usuario)
                .orElseThrow(() -> new DescansoException(HttpStatus.CONFLICT, "No tienes un descanso en curso"));

        Double lat = request != null ? request.latitud() : null;
        Double lon = request != null ? request.longitud() : null;
        cerrar(descanso, resolverMomento(request), false, lat, lon);
        descansoRepository.save(descanso);
        logger.info("🍽️ Descanso finalizado: {} ({} min)", usuario.getNombre(), descanso.getMinutosDuracion());

        return new DescansoResponse(
                descanso.getId(),
                aIso(descanso.getHoraInicio()),
                aIso(descanso.getHoraFin()),
                descanso.getMinutosDuracion(),
                descanso.getExcedioTiempoLimite());
    }

    @Transactional(readOnly = true)
    public Optional<DescansoActualResponse> obtenerActual(Usuario usuario) {
        return buscarAbierto(usuario).map(d -> {
            int limite = limiteMinutos(usuario);
            int transcurridos = minutosEntre(d.getHoraInicio(), ahora());
            return new DescansoActualResponse(
                    d.getId(),
                    d.getRegistro().getId(),
                    aIso(d.getHoraInicio()),
                    transcurridos,
                    limite,
                    d.getExcedioTiempoLimite() || transcurridos > limite);
        });
    }

    private Optional<Descanso> buscarAbierto(Usuario usuario) {
        return registroRepository.findUltimoRegistroSinSalida(usuario)
                .flatMap(descansoRepository::findFirstByRegistroAndHoraFinIsNull);
    }

    // ============================
    // Integración con el turno
    // ============================

    /**
     * Cierra un descanso abierto cuando el turno termina sin que el usuario lo
     * finalice.
     */
    @Transactional
    public void cerrarAbiertoPorSalida(Registro registro, LocalDateTime horaSalida) {
        descansoRepository.findFirstByRegistroAndHoraFinIsNull(registro).ifPresent(d -> {
            cerrar(d, horaSalida, true, null, null);
            descansoRepository.save(d);
            logger.info("🍽️ Descanso #{} cerrado automáticamente por salida", d.getId());
        });
    }

    /**
     * Minutos de descanso del registro; un descanso abierto cuenta hasta
     * {@code hasta}.
     */
    @Transactional(readOnly = true)
    public int minutosDescanso(Registro registro, LocalDateTime hasta) {
        long total = descansoRepository.sumMinutosFinalizados(registro);
        Optional<Descanso> abierto = descansoRepository.findFirstByRegistroAndHoraFinIsNull(registro);
        if (abierto.isPresent()) {
            total += minutosEntre(abierto.get().getHoraInicio(), hasta);
        }
        return (int) total;
    }

    /** Minutos de descansos ya finalizados del registro. */
    public int minutosDescansoCerrados(Registro registro) {
        return (int) descansoRepository.sumMinutosFinalizados(registro);
    }

    private void cerrar(Descanso d, LocalDateTime fin, boolean automatico, Double lat, Double lon) {
        if (fin.isBefore(d.getHoraInicio())) {
            fin = d.getHoraInicio();
        }
        int minutos = minutosEntre(d.getHoraInicio(), fin);
        d.setHoraFin(fin);
        d.setMinutosDuracion(minutos);
        d.setCerradoAutomaticamente(automatico);
        d.setLatitudFin(lat);
        d.setLongitudFin(lon);
        if (minutos > limiteMinutos(d.getRegistro().getUsuario())) {
            d.setExcedioTiempoLimite(true);
        }
    }

    // ============================
    // Exceso de tiempo (push)
    // ============================

    @Scheduled(fixedRate = 60000)
    @Transactional
    public void verificarDescansosExcedidos() {
        LocalDateTime ahora = ahora();
        for (Descanso d : descansoRepository.findAbiertosConUsuario()) {
            Usuario usuario = d.getRegistro().getUsuario();
            int limite = limiteMinutos(usuario);
            if (minutosEntre(d.getHoraInicio(), ahora) <= limite) {
                continue;
            }

            d.setExcedioTiempoLimite(true);
            if (!d.getNotificado()) {
                d.setNotificado(true);
                try {
                    notificacionService.enviarNotificacionAUsuario(
                            usuario,
                            "Descanso de almuerzo excedido",
                            "Ya llevas más de " + formatearLimite(limite)
                                    + " en tu descanso de almuerzo. Recuerda finalizarlo.",
                            Map.of(
                                    "tipo", "DESCANSO_EXCEDIDO",
                                    "descansoId", String.valueOf(d.getId()),
                                    "registroId", String.valueOf(d.getRegistro().getId())));
                } catch (Exception e) {
                    logger.error("❌ Error enviando push de descanso excedido #{}: {}", d.getId(), e.getMessage());
                }
            }
            descansoRepository.save(d);
        }
    }

    // ============================
    // Asistencia y admin
    // ============================

    @Transactional(readOnly = true)
    public Map<Long, List<Descanso>> descansosPorRegistro(Collection<Long> registroIds) {
        if (registroIds.isEmpty()) {
            return Map.of();
        }
        return descansoRepository.findByRegistroIds(registroIds).stream()
                .collect(Collectors.groupingBy(d -> d.getRegistro().getId()));
    }

    @Transactional(readOnly = true)
    public Page<DescansoHistorialResponse> historial(Usuario admin, String identificacion, String tipoUsuario,
            LocalDate fechaDesde, LocalDate fechaHasta, boolean soloExcedidos, int page, int size) {

        String cargoAdmin = admin != null ? admin.getCargo() : null;
        boolean superAdmin = cargoAdmin == null || "ADMIN".equals(cargoAdmin);
        String filtroTipo = superAdmin && tipoUsuario != null && !tipoUsuario.isBlank() ? tipoUsuario : null;
        String filtroIdent = identificacion != null && !identificacion.isBlank() ? identificacion.trim() : null;

        LocalDateTime desde = fechaDesde != null ? fechaDesde.atStartOfDay() : null;
        LocalDateTime hasta = fechaHasta != null ? fechaHasta.plusDays(1).atStartOfDay() : null;

        LocalDateTime ahora = ahora();
        List<DescansoHistorialResponse> todos = descansoRepository
                .buscarHistorial(filtroIdent, filtroTipo, desde, hasta, soloExcedidos).stream()
                .filter(d -> empleadoVisible(d.getRegistro().getUsuario(), admin))
                .map(d -> toHistorial(d, ahora))
                .toList();

        int start = Math.min(page * size, todos.size());
        int end = Math.min(start + size, todos.size());
        return new PageImpl<>(todos.subList(start, end), PageRequest.of(page, size), todos.size());
    }

    private DescansoHistorialResponse toHistorial(Descanso d, LocalDateTime ahora) {
        Usuario u = d.getRegistro().getUsuario();
        boolean enCurso = d.getHoraFin() == null;
        int minutos = enCurso ? minutosEntre(d.getHoraInicio(), ahora) : d.getMinutosDuracion();
        boolean excedio = d.getExcedioTiempoLimite() || (enCurso && minutos > limiteMinutos(u));
        return new DescansoHistorialResponse(
                d.getId(),
                d.getRegistro().getId(),
                u.getIdentificacion(),
                u.getNombre(),
                u.getCargo(),
                d.getHoraInicio().toLocalDate().toString(),
                aIso(d.getHoraInicio()),
                aIso(d.getHoraFin()),
                minutos,
                enCurso,
                excedio,
                d.getCerradoAutomaticamente());
    }

    private boolean empleadoVisible(Usuario empleado, Usuario admin) {
        String cargoAdmin = admin != null ? admin.getCargo() : null;
        if (cargoAdmin == null || "ADMIN".equals(cargoAdmin)) {
            return true;
        }
        String cargoEsperado = "ADMIN_TEC".equals(cargoAdmin) ? "USER_TEC" : "USER_COO";
        if (!cargoEsperado.equals(empleado.getCargo())) {
            return false;
        }
        List<String> ciudadesAdmin = admin.getCiudades();
        if (ciudadesAdmin == null || ciudadesAdmin.isEmpty()) {
            return true;
        }
        return empleado.getCiudades().stream().anyMatch(ciudadesAdmin::contains);
    }

    // ============================
    // Utilidades
    // ============================

    public int limiteMinutos(Usuario usuario) {
        Integer limite = usuario.getTiempoLimiteAlmuerzoMinutos();
        return limite != null && limite > 0 ? limite : LIMITE_GLOBAL_MINUTOS;
    }

    public static String aIso(LocalDateTime momento) {
        if (momento == null) {
            return null;
        }
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(momento.atZone(ZONA_COLOMBIA));
    }

    private static LocalDateTime ahora() {
        return LocalDateTime.now(ZONA_COLOMBIA).withNano(0);
    }

    private static int minutosEntre(LocalDateTime desde, LocalDateTime hasta) {
        return (int) Math.max(0, Duration.between(desde, hasta).toMinutes());
    }

    // fechaCreacion permite sincronizar acciones hechas sin conexión
    private LocalDateTime resolverMomento(DescansoRequest request) {
        if (request != null && request.fechaCreacion() != null && !request.fechaCreacion().isBlank()) {
            try {
                return ZonedDateTime.parse(request.fechaCreacion()).withZoneSameInstant(ZONA_COLOMBIA)
                        .toLocalDateTime().withNano(0);
            } catch (Exception ignored) {
            }
            try {
                return LocalDateTime.parse(request.fechaCreacion()).withNano(0);
            } catch (Exception ignored) {
            }
        }
        return ahora();
    }

    private static String formatearLimite(int minutos) {
        if (minutos % 60 == 0) {
            int horas = minutos / 60;
            return horas + (horas == 1 ? " hora" : " horas");
        }
        return minutos + " minutos";
    }
}
