package com.practica.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servicio para hacer reverse geocoding usando Google Geocoding API.
 * Convierte coordenadas (lat, lng) en direcciones legibles.
 */
@Service
public class GeocodingService {

    private static final Logger logger = LoggerFactory.getLogger(GeocodingService.class);
    private static final String GOOGLE_GEOCODING_URL = "https://maps.googleapis.com/maps/api/geocode/json";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // Cache en memoria para evitar llamadas repetidas a Google por coordenadas
    // cercanas
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_SECONDS = 24 * 60 * 60; // 24 horas

    @Value("${google.geocoding.api-key:}")
    private String apiKey;

    public GeocodingService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Obtiene la dirección formateada a partir de coordenadas.
     * 
     * @param latitud  Latitud de la ubicación
     * @param longitud Longitud de la ubicación
     * @return Dirección formateada o coordenadas si falla
     */
    public String obtenerDireccion(Double latitud, Double longitud) {
        if (latitud == null || longitud == null) {
            return null;
        }

        String cacheKey = buildCacheKey(latitud, longitud);
        String cached = getFromCache(cacheKey);
        if (cached != null) {
            return cached;
        }

        // Si no hay API key configurada, retornar coordenadas
        if (apiKey == null || apiKey.trim().isEmpty()) {
            logger.warn("⚠️ Google Geocoding API key no configurada. Usando coordenadas.");
            return formatearCoordenadas(latitud, longitud);
        }

        try {
            // Sin filtro result_type para obtener todos los detalles disponibles
            String url = UriComponentsBuilder.fromHttpUrl(GOOGLE_GEOCODING_URL)
                    .queryParam("latlng", latitud + "," + longitud)
                    .queryParam("key", apiKey)
                    .queryParam("language", "es")
                    .build()
                    .toUriString();

            logger.info("📍 Reverse geocoding para: {}, {}", latitud, longitud);

            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);

            String status = root.path("status").asText();

            if ("OK".equals(status)) {
                JsonNode results = root.path("results");
                if (results.isArray() && results.size() > 0) {
                    String direccion = results.get(0).path("formatted_address").asText();
                    // Quitar ", Colombia" del final ya que se sobreentiende
                    direccion = quitarPais(direccion);
                    logger.info("✅ Dirección obtenida: {}", direccion);
                    putInCache(cacheKey, direccion);
                    return direccion;
                }
            } else if ("ZERO_RESULTS".equals(status)) {
                logger.warn("⚠️ Sin resultados para las coordenadas: {}, {}", latitud, longitud);
                String fallback = formatearCoordenadas(latitud, longitud);
                putInCache(cacheKey, fallback);
                return fallback;
            } else {
                logger.error("❌ Error en Geocoding API. Status: {}", status);
                String fallback = formatearCoordenadas(latitud, longitud);
                putInCache(cacheKey, fallback);
                return fallback;
            }

        } catch (Exception e) {
            logger.error("❌ Error al llamar Google Geocoding API: {}", e.getMessage());
        }

        String fallback = formatearCoordenadas(latitud, longitud);
        putInCache(cacheKey, fallback);
        return fallback;
    }

    private String buildCacheKey(Double latitud, Double longitud) {
        // Redondeo a 5 decimales (~1.1m) para agrupar coordenadas casi iguales
        double lat = Math.round(latitud * 100000.0) / 100000.0;
        double lon = Math.round(longitud * 100000.0) / 100000.0;
        return lat + "," + lon;
    }

    private String getFromCache(String key) {
        CacheEntry entry = cache.get(key);
        if (entry == null) {
            return null;
        }
        if (Instant.now().isAfter(entry.expiresAt())) {
            cache.remove(key);
            return null;
        }
        return entry.address();
    }

    private void putInCache(String key, String address) {
        cache.put(key, new CacheEntry(address, Instant.now().plusSeconds(CACHE_TTL_SECONDS)));
    }

    private record CacheEntry(String address, Instant expiresAt) {
    }

    /**
     * Formatea las coordenadas como string legible (fallback)
     */
    private String formatearCoordenadas(Double latitud, Double longitud) {
        return String.format("Lat: %.6f, Lon: %.6f", latitud, longitud);
    }

    /**
     * Quita el país del final de la dirección ya que se sobreentiende.
     * Funciona para Colombia, Ecuador y otros países de Latinoamérica.
     */
    private String quitarPais(String direccion) {
        if (direccion == null) {
            return null;
        }
        // Lista de países a quitar del final
        String[] paises = {
                ", Colombia",
                ", Ecuador",
                ", Perú",
                ", Peru",
                ", Venezuela",
                ", Chile",
                ", Argentina",
                ", Bolivia",
                ", Paraguay",
                ", Uruguay",
                ", Brasil",
                ", Mexico",
                ", México"
        };

        for (String pais : paises) {
            if (direccion.endsWith(pais)) {
                return direccion.substring(0, direccion.length() - pais.length());
            }
        }
        return direccion;
    }

    /**
     * Verifica si el servicio tiene una API key configurada
     */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.trim().isEmpty();
    }
}
