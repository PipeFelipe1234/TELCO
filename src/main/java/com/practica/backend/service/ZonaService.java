package com.practica.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.practica.backend.dto.ZonaRequest;
import com.practica.backend.dto.ZonaResponse;
import com.practica.backend.entity.Zona;
import com.practica.backend.repository.ZonaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Servicio para gestión de zonas geográficas (geocercas).
 * Incluye algoritmo de ray-casting para verificar si un punto está dentro de un
 * polígono.
 */
@Service
public class ZonaService {

    private static final Logger logger = LoggerFactory.getLogger(ZonaService.class);
    private final ZonaRepository zonaRepository;
    private final ObjectMapper objectMapper;

    public ZonaService(ZonaRepository zonaRepository, ObjectMapper objectMapper) {
        this.zonaRepository = zonaRepository;
        this.objectMapper = objectMapper;
    }

    // ============================
    // 📍 CRUD DE ZONAS
    // ============================

    /**
     * Obtiene todas las zonas activas con coordenadas parseadas
     */
    public List<ZonaResponse> obtenerZonasActivas() {
        return zonaRepository.findByActivaTrue().stream()
                .map(this::convertirAResponse)
                .toList();
    }

    /**
     * Obtiene todas las zonas
     */
    public List<ZonaResponse> obtenerTodasLasZonas() {
        return zonaRepository.findAll().stream()
                .map(this::convertirAResponse)
                .toList();
    }

    /**
     * Obtiene una zona por ID
     */
    public ZonaResponse obtenerZonaPorId(Long id) {
        Zona zona = zonaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Zona no encontrada con ID: " + id));
        return convertirAResponse(zona);
    }

    /**
     * Crea una nueva zona
     */
    @Transactional
    public ZonaResponse crearZona(ZonaRequest request) {
        String coordenadasJson = convertirCoordenadasAJson(request.coordenadas());

        Zona zona = new Zona(
                request.nombre(),
                coordenadasJson,
                request.color() != null ? request.color() : "#FF0000");

        zona = zonaRepository.save(zona);
        logger.info("✅ Zona creada: {} con {} puntos", zona.getNombre(), request.coordenadas().size());

        return convertirAResponse(zona);
    }

    /**
     * Actualiza una zona existente
     */
    @Transactional
    public ZonaResponse actualizarZona(Long id, ZonaRequest request) {
        Zona zona = zonaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Zona no encontrada con ID: " + id));

        zona.setNombre(request.nombre());
        zona.setCoordenadas(convertirCoordenadasAJson(request.coordenadas()));
        if (request.color() != null) {
            zona.setColor(request.color());
        }

        zona = zonaRepository.save(zona);
        logger.info("✅ Zona actualizada: {}", zona.getNombre());

        return convertirAResponse(zona);
    }

    /**
     * Activa o desactiva una zona
     */
    @Transactional
    public ZonaResponse cambiarEstadoZona(Long id, boolean activa) {
        Zona zona = zonaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Zona no encontrada con ID: " + id));

        zona.setActiva(activa);
        zona = zonaRepository.save(zona);
        logger.info("📍 Zona {} {}", zona.getNombre(), activa ? "activada" : "desactivada");

        return convertirAResponse(zona);
    }

    /**
     * Elimina una zona
     */
    @Transactional
    public void eliminarZona(Long id) {
        Zona zona = zonaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Zona no encontrada con ID: " + id));

        zonaRepository.delete(zona);
        logger.info("🗑️ Zona eliminada: {}", zona.getNombre());
    }

    // ============================
    // 📦 IMPORTACIÓN DE GEOJSON
    // ============================

    /**
     * Importa zonas desde un GeoJSON FeatureCollection.
     * Formato esperado: {"type":"FeatureCollection","features":[...]}
     * Cada feature debe tener geometry.type = "Polygon"
     */
    @Transactional
    public List<ZonaResponse> importarGeoJson(String geoJsonContent) {
        try {
            JsonNode root = objectMapper.readTree(geoJsonContent);
            JsonNode features = root.get("features");

            if (features == null || !features.isArray()) {
                throw new RuntimeException("El GeoJSON no contiene un array de features válido");
            }

            List<ZonaResponse> zonasImportadas = new ArrayList<>();
            int contador = 1;

            for (JsonNode feature : features) {
                JsonNode geometry = feature.get("geometry");
                JsonNode properties = feature.get("properties");

                if (geometry == null || !"Polygon".equals(geometry.get("type").asText())) {
                    logger.warn("⚠️ Feature ignorada: no es un polígono");
                    continue;
                }

                // Obtener nombre de la zona
                String nombre = "Zona " + contador;
                if (properties != null) {
                    if (properties.has("nombre")) {
                        nombre = properties.get("nombre").asText();
                    } else if (properties.has("zona")) {
                        nombre = "Zona " + properties.get("zona").asText();
                    }
                }

                // Obtener color si está definido
                String color = "#FF0000";
                if (properties != null && properties.has("fill")) {
                    color = properties.get("fill").asText();
                }

                // Obtener coordenadas del polígono
                // GeoJSON usa [longitude, latitude], nosotros guardamos como [[lng, lat], ...]
                JsonNode coordinates = geometry.get("coordinates");
                if (coordinates != null && coordinates.isArray() && coordinates.size() > 0) {
                    // El primer array es el anillo exterior del polígono
                    JsonNode ring = coordinates.get(0);

                    // Convertir a nuestro formato JSON
                    List<List<Double>> coordenadasList = new ArrayList<>();
                    for (JsonNode coord : ring) {
                        double lng = coord.get(0).asDouble();
                        double lat = coord.get(1).asDouble();
                        coordenadasList.add(List.of(lng, lat));
                    }

                    String coordenadasJson = objectMapper.writeValueAsString(coordenadasList);

                    Zona zona = new Zona(nombre, coordenadasJson, color);
                    zona = zonaRepository.save(zona);
                    zonasImportadas.add(convertirAResponse(zona));

                    logger.info("✅ Zona importada: {} con {} puntos", nombre, coordenadasList.size());
                    contador++;
                }
            }

            logger.info("📦 Importación completada: {} zonas importadas", zonasImportadas.size());
            return zonasImportadas;

        } catch (JsonProcessingException e) {
            logger.error("❌ Error al parsear GeoJSON: {}", e.getMessage());
            throw new RuntimeException("Error al parsear GeoJSON: " + e.getMessage());
        }
    }

    // ============================
    // 🎯 ALGORITMO DE CONTAINMENT (Ray-Casting)
    // ============================

    /**
     * Verifica si un punto (lat, lng) está dentro de una zona específica.
     * Usa el algoritmo Ray-Casting (point-in-polygon).
     */
    public boolean estaDentroDeZona(double latitud, double longitud, Zona zona) {
        try {
            List<double[]> poligono = parsearCoordenadas(zona.getCoordenadas());
            return puntoEnPoligono(latitud, longitud, poligono);
        } catch (Exception e) {
            logger.error("❌ Error al verificar containment: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Busca en qué zona activa se encuentra un punto.
     * Retorna null si no está en ninguna zona.
     */
    public Zona encontrarZonaPorPunto(double latitud, double longitud) {
        List<Zona> zonasActivas = zonaRepository.findByActivaTrue();

        for (Zona zona : zonasActivas) {
            if (estaDentroDeZona(latitud, longitud, zona)) {
                return zona;
            }
        }

        return null;
    }

    /**
     * Algoritmo Ray-Casting para determinar si un punto está dentro de un polígono.
     * El polígono es una lista de puntos [lng, lat].
     * 
     * Funciona lanzando un "rayo" horizontal desde el punto hacia la derecha
     * y contando cuántas veces cruza los bordes del polígono.
     * Si cruza un número impar de veces, el punto está dentro.
     */
    private boolean puntoEnPoligono(double lat, double lng, List<double[]> poligono) {
        int n = poligono.size();
        boolean dentro = false;

        for (int i = 0, j = n - 1; i < n; j = i++) {
            // Coordenadas del polígono están en [lng, lat]
            double xi = poligono.get(i)[1]; // lat del punto i
            double yi = poligono.get(i)[0]; // lng del punto i
            double xj = poligono.get(j)[1]; // lat del punto j
            double yj = poligono.get(j)[0]; // lng del punto j

            // Verificar si el rayo horizontal cruza este borde
            if (((xi > lat) != (xj > lat)) &&
                    (lng < (yj - yi) * (lat - xi) / (xj - xi) + yi)) {
                dentro = !dentro;
            }
        }

        return dentro;
    }

    // ============================
    // 🔧 UTILIDADES
    // ============================

    /**
     * Parsea las coordenadas JSON a una lista de puntos [lng, lat]
     */
    private List<double[]> parsearCoordenadas(String coordenadasJson) {
        try {
            List<List<Double>> coords = objectMapper.readValue(
                    coordenadasJson,
                    new TypeReference<List<List<Double>>>() {
                    });

            List<double[]> resultado = new ArrayList<>();
            for (List<Double> punto : coords) {
                resultado.add(new double[] { punto.get(0), punto.get(1) });
            }
            return resultado;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error al parsear coordenadas: " + e.getMessage());
        }
    }

    /**
     * Convierte coordenadas DTO a JSON
     */
    private String convertirCoordenadasAJson(List<ZonaRequest.CoordenadaDTO> coordenadas) {
        try {
            List<List<Double>> lista = coordenadas.stream()
                    .map(c -> List.of(c.lng(), c.lat()))
                    .toList();
            return objectMapper.writeValueAsString(lista);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error al convertir coordenadas: " + e.getMessage());
        }
    }

    /**
     * Convierte una Zona a ZonaResponse con coordenadas parseadas
     */
    private ZonaResponse convertirAResponse(Zona zona) {
        try {
            List<List<Double>> coords = objectMapper.readValue(
                    zona.getCoordenadas(),
                    new TypeReference<List<List<Double>>>() {
                    });

            List<ZonaResponse.CoordenadaDTO> coordenadasDTO = coords.stream()
                    .map(c -> new ZonaResponse.CoordenadaDTO(c.get(1), c.get(0))) // [lng, lat] -> {lat, lng}
                    .toList();

            return new ZonaResponse(
                    zona.getId(),
                    zona.getNombre(),
                    coordenadasDTO,
                    zona.getColor(),
                    zona.getActiva());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error al convertir zona: " + e.getMessage());
        }
    }
}
