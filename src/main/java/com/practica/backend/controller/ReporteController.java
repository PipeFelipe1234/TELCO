package com.practica.backend.controller;

import com.practica.backend.dto.ReportePaginadoResponse;
import com.practica.backend.service.ReporteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/reportes")
@CrossOrigin(origins = "*")
public class ReporteController {

    private static final Logger logger = LoggerFactory.getLogger(ReporteController.class);
    private final ReporteService reporteService;

    public ReporteController(ReporteService reporteService) {
        this.reporteService = reporteService;
    }

    /**
     * Obtiene todos los reportes con filtros y paginación
     * 
     * @param ccCliente     Filtro por CC del cliente visitado
     * @param nombreCliente Filtro por nombre del cliente visitado
     * @param tipoUsuario   Filtro por tipo de usuario (USER_TEC, USER_COO)
     * @param estadoVisita  Filtro por estado de visita (PAGO_COMPLETO,
     *                      NO_PAGO,
     *                      etc.)
     * @param novedadId     Filtro por ID de novedad
     * @param fechaDesde    Fecha inicio del rango (formato: yyyy-MM-dd)
     * @param fechaHasta    Fecha fin del rango (formato: yyyy-MM-dd)
     * @param page          Número de página (0-indexed)
     * @param size          Cantidad de registros por página (máximo 100)
     * @return Página de reportes
     */
    @GetMapping
    public ResponseEntity<ReportePaginadoResponse> obtenerReportes(
            @RequestParam(required = false) String ccCliente,
            @RequestParam(required = false) String nombreCliente,
            @RequestParam(required = false) String tipoUsuario,
            // ⏳ COMENTADO: @RequestParam(required = false) String identificacionUsuario,
            @RequestParam(required = false) String estadoVisita,
            @RequestParam(required = false) Long novedadId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate fechaHasta,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        logger.info(
                "📊 GET /api/reportes - Filtros: ccCliente={}, nombreCliente={}, tipoUsuario={}, estadoVisita={}, novedadId={}, fechaDesde={}, fechaHasta={}, page={}, size={}",
                ccCliente, nombreCliente, tipoUsuario, estadoVisita, novedadId, fechaDesde,
                fechaHasta, page, size);

        // Validar tamaño máximo
        if (size > 100) {
            size = 100;
        }

        ReportePaginadoResponse resultado = reporteService.obtenerReportes(
                ccCliente,
                nombreCliente,
                tipoUsuario,
                // ⏳ COMENTADO: identificacionUsuario,
                estadoVisita,
                novedadId,
                fechaDesde,
                fechaHasta,
                page,
                size);

        return ResponseEntity.ok(resultado);
    }
}
