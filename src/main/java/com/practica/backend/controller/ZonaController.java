package com.practica.backend.controller;

import com.practica.backend.dto.RastreoZonaResponse;
import com.practica.backend.dto.ZonaRequest;
import com.practica.backend.dto.ZonaResponse;
import com.practica.backend.entity.Usuario;
import com.practica.backend.service.RastreoZonaService;
import com.practica.backend.service.UsuarioService;
import com.practica.backend.service.ZonaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controlador para gestión de zonas geográficas (geocercas) y rastreo de
 * empleados.
 * Todos los endpoints requieren autenticación.
 */
@RestController
@RequestMapping("/api/zonas")
@CrossOrigin(origins = "*")
public class ZonaController {

    private static final Logger logger = LoggerFactory.getLogger(ZonaController.class);

    private final ZonaService zonaService;
    private final RastreoZonaService rastreoZonaService;
    private final UsuarioService usuarioService;

    public ZonaController(ZonaService zonaService, RastreoZonaService rastreoZonaService,
            UsuarioService usuarioService) {
        this.zonaService = zonaService;
        this.rastreoZonaService = rastreoZonaService;
        this.usuarioService = usuarioService;
    }

    // ============================
    // 📍 CRUD DE ZONAS
    // ============================

    /**
     * Obtiene todas las zonas activas (para dibujar en el mapa)
     */
    @GetMapping
    public ResponseEntity<List<ZonaResponse>> obtenerZonasActivas() {
        logger.info("📍 Consultando zonas activas");
        return ResponseEntity.ok(zonaService.obtenerZonasActivas());
    }

    /**
     * Obtiene todas las zonas (incluye inactivas) - Solo admin
     */
    @GetMapping("/todas")
    public ResponseEntity<List<ZonaResponse>> obtenerTodasLasZonas() {
        logger.info("📍 Consultando todas las zonas");
        return ResponseEntity.ok(zonaService.obtenerTodasLasZonas());
    }

    /**
     * Obtiene una zona por ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<ZonaResponse> obtenerZonaPorId(@PathVariable Long id) {
        logger.info("📍 Consultando zona ID: {}", id);
        return ResponseEntity.ok(zonaService.obtenerZonaPorId(id));
    }

    /**
     * Crea una nueva zona
     */
    @PostMapping
    public ResponseEntity<ZonaResponse> crearZona(@RequestBody ZonaRequest request) {
        logger.info("📍 Creando zona: {}", request.nombre());
        return ResponseEntity.ok(zonaService.crearZona(request));
    }

    /**
     * Actualiza una zona existente
     */
    @PutMapping("/{id}")
    public ResponseEntity<ZonaResponse> actualizarZona(
            @PathVariable Long id,
            @RequestBody ZonaRequest request) {
        logger.info("📍 Actualizando zona ID: {}", id);
        return ResponseEntity.ok(zonaService.actualizarZona(id, request));
    }

    /**
     * Activa o desactiva una zona
     */
    @PatchMapping("/{id}/estado")
    public ResponseEntity<ZonaResponse> cambiarEstadoZona(
            @PathVariable Long id,
            @RequestBody Map<String, Boolean> request) {
        boolean activa = request.getOrDefault("activa", true);
        logger.info("📍 Cambiando estado de zona ID: {} a {}", id, activa ? "activa" : "inactiva");
        return ResponseEntity.ok(zonaService.cambiarEstadoZona(id, activa));
    }

    /**
     * Elimina una zona
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> eliminarZona(@PathVariable Long id) {
        logger.info("📍 Eliminando zona ID: {}", id);
        zonaService.eliminarZona(id);
        return ResponseEntity.ok(Map.of("mensaje", "Zona eliminada correctamente"));
    }

    // ============================
    // 📦 IMPORTACIÓN DE GEOJSON
    // ============================

    /**
     * Importa zonas desde un GeoJSON FeatureCollection
     * Body: El contenido del archivo GeoJSON como string
     */
    @PostMapping("/importar")
    public ResponseEntity<Map<String, Object>> importarGeoJson(@RequestBody String geoJsonContent) {
        logger.info("📦 Importando zonas desde GeoJSON");
        List<ZonaResponse> zonasImportadas = zonaService.importarGeoJson(geoJsonContent);
        return ResponseEntity.ok(Map.of(
                "mensaje", "Importación completada",
                "zonasImportadas", zonasImportadas.size(),
                "zonas", zonasImportadas));
    }

    // ============================
    // 📊 RASTREO DE EMPLEADOS EN ZONAS
    // ============================

    /**
     * Obtiene el estado de rastreo de todos los empleados.
     * Filtrado según el rol del admin autenticado:
     * - ADMIN ve todos los empleados
     * - ADMIN_TEC ve solo empleados técnicos
     * - ADMIN_COO ve solo empleados coobradores
     */
    @GetMapping("/rastreo")
    public ResponseEntity<List<RastreoZonaResponse>> obtenerTodosLosRastreos() {
        String identificacion = SecurityContextHolder.getContext().getAuthentication().getName();
        Usuario admin = usuarioService.obtenerPorIdentificacion(identificacion);
        String cargoAdmin = admin != null ? admin.getCargo() : null;

        logger.info("📊 Admin con cargo {} consultando rastreo de empleados", cargoAdmin);
        return ResponseEntity.ok(rastreoZonaService.obtenerTodosLosRastreosFiltrados(cargoAdmin));
    }

    /**
     * Obtiene solo los empleados en estado PREOCUPANTE
     * Filtrado según el rol del admin autenticado
     */
    @GetMapping("/rastreo/preocupantes")
    public ResponseEntity<List<RastreoZonaResponse>> obtenerRastreosPreocupantes() {
        String identificacion = SecurityContextHolder.getContext().getAuthentication().getName();
        Usuario admin = usuarioService.obtenerPorIdentificacion(identificacion);
        String cargoAdmin = admin != null ? admin.getCargo() : null;

        logger.info("🚨 Admin con cargo {} consultando empleados PREOCUPANTES", cargoAdmin);
        return ResponseEntity.ok(rastreoZonaService.obtenerRastreosPreocupantesFiltrados(cargoAdmin));
    }

    /**
     * Obtiene el rastreo de un empleado específico
     */
    @GetMapping("/rastreo/{empleadoId}")
    public ResponseEntity<RastreoZonaResponse> obtenerRastreoPorEmpleado(@PathVariable Long empleadoId) {
        logger.info("📊 Consultando rastreo del empleado ID: {}", empleadoId);
        return ResponseEntity.ok(rastreoZonaService.obtenerRastreoPorEmpleado(empleadoId));
    }
}
