package com.practica.backend.controller;

import com.practica.backend.dto.DashboardResponse;
import com.practica.backend.entity.Usuario;
import com.practica.backend.service.DashboardService;
import com.practica.backend.service.UsuarioService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "*")
public class DashboardController {

    private final DashboardService dashboardService;
    private final UsuarioService usuarioService;

    public DashboardController(DashboardService dashboardService, UsuarioService usuarioService) {
        this.dashboardService = dashboardService;
        this.usuarioService = usuarioService;
    }

    /**
     * GET /api/admin/dashboard?periodo=HOY
     * GET /api/admin/dashboard?periodo=SEMANA
     * GET /api/admin/dashboard?periodo=MES
     * GET
     * /api/admin/dashboard?periodo=RANGO&fechaInicio=2026-05-01&fechaFin=2026-05-11
     */
    @GetMapping("/dashboard")
    public ResponseEntity<DashboardResponse> getDashboard(
            @RequestParam(defaultValue = "HOY") String periodo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin) {

        String identificacion = SecurityContextHolder.getContext().getAuthentication().getName();
        Usuario admin = usuarioService.obtenerPorIdentificacion(identificacion);

        DashboardResponse response = dashboardService.obtenerDashboard(periodo, fechaInicio, fechaFin, admin);
        return ResponseEntity.ok(response);
    }
}
