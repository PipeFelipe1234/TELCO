package com.practica.backend.controller;

import com.practica.backend.dto.AsistenciaResponse;
import com.practica.backend.entity.Usuario;
import com.practica.backend.service.AsistenciaService;
import com.practica.backend.service.UsuarioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/asistencia")
@CrossOrigin(origins = "*")
public class AsistenciaController {

    private static final Logger logger = LoggerFactory.getLogger(AsistenciaController.class);
    private final AsistenciaService asistenciaService;
    private final UsuarioService usuarioService;

    public AsistenciaController(AsistenciaService asistenciaService, UsuarioService usuarioService) {
        this.asistenciaService = asistenciaService;
        this.usuarioService = usuarioService;
    }

    /**
     * Obtiene el resumen de asistencia del día actual
     * 
     * @param tipoUsuario Opcional: "USER_TEC" o "USER_COO" para filtrar (solo para
     *                    super admin)
     * @return AsistenciaResponse con resumen y detalles de asistencia
     */
    @GetMapping
    public ResponseEntity<AsistenciaResponse> obtenerAsistenciaHoy(
            @RequestParam(required = false) String tipoUsuario) {

        String identificacion = SecurityContextHolder.getContext().getAuthentication().getName();
        Usuario admin = usuarioService.obtenerPorIdentificacion(identificacion);

        logger.info("📊 GET /api/asistencia - Admin: {} ({}), tipoUsuario: {}",
                admin.getNombre(), admin.getCargo(), tipoUsuario);

        AsistenciaResponse respuesta = asistenciaService.obtenerAsistenciaHoy(admin, tipoUsuario);

        return ResponseEntity.ok(respuesta);
    }
}
