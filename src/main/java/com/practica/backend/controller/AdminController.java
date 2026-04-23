package com.practica.backend.controller;

import com.practica.backend.dto.AsignarZonasRequest;
import com.practica.backend.dto.ExportRequest;
import com.practica.backend.dto.GeolocalizacionExportRequest;
import com.practica.backend.dto.GeolocalizacionHistorialResponse;
import com.practica.backend.dto.RegistroFilterRequest;
import com.practica.backend.dto.SolicitudUbicacionResponse;
import com.practica.backend.dto.UsuarioConZonasResponse;
import com.practica.backend.dto.UsuarioUpdateRequest;
import com.practica.backend.entity.Usuario;
import com.practica.backend.service.ExportService;
import com.practica.backend.service.GeolocalizacionExportService;
import com.practica.backend.service.GeolocalizacionService;
import com.practica.backend.service.RegistroService;
import com.practica.backend.service.ScheduledCleanupService;
import com.practica.backend.service.UsuarioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "*")
public class AdminController {

    private static final Logger logger = LoggerFactory.getLogger(AdminController.class);

    private final RegistroService registroService;
    private final UsuarioService usuarioService;
    private final ExportService exportService;
    private final ScheduledCleanupService cleanupService;
    private final GeolocalizacionService geolocalizacionService;
    private final GeolocalizacionExportService geoExportService;

    public AdminController(
            RegistroService registroService,
            UsuarioService usuarioService,
            ExportService exportService,
            ScheduledCleanupService cleanupService,
            GeolocalizacionService geolocalizacionService,
            GeolocalizacionExportService geoExportService) {
        this.registroService = registroService;
        this.usuarioService = usuarioService;
        this.exportService = exportService;
        this.cleanupService = cleanupService;
        this.geolocalizacionService = geolocalizacionService;
        this.geoExportService = geoExportService;
    }

    // 👮 VER TODOS LOS REGISTROS
    @GetMapping("/registros")
    public ResponseEntity<?> todosLosRegistros() {
        return ResponseEntity.ok(registroService.obtenerTodos());
    }

    // 👮 VER TODOS LOS USUARIOS
    @GetMapping("/usuarios")
    public ResponseEntity<?> todosLosUsuarios() {
        return ResponseEntity.ok(usuarioService.obtenerTodos());
    }

    // 👮 VER UN USUARIO ESPECÍFICO POR ID
    @GetMapping("/usuarios/{id}")
    public ResponseEntity<?> obtenerUsuarioPorId(@PathVariable Long id) {
        return ResponseEntity.ok(usuarioService.obtenerUsuarioResponsePorId(id));
    }

    // � MODIFICAR UN USUARIO POR ID (actualización parcial)
    @PutMapping("/usuarios/{id}")
    public ResponseEntity<?> actualizarUsuario(
            @PathVariable Long id,
            @RequestBody UsuarioUpdateRequest request) {
        return ResponseEntity.ok(usuarioService.actualizarUsuarioParcial(id, request));
    }

    // �🔍 FILTRAR REGISTROS POR FECHA, IDENTIFICACIÓN O NOMBRES
    @PostMapping("/registros/filtrar")
    public ResponseEntity<?> filtrarRegistros(@RequestBody RegistroFilterRequest filtro) {
        return ResponseEntity.ok(registroService.filtrarRegistros(filtro));
    }

    // ============================
    // 📤 EXPORTACIÓN DE REGISTROS (ADMIN)
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
     * Exporta registros a PDF con filtros avanzados
     * Recibe: ExportRequest con fechaInicio, fechaFin, usuarioId (opcional),
     * busqueda (opcional)
     */
    @PostMapping("/exportar/pdf")
    public ResponseEntity<byte[]> exportarPdf(@RequestBody ExportRequest request) {
        try {
            logger.info("📤 Admin exportando PDF - fechaInicio: {}, fechaFin: {}, usuarioId: {}, busqueda: {}",
                    request.fechaInicio(), request.fechaFin(), request.usuarioId(), request.busqueda());

            byte[] pdfBytes;
            String filename;

            // Si se especifican fechaInicio/fechaFin, usar nuevo método con filtros
            if (request.fechaInicio() != null || request.fechaFin() != null) {
                pdfBytes = exportService.exportarPdfAdmin(request);
                filename = "reporte_asistencia.pdf";
            } else {
                // Método legacy por mes/año
                int mes = request.mes() != null ? request.mes() : LocalDate.now().getMonthValue();
                int anio = request.anio() != null ? request.anio() : LocalDate.now().getYear();
                pdfBytes = exportService.exportarPdfAdmin(mes, anio);
                String nombreMes = exportService.getNombreMes(mes);
                filename = "Registros_Todos_" + nombreMes + "_" + anio + ".pdf";
            }

            logger.info("✅ PDF generado exitosamente");

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdfBytes);
        } catch (Exception e) {
            logger.error("❌ Error al generar PDF: {}", e.getMessage());
            throw new RuntimeException("Error al generar PDF: " + e.getMessage());
        }
    }

    /**
     * Exporta registros a Excel con filtros avanzados
     * Recibe: ExportRequest con fechaInicio, fechaFin, usuarioId (opcional),
     * busqueda (opcional)
     */
    @PostMapping("/exportar/excel")
    public ResponseEntity<byte[]> exportarExcel(@RequestBody ExportRequest request) {
        try {
            logger.info("📤 Admin exportando Excel - fechaInicio: {}, fechaFin: {}, usuarioId: {}, busqueda: {}",
                    request.fechaInicio(), request.fechaFin(), request.usuarioId(), request.busqueda());

            byte[] excelBytes;
            String filename;

            // Si se especifican fechaInicio/fechaFin, usar nuevo método con filtros
            if (request.fechaInicio() != null || request.fechaFin() != null) {
                excelBytes = exportService.exportarExcelAdmin(request);
                filename = "reporte_asistencia.xlsx";
            } else {
                // Método legacy por mes/año
                int mes = request.mes() != null ? request.mes() : LocalDate.now().getMonthValue();
                int anio = request.anio() != null ? request.anio() : LocalDate.now().getYear();
                excelBytes = exportService.exportarExcelAdmin(mes, anio);
                String nombreMes = exportService.getNombreMes(mes);
                filename = "Registros_Todos_" + nombreMes + "_" + anio + ".xlsx";
            }

            logger.info("✅ Excel generado exitosamente");

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType
                            .parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(excelBytes);
        } catch (Exception e) {
            logger.error("❌ Error al generar Excel: {}", e.getMessage());
            throw new RuntimeException("Error al generar Excel: " + e.getMessage());
        }
    }

    /**
     * Exporta TODOS los registros del mes a Word (todos los empleados) - Método
     * legacy
     */
    @PostMapping("/exportar/word")
    public ResponseEntity<byte[]> exportarWord(@RequestBody ExportRequest request) {
        try {
            int mes = request.mes() != null ? request.mes() : LocalDate.now().getMonthValue();
            int anio = request.anio() != null ? request.anio() : LocalDate.now().getYear();

            byte[] wordBytes = exportService.exportarWordAdmin(mes, anio);

            String nombreMes = exportService.getNombreMes(mes);
            String filename = "Registros_Todos_" + nombreMes + "_" + anio + ".docx";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType
                            .parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                    .body(wordBytes);
        } catch (Exception e) {
            throw new RuntimeException("Error al generar Word: " + e.getMessage());
        }
    }

    /**
     * Fuerza la eliminación de registros de un mes específico (SOLO ADMIN)
     * Útil para pruebas o limpieza manual
     */
    @DeleteMapping("/registros/limpiar/{mes}/{anio}")
    public ResponseEntity<?> forzarLimpieza(@PathVariable int mes, @PathVariable int anio) {
        int eliminados = cleanupService.forzarEliminacionMes(mes, anio);
        String nombreMes = exportService.getNombreMes(mes);
        return ResponseEntity.ok(Map.of(
                "mensaje", "Se eliminaron " + eliminados + " registros del mes de " + nombreMes + " " + anio,
                "eliminados", eliminados,
                "mes", nombreMes,
                "anio", anio));
    }

    // ============================
    // 📍 GEOLOCALIZACIÓN EN TIEMPO REAL
    // ============================

    /**
     * Admin solicita la ubicación de un empleado
     * Envía notificación silenciosa (data-only) al dispositivo del empleado
     * 
     * @param empleadoId ID del empleado a geolocalizar
     * @return solicitudId y estado
     */
    @PostMapping("/geolocalizar/{empleadoId}")
    public ResponseEntity<SolicitudUbicacionResponse> solicitarUbicacion(@PathVariable Long empleadoId) {
        logger.info("📍 Solicitud de geolocalización para empleado ID: {}", empleadoId);

        String identificacion = SecurityContextHolder.getContext().getAuthentication().getName();
        Usuario admin = usuarioService.obtenerPorIdentificacion(identificacion);

        SolicitudUbicacionResponse response = geolocalizacionService.solicitarUbicacion(admin, empleadoId);

        return ResponseEntity.ok(response);
    }

    /**
     * Admin consulta el resultado de una solicitud de ubicación (polling)
     * 
     * @param solicitudId ID de la solicitud a consultar
     * @return Estado y datos de la ubicación si ya respondió
     */
    @GetMapping("/geolocalizar/resultado/{solicitudId}")
    public ResponseEntity<SolicitudUbicacionResponse> obtenerResultadoGeolocalizacion(@PathVariable Long solicitudId) {
        logger.info("📍 Consultando resultado de solicitud ID: {}", solicitudId);

        SolicitudUbicacionResponse response = geolocalizacionService.obtenerResultado(solicitudId);

        return ResponseEntity.ok(response);
    }

    // ============================
    // 📊 HISTORIAL DE GEOLOCALIZACIONES
    // ============================

    /**
     * Obtener historial de geolocalizaciones con filtros
     */
    @PostMapping("/geolocalizaciones/historial")
    public ResponseEntity<List<GeolocalizacionHistorialResponse>> obtenerHistorialGeolocalizaciones(
            @RequestBody(required = false) GeolocalizacionExportRequest filtros) {
        logger.info("📊 Consultando historial de geolocalizaciones");
        List<GeolocalizacionHistorialResponse> historial = geoExportService.obtenerHistorial(filtros);
        return ResponseEntity.ok(historial);
    }

    /**
     * Obtener meses disponibles para exportar geolocalizaciones
     */
    @GetMapping("/geolocalizaciones/meses")
    public ResponseEntity<List<Map<String, Object>>> obtenerMesesGeolocalizaciones() {
        logger.info("📅 Consultando meses disponibles para geolocalizaciones");
        return ResponseEntity.ok(geoExportService.obtenerMesesDisponibles());
    }

    /**
     * Exportar historial de geolocalizaciones a Excel con filtros
     * Body: {"mes": 3} para marzo del año actual
     * Body: {"mes": 3, "anio": 2026} para marzo 2026
     */
    @PostMapping("/geolocalizaciones/exportar/excel")
    public ResponseEntity<byte[]> exportarGeolocalizacionesExcel(
            @RequestBody(required = false) GeolocalizacionExportRequest filtros) {
        try {
            logger.info("📊 Exportando geolocalizaciones a Excel");
            byte[] excelBytes = geoExportService.exportarExcel(filtros);

            // Generar nombre de archivo según filtros
            String filename;
            if (filtros != null && filtros.mes() != null) {
                int mes = filtros.mes();
                int anio = filtros.anio() != null ? filtros.anio() : LocalDate.now().getYear();
                String nombreMes = geoExportService.getNombreMes(mes);
                filename = "geolocalizaciones_" + nombreMes + "_" + anio + ".xlsx";
            } else {
                filename = "geolocalizaciones_" + LocalDate.now() + ".xlsx";
            }

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                    .contentType(MediaType
                            .parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(excelBytes);
        } catch (Exception e) {
            logger.error("Error exportando geolocalizaciones a Excel: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Exportar historial de geolocalizaciones a PDF con filtros
     * Body: {"mes": 3} para marzo del año actual
     * Body: {"mes": 3, "anio": 2026} para marzo 2026
     */
    @PostMapping("/geolocalizaciones/exportar/pdf")
    public ResponseEntity<byte[]> exportarGeolocalizacionesPdf(
            @RequestBody(required = false) GeolocalizacionExportRequest filtros) {
        try {
            logger.info("📄 Exportando geolocalizaciones a PDF");
            byte[] pdfBytes = geoExportService.exportarPdf(filtros);

            // Generar nombre de archivo según filtros
            String filename;
            if (filtros != null && filtros.mes() != null) {
                int mes = filtros.mes();
                int anio = filtros.anio() != null ? filtros.anio() : LocalDate.now().getYear();
                String nombreMes = geoExportService.getNombreMes(mes);
                filename = "geolocalizaciones_" + nombreMes + "_" + anio + ".pdf";
            } else {
                filename = "geolocalizaciones_" + LocalDate.now() + ".pdf";
            }

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdfBytes);
        } catch (Exception e) {
            logger.error("Error exportando geolocalizaciones a PDF: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Eliminar geolocalizaciones de un mes específico (limpieza manual)
     */
    @DeleteMapping("/geolocalizaciones/limpiar/{mes}/{anio}")
    public ResponseEntity<Map<String, Object>> limpiarGeolocalizaciones(
            @PathVariable int mes, @PathVariable int anio) {
        logger.info("🗑️ Eliminando geolocalizaciones de {}/{}", mes, anio);

        long cantidad = geolocalizacionService.eliminarPorMesYAnio(mes, anio);
        String nombreMes = geoExportService.getNombreMes(mes);

        return ResponseEntity.ok(Map.of(
                "mensaje", "Se eliminaron " + cantidad + " geolocalizaciones del mes de " + nombreMes + " " + anio,
                "eliminados", cantidad,
                "mes", nombreMes,
                "anio", anio));
    }

    /**
     * Obtener información de limpieza automática de geolocalizaciones
     */
    @GetMapping("/geolocalizaciones/info-limpieza")
    public ResponseEntity<Map<String, Object>> obtenerInfoLimpiezaGeolocalizaciones() {
        logger.info("📊 Consultando info de limpieza de geolocalizaciones");
        return ResponseEntity.ok(geolocalizacionService.obtenerInfoLimpieza());
    }

    // ============================
    // 📍 ASIGNACIÓN DE ZONAS A USUARIOS
    // ============================

    /**
     * Obtiene todos los usuarios con sus zonas asignadas
     * Filtrado según el rol del admin autenticado:
     * - ADMIN ve todos (USER_TEC + USER_COO)
     * - ADMIN_TEC ve solo técnicos
     * - ADMIN_COO ve solo coobradores
     */
    @GetMapping("/usuarios/con-zonas")
    public ResponseEntity<List<UsuarioConZonasResponse>> obtenerUsuariosConZonas() {
        String identificacion = SecurityContextHolder.getContext().getAuthentication().getName();
        Usuario admin = usuarioService.obtenerPorIdentificacion(identificacion);
        String cargoAdmin = admin != null ? admin.getCargo() : null;

        logger.info("📍 Admin con cargo {} consultando usuarios", cargoAdmin);
        return ResponseEntity.ok(usuarioService.obtenerUsuariosFiltrados(cargoAdmin));
    }

    /**
     * Obtiene un usuario con sus zonas asignadas
     */
    @GetMapping("/usuarios/{id}/zonas")
    public ResponseEntity<UsuarioConZonasResponse> obtenerUsuarioConZonas(@PathVariable Long id) {
        logger.info("📍 Consultando zonas del usuario ID: {}", id);
        return ResponseEntity.ok(usuarioService.obtenerUsuarioConZonas(id));
    }

    /**
     * Asigna zonas a un usuario (reemplaza las existentes)
     * Body: {"zonaIds": [1, 2, 3]}
     */
    @PutMapping("/usuarios/{id}/zonas")
    public ResponseEntity<UsuarioConZonasResponse> asignarZonas(
            @PathVariable Long id,
            @RequestBody AsignarZonasRequest request) {
        logger.info("📍 Asignando {} zonas al usuario ID: {}", request.zonaIds().size(), id);
        return ResponseEntity.ok(usuarioService.asignarZonas(id, request.zonaIds()));
    }

    /**
     * Agrega zonas a un usuario (sin eliminar las existentes)
     * Body: {"zonaIds": [1, 2, 3]}
     */
    @PostMapping("/usuarios/{usuarioId}/zonas")
    public ResponseEntity<UsuarioConZonasResponse> agregarZonas(
            @PathVariable Long usuarioId,
            @RequestBody AsignarZonasRequest request) {
        logger.info("📍 Agregando zonas {} al usuario {}", request.zonaIds(), usuarioId);
        return ResponseEntity.ok(usuarioService.agregarZonas(usuarioId, request.zonaIds()));
    }

    /**
     * Quita una zona de un usuario
     */
    @DeleteMapping("/usuarios/{usuarioId}/zonas/{zonaId}")
    public ResponseEntity<UsuarioConZonasResponse> quitarZona(
            @PathVariable Long usuarioId,
            @PathVariable Long zonaId) {
        logger.info("📍 Quitando zona {} del usuario {}", zonaId, usuarioId);
        return ResponseEntity.ok(usuarioService.quitarZona(usuarioId, zonaId));
    }
}
