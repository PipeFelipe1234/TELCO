package com.practica.backend.service;

import com.practica.backend.dto.AgregarReporteRequest;
import com.practica.backend.dto.MarcarEntradaRequest;
import com.practica.backend.dto.MarcarSalidaRequest;
import com.practica.backend.dto.ReporteTurnoResponse;
import com.practica.backend.dto.RegistroFilterRequest;
import com.practica.backend.dto.RegistroResponse;
import com.practica.backend.entity.Registro;
import com.practica.backend.entity.RegistroReporte;
import com.practica.backend.entity.Usuario;
import com.practica.backend.repository.RegistroReporteRepository;
import com.practica.backend.repository.RegistroRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class RegistroService {

    private final RegistroRepository registroRepository;
    private final RegistroReporteRepository registroReporteRepository;
    private final NotificacionService notificacionService;
    private final GeocodingService geocodingService;
    private final RastreoZonaService rastreoZonaService;
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_DATE_TIME;
    private static final ZoneId ZONA_COLOMBIA = ZoneId.of("America/Bogota");
    private static final long VENTANA_DUPLICADO_MINUTOS = 5;
    private static final double UMBRAL_COORD_DUPLICADO = 0.0001d;

    public RegistroService(RegistroRepository registroRepository,
            RegistroReporteRepository registroReporteRepository,
            NotificacionService notificacionService,
            GeocodingService geocodingService, RastreoZonaService rastreoZonaService) {
        this.registroRepository = registroRepository;
        this.registroReporteRepository = registroReporteRepository;
        this.notificacionService = notificacionService;
        this.geocodingService = geocodingService;
        this.rastreoZonaService = rastreoZonaService;
    }

    /**
     * Parsea una fecha ISO 8601 y retorna LocalDate y LocalTime
     */
    private LocalDateTime parseISODateTime(String iso8601) {
        if (iso8601 == null || iso8601.trim().isEmpty()) {
            return null;
        }
        // Intenta primero con offset de zona (ej. "2026-05-13T21:29:00.000-05:00" de
        // Flutter)
        try {
            ZonedDateTime zdt = ZonedDateTime.parse(iso8601);
            return zdt.withZoneSameInstant(ZONA_COLOMBIA).toLocalDateTime();
        } catch (Exception ignored) {
        }
        // Intenta LocalDateTime sin offset
        try {
            return LocalDateTime.parse(iso8601, ISO_FORMATTER);
        } catch (Exception e) {
            return null;
        }
    }

    public RegistroResponse marcarEntrada(Usuario usuario, MarcarEntradaRequest request) {

        LocalDate hoy = LocalDate.now(ZONA_COLOMBIA);
        LocalTime horaActual = LocalTime.now(ZONA_COLOMBIA);

        // Validar precisión GPS
        if (request.precisionMetrosCheckin() != null && request.precisionMetrosCheckin() > 50) {
            throw new RuntimeException("Precisión GPS insuficiente en entrada");
        }

        // Si viene fechaCreacion, usarla; sino, usar la hora actual
        LocalDateTime fechaHoraRegistro = parseISODateTime(request.fechaCreacion());

        if (fechaHoraRegistro != null) {
            hoy = fechaHoraRegistro.toLocalDate();
            horaActual = fechaHoraRegistro.toLocalTime();
        }

        Registro registro = new Registro();
        registro.setUsuario(usuario);
        registro.setFecha(hoy);
        registro.setHoraEntrada(horaActual);
        registro.setLatitudCheckin(request.latitudCheckin());
        registro.setLongitudCheckin(request.longitudCheckin());
        registro.setPrecisionMetrosCheckin(request.precisionMetrosCheckin());

        // 📍 Reverse geocoding: si el frontend envía ubicación la usamos, si no,
        // llamamos a Google API
        String ubicacionEntrada = request.ubicacionEntrada();
        if (ubicacionEntrada == null || ubicacionEntrada.trim().isEmpty()) {
            ubicacionEntrada = geocodingService.obtenerDireccion(
                    request.latitudCheckin(),
                    request.longitudCheckin());
        }
        registro.setUbicacionEntrada(ubicacionEntrada);

        Registro guardado = registroRepository.save(registro);

        // 📲 ENVIAR NOTIFICACIÓN A LOS ADMINS
        enviarNotificacionEntrada(guardado);

        return mapToResponse(guardado);
    }

    public RegistroResponse marcarSalida(
            Usuario usuario,
            MarcarSalidaRequest request) {

        LocalDate fechaSalida = LocalDate.now(ZONA_COLOMBIA);
        LocalTime horaSalida = LocalTime.now(ZONA_COLOMBIA);

        // Si viene fechaCreacion, usarla para obtener la fecha correcta
        LocalDateTime fechaHoraRegistro = parseISODateTime(request.fechaCreacion());

        if (fechaHoraRegistro != null) {
            fechaSalida = fechaHoraRegistro.toLocalDate();
            horaSalida = fechaHoraRegistro.toLocalTime();
        }

        // 🔄 Buscar cualquier registro sin salida (sin importar la fecha de entrada)
        Registro registro = registroRepository
                .findUltimoRegistroSinSalida(usuario)
                .orElseThrow(() -> new RuntimeException("No hay entrada sin salida registrada"));

        // Validar precisión GPS
        if (request.precisionMetros() != null && request.precisionMetros() > 50) {
            throw new RuntimeException("Precisión GPS insuficiente en salida");
        }

        registro.setHoraSalida(horaSalida);
        registro.setLatitud(request.latitud());
        registro.setLongitud(request.longitud());
        registro.setPrecisionMetros(request.precisionMetros());

        // 📍 Reverse geocoding: si el frontend envía ubicación la usamos, si no,
        // llamamos a Google API
        String ubicacionSalida = request.ubicacionSalida();
        if (ubicacionSalida == null || ubicacionSalida.trim().isEmpty()) {
            ubicacionSalida = geocodingService.obtenerDireccion(
                    request.latitud(),
                    request.longitud());
        }
        registro.setUbicacionSalida(ubicacionSalida);

        // ⏱️ Calcular horas trabajadas considerando que pueden ser días diferentes
        LocalDateTime fechaHoraEntrada = LocalDateTime.of(registro.getFecha(), registro.getHoraEntrada());
        LocalDateTime fechaHoraSalidaFinal = LocalDateTime.of(fechaSalida, horaSalida);
        Duration duracion = Duration.between(fechaHoraEntrada, fechaHoraSalidaFinal);

        registro.setHorasTrabajadas((int) duracion.toHours());
        registro.setMinutosTrabajados((int) duracion.toMinutes());

        Registro guardado = registroRepository.save(registro);

        // 🗑️ ELIMINAR RASTREO DE ZONA (el empleado ya no está en turno)
        try {
            rastreoZonaService.eliminarRastreo(usuario);
        } catch (Exception e) {
            // No fallar si hay error en el rastreo
            // El log ya se registra en RastreoZonaService
        }

        // �📲 ENVIAR NOTIFICACIÓN A LOS ADMINS
        enviarNotificacionSalida(guardado);

        return mapToResponse(guardado);
    }

    public RegistroResponse agregarReporte(Usuario usuario, AgregarReporteRequest request) {
        // Buscar el turno actual en curso
        Registro registro = registroRepository
                .findUltimoRegistroSinSalida(usuario)
                .orElseThrow(() -> new RuntimeException("No hay entrada sin salida registrada"));

        // Validar precisión GPS
        if (request.precisionMetros() != null && request.precisionMetros() > 50) {
            throw new RuntimeException("Precisión GPS insuficiente para registrar reporte");
        }

        LocalDateTime fechaHoraReporte = parseISODateTime(request.fechaCreacion());
        if (fechaHoraReporte == null) {
            fechaHoraReporte = LocalDateTime.now(ZONA_COLOMBIA);
        }

        // Idempotencia exacta para sincronización offline: mismo usuario + mismo
        // timestamp original del frontend => mismo reporte.
        boolean yaExistePorTimestamp = registroReporteRepository
                .existsByRegistroUsuarioIdAndFechaHora(usuario.getId(), fechaHoraReporte);
        if (yaExistePorTimestamp) {
            return mapToResponse(registro);
        }

        Optional<RegistroReporte> ultimoReporteOpt = registroReporteRepository
                .findTopByRegistroOrderByFechaHoraDesc(registro);
        if (ultimoReporteOpt.isPresent()
                && esReporteDuplicado(ultimoReporteOpt.get(), request, fechaHoraReporte)) {
            // No persistimos ni notificamos cuando llega el mismo reporte repetido.
            return mapToResponse(registro);
        }

        String ubicacion = request.ubicacion();
        if ((ubicacion == null || ubicacion.trim().isEmpty())
                && request.latitud() != null
                && request.longitud() != null) {
            ubicacion = geocodingService.obtenerDireccion(request.latitud(), request.longitud());
        }

        try {
            crearReporteTurno(
                    registro,
                    request.latitud(),
                    request.longitud(),
                    request.precisionMetros(),
                    request.reporte(),
                    request.picture(),
                    ubicacion,
                    fechaHoraReporte,
                    false,
                    request.novedadId());
        } catch (DataIntegrityViolationException ex) {
            // Segunda barrera (BD): en carrera concurrente, no duplicar reporte.
            return mapToResponse(registro);
        }

        // Mantener visibilidad rápida del último reporte en campos legacy
        registro.setReporte(request.reporte());
        registro.setPicture(request.picture());
        Registro guardado = registroRepository.save(registro);

        // 📲 ENVIAR NOTIFICACIÓN AL ADMIN CORRESPONDIENTE
        enviarNotificacionReporte(guardado);

        return mapToResponse(guardado);
    }

    private boolean esReporteDuplicado(
            RegistroReporte ultimoReporte,
            AgregarReporteRequest request,
            LocalDateTime fechaHoraReporte) {
        if (ultimoReporte.getFechaHora() == null || fechaHoraReporte == null) {
            return false;
        }

        long diferenciaMinutos = Math.abs(Duration.between(ultimoReporte.getFechaHora(), fechaHoraReporte).toMinutes());
        if (diferenciaMinutos > VENTANA_DUPLICADO_MINUTOS) {
            return false;
        }

        boolean mismaFoto = Objects.equals(normalizarPicture(ultimoReporte.getPicture()),
                normalizarPicture(request.picture()));
        boolean mismoTexto = Objects.equals(normalizarTexto(ultimoReporte.getReporte()),
                normalizarTexto(request.reporte()));
        boolean mismasCoords = sonCoordenadasSimilares(
                ultimoReporte.getLatitud(),
                ultimoReporte.getLongitud(),
                request.latitud(),
                request.longitud());

        // Solo bloqueamos cuando el payload completo es esencialmente el mismo.
        return mismaFoto && mismoTexto && mismasCoords;
    }

    private String normalizarTexto(String valor) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.trim();
        return limpio.isEmpty() ? null : limpio;
    }

    private String normalizarPicture(String picture) {
        String normalizado = normalizarTexto(picture);
        if (normalizado == null) {
            return null;
        }

        int queryIndex = normalizado.indexOf('?');
        if (queryIndex >= 0) {
            normalizado = normalizado.substring(0, queryIndex);
        }

        int fragmentIndex = normalizado.indexOf('#');
        if (fragmentIndex >= 0) {
            normalizado = normalizado.substring(0, fragmentIndex);
        }

        return normalizado;
    }

    private boolean sonCoordenadasSimilares(Double latA, Double lonA, Double latB, Double lonB) {
        if (latA == null || lonA == null || latB == null || lonB == null) {
            return false;
        }

        return Math.abs(latA - latB) <= UMBRAL_COORD_DUPLICADO
                && Math.abs(lonA - lonB) <= UMBRAL_COORD_DUPLICADO;
    }

    public List<RegistroResponse> obtenerMisRegistros(Usuario usuario) {
        return registroRepository.findAllByUsuario(usuario)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    public List<RegistroResponse> obtenerTodos() {
        return registroRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    /**
     * Obtiene todos los registros filtrados según el cargo Y las ciudades del
     * admin.
     * - ADMIN sin ciudades: ve todo
     * - ADMIN_TEC: ve solo USER_TEC de sus ciudades asignadas
     * - ADMIN_COO: ve solo USER_COO de sus ciudades asignadas
     */
    public List<RegistroResponse> obtenerTodosFiltrados(Usuario admin) {
        String cargoAdmin = admin != null ? admin.getCargo() : null;
        List<Registro> registros = registroRepository.findAll();

        return registros.stream()
                .filter(r -> empleadoVisibleParaAdmin(r, cargoAdmin, admin != null ? admin.getCiudades() : List.of()))
                .map(this::mapToResponse)
                .toList();
    }

    /**
     * Filtrar registros con criterios y filtrado por cargo+ciudades del admin.
     */
    public List<RegistroResponse> filtrarRegistrosFiltrados(RegistroFilterRequest filtro, Usuario admin) {
        String cargoAdmin = admin != null ? admin.getCargo() : null;
        List<Registro> registros = registroRepository.findByFiltros(
                filtro.getFecha(),
                filtro.getIdentificacion(),
                filtro.getNombres(),
                filtro.getNovedadId());

        return registros.stream()
                .filter(r -> empleadoVisibleParaAdmin(r, cargoAdmin, admin != null ? admin.getCiudades() : List.of()))
                .map(this::mapToResponse)
                .toList();
    }

    /**
     * Determina si un registro debe ser visible para un admin según su cargo y
     * ciudades.
     * - ADMIN (o cargo null): ve todo
     * - ADMIN_TEC: ve solo USER_TEC en sus ciudades
     * - ADMIN_COO: ve solo USER_COO en sus ciudades
     */
    private boolean empleadoVisibleParaAdmin(Registro r, String cargoAdmin, List<String> ciudadesAdmin) {
        if (cargoAdmin == null || "ADMIN".equals(cargoAdmin)) {
            return true;
        }

        Usuario empleado = r.getUsuario();
        String cargoEsperado = "ADMIN_TEC".equals(cargoAdmin) ? "USER_TEC" : "USER_COO";

        if (!cargoEsperado.equals(empleado.getCargo())) {
            return false;
        }

        // Si el admin no tiene ciudades asignadas, ve todos los empleados de su cargo
        if (ciudadesAdmin == null || ciudadesAdmin.isEmpty()) {
            return true;
        }

        // El empleado debe tener al menos una ciudad en común con el admin
        List<String> ciudadesEmpleado = empleado.getCiudades();
        if (ciudadesEmpleado == null || ciudadesEmpleado.isEmpty()) {
            return false;
        }

        return ciudadesAdmin.stream().anyMatch(ciudadesEmpleado::contains);
    }

    // 🔁 Mapper centralizado
    private RegistroResponse mapToResponse(Registro r) {
        Boolean enCurso = (r.getHoraSalida() == null);
        Integer horasTrabajadas;
        Integer minutosTrabajados;

        if (enCurso) {
            // 🟢 Turno en curso - calcular horas y minutos en tiempo real
            Duration duracion = Duration.between(r.getHoraEntrada(), LocalTime.now(ZONA_COLOMBIA));
            horasTrabajadas = (int) duracion.toHours();
            minutosTrabajados = (int) duracion.toMinutes();
        } else {
            // 🔴 Turno finalizado - usar valor guardado o calcular
            if (r.getHorasTrabajadas() != null) {
                horasTrabajadas = r.getHorasTrabajadas();
            } else {
                Duration duracion = Duration.between(r.getHoraEntrada(), r.getHoraSalida());
                horasTrabajadas = (int) duracion.toHours();
            }
            if (r.getMinutosTrabajados() != null) {
                minutosTrabajados = r.getMinutosTrabajados();
            } else {
                Duration duracion = Duration.between(r.getHoraEntrada(), r.getHoraSalida());
                minutosTrabajados = (int) duracion.toMinutes();
            }
        }

        List<ReporteTurnoResponse> reportes = registroReporteRepository
                .findByRegistroOrderByFechaHoraAsc(r)
                .stream()
                .map(this::mapReporteToResponse)
                .toList();

        return new RegistroResponse(
                r.getId(),
                r.getFecha(),
                r.getHoraEntrada(),
                r.getHoraSalida(),
                r.getLatitud(),
                r.getLongitud(),
                r.getPrecisionMetros(),
                r.getLatitudCheckin(),
                r.getLongitudCheckin(),
                r.getPrecisionMetrosCheckin(),
                r.getReporte(),
                r.getPicture(),
                r.getUsuario().getIdentificacion(),
                r.getUsuario().getNombre(),
                r.getUsuario().getFoto(),
                r.getUsuario().getTelefono(),
                r.getUsuario().getCargo(),
                horasTrabajadas,
                minutosTrabajados,
                enCurso,
                r.getUbicacionEntrada(),
                r.getUbicacionSalida(),
                reportes);
    }

    private void crearReporteTurno(
            Registro registro,
            Double latitud,
            Double longitud,
            Double precisionMetros,
            String reporte,
            String picture,
            String ubicacion,
            LocalDateTime fechaHora,
            boolean esSalida,
            Long novedadId) {
        RegistroReporte reporteTurno = new RegistroReporte();
        reporteTurno.setRegistro(registro);
        reporteTurno.setLatitud(latitud);
        reporteTurno.setLongitud(longitud);
        reporteTurno.setPrecisionMetros(precisionMetros);
        reporteTurno.setReporte(reporte);
        reporteTurno.setPicture(picture);
        reporteTurno.setUbicacion(ubicacion);
        reporteTurno.setFechaHora(fechaHora);
        reporteTurno.setEsSalida(esSalida);
        reporteTurno.setNovedadId(novedadId);
        registroReporteRepository.save(reporteTurno);
    }

    private ReporteTurnoResponse mapReporteToResponse(RegistroReporte reporte) {
        String fechaHoraColombia = formatearFechaHoraColombia(reporte.getFechaHora());
        return new ReporteTurnoResponse(
                reporte.getId(),
                fechaHoraColombia,
                fechaHoraColombia,
                reporte.getLatitud(),
                reporte.getLongitud(),
                reporte.getPrecisionMetros(),
                reporte.getReporte(),
                reporte.getPicture(),
                reporte.getUbicacion(),
                reporte.getEsSalida(),
                reporte.getNovedadId());
    }

    private String formatearFechaHoraColombia(LocalDateTime fechaHora) {
        if (fechaHora == null) {
            return null;
        }
        return fechaHora.atZone(ZONA_COLOMBIA).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    // 📲 NOTIFICACIÓN DE ENTRADA
    private void enviarNotificacionEntrada(Registro registro) {
        try {
            Map<String, String> datos = new HashMap<>();
            datos.put("tipo", "ENTRADA");
            datos.put("registroId", registro.getId().toString());
            datos.put("usuarioId", registro.getUsuario().getId().toString());
            datos.put("fecha", registro.getFecha().toString());
            datos.put("hora", registro.getHoraEntrada().toString());

            String titulo = "✅ Entrada Registrada";
            String mensaje = registro.getUsuario().getNombre() + " marcó Entrada";

            notificacionService.enviarNotificacionFiltradaPorCargo(
                    registro.getUsuario().getCargo(),
                    registro.getUsuario().getCiudades(),
                    titulo, mensaje, datos);
        } catch (Exception e) {
            System.err.println("❌ Error al enviar notificación de entrada: " + e.getMessage());
        }
    }

    // 📲 NOTIFICACIÓN DE SALIDA
    private void enviarNotificacionSalida(Registro registro) {
        try {
            Map<String, String> datos = new HashMap<>();
            datos.put("tipo", "SALIDA");
            datos.put("registroId", registro.getId().toString());
            datos.put("usuarioId", registro.getUsuario().getId().toString());
            datos.put("fecha", registro.getFecha().toString());
            datos.put("hora", registro.getHoraSalida().toString());

            String titulo = "🚪 Salida Registrada";
            String mensaje = registro.getUsuario().getNombre() + " marcó Salida";

            notificacionService.enviarNotificacionFiltradaPorCargo(
                    registro.getUsuario().getCargo(),
                    registro.getUsuario().getCiudades(),
                    titulo, mensaje, datos);
        } catch (Exception e) {
            System.err.println("❌ Error al enviar notificación de salida: " + e.getMessage());
        }
    }

    // 📲 NOTIFICACIÓN DE REPORTE INTERMEDIO
    private void enviarNotificacionReporte(Registro registro) {
        try {
            Map<String, String> datos = new HashMap<>();
            datos.put("tipo", "REPORTE");
            datos.put("registroId", registro.getId().toString());
            datos.put("usuarioId", registro.getUsuario().getId().toString());
            datos.put("fecha", registro.getFecha().toString());

            String cargo = registro.getUsuario().getCargo() != null
                    ? registro.getUsuario().getCargo()
                    : "Empleado";
            String titulo = "📋 Nuevo Reporte";
            String mensaje = registro.getUsuario().getNombre() + " " + cargo + " envió un reporte";

            notificacionService.enviarNotificacionFiltradaPorCargo(
                    registro.getUsuario().getCargo(),
                    registro.getUsuario().getCiudades(),
                    titulo, mensaje, datos);
        } catch (Exception e) {
            System.err.println("❌ Error al enviar notificación de reporte: " + e.getMessage());
        }
    }
}
