package com.practica.backend.controller;

import com.practica.backend.dto.DescansoRequest;
import com.practica.backend.entity.Usuario;
import com.practica.backend.service.DescansoException;
import com.practica.backend.service.DescansoService;
import com.practica.backend.service.UsuarioService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
public class DescansoController {

    private final DescansoService descansoService;
    private final UsuarioService usuarioService;

    public DescansoController(DescansoService descansoService, UsuarioService usuarioService) {
        this.descansoService = descansoService;
        this.usuarioService = usuarioService;
    }

    @PostMapping("/api/registrar-descanso/iniciar")
    public ResponseEntity<?> iniciar(@RequestBody(required = false) DescansoRequest request) {
        return ResponseEntity.ok(descansoService.iniciar(usuarioAutenticado(), request));
    }

    @PostMapping("/api/registrar-descanso/finalizar")
    public ResponseEntity<?> finalizar(@RequestBody(required = false) DescansoRequest request) {
        return ResponseEntity.ok(descansoService.finalizar(usuarioAutenticado(), request));
    }

    @GetMapping("/api/registrar-descanso/actual")
    public ResponseEntity<?> actual() {
        return descansoService.obtenerActual(usuarioAutenticado())
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/api/admin/descansos")
    public ResponseEntity<?> historial(
            @RequestParam(required = false) String identificacion,
            @RequestParam(required = false) String tipoUsuario,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
            @RequestParam(defaultValue = "false") boolean soloExcedidos,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(descansoService.historial(usuarioAutenticado(), identificacion, tipoUsuario,
                fechaDesde, fechaHasta, soloExcedidos, Math.max(page, 0), Math.max(size, 1)));
    }

    @ExceptionHandler(DescansoException.class)
    public ResponseEntity<Map<String, Object>> manejarError(DescansoException e) {
        return ResponseEntity.status(e.getStatus())
                .body(Map.of("status", e.getStatus().value(), "mensaje", e.getMessage()));
    }

    private Usuario usuarioAutenticado() {
        String identificacion = SecurityContextHolder.getContext().getAuthentication().getName();
        Usuario usuario = usuarioService.obtenerPorIdentificacion(identificacion);
        if (usuario == null) {
            throw new DescansoException(org.springframework.http.HttpStatus.NOT_FOUND, "Usuario no encontrado");
        }
        return usuario;
    }
}
