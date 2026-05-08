package com.practica.backend.controller;

import com.practica.backend.dto.ExportRequest;
import com.practica.backend.dto.AgregarReporteRequest;
import com.practica.backend.dto.MarcarEntradaRequest;
import com.practica.backend.dto.MarcarSalidaRequest;
import com.practica.backend.dto.RegistroResponse;
import com.practica.backend.entity.Usuario;
import com.practica.backend.service.ExportService;
import com.practica.backend.service.RegistroService;
import com.practica.backend.service.ScheduledCleanupService;
import com.practica.backend.service.UsuarioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/registros")
@CrossOrigin(origins = "*")
public class RegistroController {

        private static final Logger logger = LoggerFactory.getLogger(RegistroController.class);

        private final RegistroService registroService;
        private final UsuarioService usuarioService;
        private final ExportService exportService;
        private final ScheduledCleanupService cleanupService;

        public RegistroController(
                        RegistroService registroService,
                        UsuarioService usuarioService,
                        ExportService exportService,
                        ScheduledCleanupService cleanupService) {
                this.registroService = registroService;
                this.usuarioService = usuarioService;
                this.exportService = exportService;
                this.cleanupService = cleanupService;
        }

        @PostMapping("/entrada")
        public ResponseEntity<RegistroResponse> marcarEntrada(@RequestBody MarcarEntradaRequest request) {

                String identificacion = SecurityContextHolder.getContext()
                                .getAuthentication()
                                .getName();

                Usuario usuario = usuarioService.obtenerPorIdentificacion(identificacion);

                return ResponseEntity.ok(registroService.marcarEntrada(usuario, request));
        }

        @PostMapping("/salida")
        public ResponseEntity<RegistroResponse> marcarSalida(
                        @RequestBody MarcarSalidaRequest request) {
                logger.info("📤 Iniciando marcarSalida...");

                try {
                        String identificacion = SecurityContextHolder.getContext()
                                        .getAuthentication()
                                        .getName();
                        logger.info("📤 Identificación obtenida: {}", identificacion);

                        Usuario usuario = usuarioService.obtenerPorIdentificacion(identificacion);
                        logger.info("📤 Usuario encontrado: {}", usuario.getNombre());

                        RegistroResponse response = registroService.marcarSalida(usuario, request);
                        logger.info("✅ Salida registrada exitosamente para: {}", usuario.getNombre());

                        return ResponseEntity.ok(response);
                } catch (Exception e) {
                        logger.error("❌ Error en marcarSalida: {}", e.getMessage(), e);
                        throw e;
                }
        }

        @PostMapping("/reporte")
        public ResponseEntity<RegistroResponse> agregarReporte(
                        @RequestBody AgregarReporteRequest request) {
                String identificacion = SecurityContextHolder.getContext()
                                .getAuthentication()
                                .getName();

                Usuario usuario = usuarioService.obtenerPorIdentificacion(identificacion);

                return ResponseEntity.ok(registroService.agregarReporte(usuario, request));
        }

        @GetMapping("/mis-registros")
        public ResponseEntity<?> misRegistros() {

                String identificacion = SecurityContextHolder.getContext().getAuthentication().getName();

                Usuario usuario = usuarioService.obtenerPorIdentificacion(identificacion);

                return ResponseEntity.ok(
                                registroService.obtenerMisRegistros(usuario));
        }

        // ============================
        // 📤 EXPORTACIÓN DE REGISTROS (USER)
        // ============================

        /**
         * Obtiene información sobre la próxima limpieza automática
         * Incluye advertencia si faltan 4 días hábiles o menos
         */
        @GetMapping("/limpieza/info")
        public ResponseEntity<?> obtenerInfoLimpieza() {
                return ResponseEntity.ok(cleanupService.obtenerInfoLimpieza());
        }

        /**
         * Obtiene los meses disponibles para exportar
         */
        @GetMapping("/exportar/meses")
        public ResponseEntity<?> obtenerMesesDisponibles() {
                return ResponseEntity.ok(cleanupService.obtenerMesesDisponibles());
        }

        /**
         * Exporta los registros del usuario autenticado a PDF
         * Recibe: fechaInicio y fechaFin como parámetros de query
         * Obtiene el usuarioId del token JWT del usuario autenticado
         */
        @PostMapping("/exportar/pdf")
        public ResponseEntity<byte[]> exportarPdf(
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin) {
                try {
                        String identificacion = SecurityContextHolder.getContext()
                                        .getAuthentication().getName();
                        Usuario usuario = usuarioService.obtenerPorIdentificacion(identificacion);

                        logger.info("📤 Usuario {} exportando PDF - fechaInicio: {}, fechaFin: {}",
                                        usuario.getNombre(), fechaInicio, fechaFin);

                        byte[] pdfBytes;
                        String filename;

                        // Si se especifican fechas, usar nuevo método con filtros
                        if (fechaInicio != null || fechaFin != null) {
                                pdfBytes = exportService.exportarPdfUsuario(usuario.getId(), fechaInicio, fechaFin);
                                filename = "reporte_asistencia.pdf";
                        } else {
                                // Método legacy por mes/año actual
                                int mes = LocalDate.now().getMonthValue();
                                int anio = LocalDate.now().getYear();
                                pdfBytes = exportService.exportarPdfUsuario(usuario, mes, anio);
                                String nombreMes = exportService.getNombreMes(mes);
                                filename = "Registros_" + nombreMes + "_" + anio + ".pdf";
                        }

                        logger.info("✅ PDF generado exitosamente para usuario {}", usuario.getNombre());

                        return ResponseEntity.ok()
                                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                                        "attachment; filename=\"" + filename + "\"")
                                        .contentType(MediaType.APPLICATION_PDF)
                                        .body(pdfBytes);
                } catch (Exception e) {
                        logger.error("❌ Error al generar PDF: {}", e.getMessage());
                        throw new RuntimeException("Error al generar PDF: " + e.getMessage());
                }
        }

        /**
         * Exporta los registros del usuario autenticado a Excel
         * Recibe: fechaInicio y fechaFin como parámetros de query
         * Obtiene el usuarioId del token JWT del usuario autenticado
         */
        @PostMapping("/exportar/excel")
        public ResponseEntity<byte[]> exportarExcel(
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin) {
                try {
                        String identificacion = SecurityContextHolder.getContext()
                                        .getAuthentication().getName();
                        Usuario usuario = usuarioService.obtenerPorIdentificacion(identificacion);

                        logger.info("📤 Usuario {} exportando Excel - fechaInicio: {}, fechaFin: {}",
                                        usuario.getNombre(), fechaInicio, fechaFin);

                        byte[] excelBytes;
                        String filename;

                        // Si se especifican fechas, usar nuevo método con filtros
                        if (fechaInicio != null || fechaFin != null) {
                                excelBytes = exportService.exportarExcelUsuario(usuario.getId(), fechaInicio, fechaFin);
                                filename = "reporte_asistencia.xlsx";
                        } else {
                                // Método legacy por mes/año actual
                                int mes = LocalDate.now().getMonthValue();
                                int anio = LocalDate.now().getYear();
                                excelBytes = exportService.exportarExcelUsuario(usuario, mes, anio);
                                String nombreMes = exportService.getNombreMes(mes);
                                filename = "Registros_" + nombreMes + "_" + anio + ".xlsx";
                        }

                        logger.info("✅ Excel generado exitosamente para usuario {}", usuario.getNombre());

                        return ResponseEntity.ok()
                                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                                        "attachment; filename=\"" + filename + "\"")
                                        .contentType(MediaType.parseMediaType(
                                                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                                        .body(excelBytes);
                } catch (Exception e) {
                        logger.error("❌ Error al generar Excel: {}", e.getMessage());
                        throw new RuntimeException("Error al generar Excel: " + e.getMessage());
                }
        }

        /**
         * Exporta los registros del usuario a Word - Método legacy
         */
        @PostMapping("/exportar/word")
        public ResponseEntity<byte[]> exportarWord(@RequestBody ExportRequest request) {
                try {
                        String identificacion = SecurityContextHolder.getContext()
                                        .getAuthentication().getName();
                        Usuario usuario = usuarioService.obtenerPorIdentificacion(identificacion);

                        int mes = request.mes() != null ? request.mes() : LocalDate.now().getMonthValue();
                        int anio = request.anio() != null ? request.anio() : LocalDate.now().getYear();

                        byte[] wordBytes = exportService.exportarWordUsuario(usuario, mes, anio);

                        String nombreMes = exportService.getNombreMes(mes);
                        String filename = "Registros_" + nombreMes + "_" + anio + ".docx";

                        return ResponseEntity.ok()
                                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                                        "attachment; filename=\"" + filename + "\"")
                                        .contentType(MediaType.parseMediaType(
                                                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                                        .body(wordBytes);
                } catch (Exception e) {
                        throw new RuntimeException("Error al generar Word: " + e.getMessage());
                }
        }
}
