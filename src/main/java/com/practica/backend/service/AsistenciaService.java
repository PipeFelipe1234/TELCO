package com.practica.backend.service;

import com.practica.backend.dto.AsistenciaResponse;
import com.practica.backend.dto.DetallesAsistenciaResponse;
import com.practica.backend.dto.PersonalEnTurno;
import com.practica.backend.dto.PersonalFinalizado;
import com.practica.backend.dto.PersonalNoIniciado;
import com.practica.backend.dto.ResumenAsistenciaResponse;
import com.practica.backend.entity.Descanso;
import com.practica.backend.entity.Registro;
import com.practica.backend.entity.Usuario;
import com.practica.backend.repository.RegistroRepository;
import com.practica.backend.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AsistenciaService {

    private static final Logger logger = LoggerFactory.getLogger(AsistenciaService.class);
    private static final ZoneId ZONA_COLOMBIA = ZoneId.of("America/Bogota");

    private final UsuarioRepository usuarioRepository;
    private final RegistroRepository registroRepository;
    private final DescansoService descansoService;

    public AsistenciaService(UsuarioRepository usuarioRepository, RegistroRepository registroRepository,
            DescansoService descansoService) {
        this.usuarioRepository = usuarioRepository;
        this.registroRepository = registroRepository;
        this.descansoService = descansoService;
    }

    /**
     * Obtiene el resumen de asistencia del día actual
     * Filtra por rol del admin y sus ciudades asignadas
     */
    @Transactional(readOnly = true)
    public AsistenciaResponse obtenerAsistenciaHoy(Usuario admin, String tipoUsuario) {
        LocalDate hoy = LocalDate.now(ZONA_COLOMBIA);
        logger.info("📊 Obteniendo asistencia para el día {} - Admin: {} ({})", hoy, admin.getNombre(),
                admin.getCargo());

        // Obtener personal a monitorear según rol del admin
        List<Usuario> personalAMonitorear = obtenerPersonalParaAdmin(admin, tipoUsuario);
        logger.debug("📋 Personal a monitorear: {} usuarios", personalAMonitorear.size());

        // Obtener registros del día actual
        List<Registro> registrosHoy = registroRepository.findByFecha(hoy);
        // Si hay múltiples registros por usuario en el mismo día, mantener el último
        // (más reciente)
        Map<Long, Registro> registrosPorUsuario = registrosHoy.stream()
                .collect(Collectors.toMap(r -> r.getUsuario().getId(), r -> r, (existing, next) -> next));

        Map<Long, List<Descanso>> descansosPorRegistro = descansoService.descansosPorRegistro(
                registrosPorUsuario.values().stream().map(Registro::getId).toList());
        LocalDateTime ahora = LocalDateTime.now(ZONA_COLOMBIA);

        // Clasificar personal en tres categorías
        List<PersonalNoIniciado> noIniciados = new ArrayList<>();
        List<PersonalEnTurno> enTurno = new ArrayList<>();
        List<PersonalFinalizado> finalizados = new ArrayList<>();

        for (Usuario usuario : personalAMonitorear) {
            Registro registro = registrosPorUsuario.get(usuario.getId());

            if (registro == null) {
                // No ha iniciado turno
                noIniciados.add(new PersonalNoIniciado(
                        null,
                        usuario.getIdentificacion(),
                        usuario.getNombre(),
                        usuario.getCargo(),
                        extraerPrimeraCiudad(usuario.getCiudades()),
                        usuario.getFoto()));
            } else if (registro.getHoraSalida() == null) {
                // En turno
                List<Descanso> descansos = descansosPorRegistro.getOrDefault(registro.getId(), List.of());
                Descanso abierto = descansos.stream().filter(d -> d.getHoraFin() == null).findFirst().orElse(null);
                boolean excedido = abierto != null && (abierto.getExcedioTiempoLimite()
                        || Duration.between(abierto.getHoraInicio(), ahora).toMinutes() > descansoService
                                .limiteMinutos(usuario));
                enTurno.add(new PersonalEnTurno(
                        registro.getId(),
                        usuario.getIdentificacion(),
                        usuario.getNombre(),
                        usuario.getCargo(),
                        registro.getHoraEntrada().toString(),
                        extraerPrimeraCiudad(usuario.getCiudades()),
                        usuario.getFoto(),
                        (int) (registro.getReportes() != null ? registro.getReportes().size() : 0),
                        abierto != null,
                        abierto != null ? DescansoService.aIso(abierto.getHoraInicio()) : null,
                        excedido,
                        descansos.size()));
            } else {
                // Turno finalizado
                finalizados.add(new PersonalFinalizado(
                        registro.getId(),
                        usuario.getIdentificacion(),
                        usuario.getNombre(),
                        usuario.getCargo(),
                        registro.getHoraEntrada().toString(),
                        registro.getHoraSalida().toString(),
                        extraerPrimeraCiudad(usuario.getCiudades()),
                        usuario.getFoto(),
                        (int) (registro.getReportes() != null ? registro.getReportes().size() : 0),
                        false,
                        null,
                        false,
                        descansosPorRegistro.getOrDefault(registro.getId(), List.of()).size()));
            }
        }

        // Crear resumen
        int total = noIniciados.size() + enTurno.size() + finalizados.size();
        ResumenAsistenciaResponse resumen = new ResumenAsistenciaResponse(
                total,
                noIniciados.size(),
                enTurno.size(),
                finalizados.size());

        // Crear detalles
        DetallesAsistenciaResponse detalles = new DetallesAsistenciaResponse(
                noIniciados,
                enTurno,
                finalizados);

        logger.info("✅ Asistencia procesada: Total={}, NoIniciados={}, EnTurno={}, Finalizados={}",
                total, noIniciados.size(), enTurno.size(), finalizados.size());

        return new AsistenciaResponse(hoy, resumen, detalles);
    }

    /**
     * Obtiene el personal que el admin puede ver según su rol y ciudades
     */
    private List<Usuario> obtenerPersonalParaAdmin(Usuario admin, String tipoUsuario) {
        String cargoAdmin = admin.getCargo();

        // Super admin: ve TODO el personal
        if ("ADMIN".equals(cargoAdmin)) {
            if (tipoUsuario != null && !tipoUsuario.trim().isEmpty()) {
                return usuarioRepository.findByCargoAndRol(tipoUsuario, "USER");
            } else {
                return usuarioRepository.findByRol("USER");
            }
        }

        // Obtener ciudades del admin (ya es List<String>)
        List<String> ciudadesAdmin = admin.getCiudades();

        // Admin técnico: solo ve USER_TEC
        if ("ADMIN_TEC".equals(cargoAdmin)) {
            return usuarioRepository.findByCargoAndRol("USER_TEC", "USER")
                    .stream()
                    .filter(u -> estáEnCiudades(u.getCiudades(), ciudadesAdmin))
                    .collect(Collectors.toList());
        }

        // Admin colaborador: solo ve USER_COO
        if ("ADMIN_COO".equals(cargoAdmin)) {
            return usuarioRepository.findByCargoAndRol("USER_COO", "USER")
                    .stream()
                    .filter(u -> estáEnCiudades(u.getCiudades(), ciudadesAdmin))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }

    /**
     * Extrae la primera ciudad de la lista
     */
    private String extraerPrimeraCiudad(List<String> ciudades) {
        if (ciudades == null || ciudades.isEmpty()) {
            return "N/A";
        }
        return ciudades.get(0);
    }

    /**
     * Verifica si el usuario está en alguna de las ciudades permitidas
     */
    private boolean estáEnCiudades(List<String> ciudadesUsuario, List<String> ciudadesPermitidas) {
        if (ciudadesPermitidas == null || ciudadesPermitidas.isEmpty()) {
            return true; // Sin restricción de ciudades
        }
        if (ciudadesUsuario == null || ciudadesUsuario.isEmpty()) {
            return false;
        }
        return ciudadesUsuario.stream()
                .anyMatch(ciudadesPermitidas::contains);
    }
}
