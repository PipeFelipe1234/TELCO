package com.practica.backend.service;

import com.practica.backend.dto.UsuarioConZonasResponse;
import com.practica.backend.dto.UsuarioRequest;
import com.practica.backend.dto.UsuarioResponse;
import com.practica.backend.dto.UsuarioUpdateRequest;
import com.practica.backend.entity.Usuario;
import com.practica.backend.entity.Zona;
import com.practica.backend.repository.UsuarioRepository;
import com.practica.backend.repository.ZonaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class UsuarioService {

        private static final Logger logger = LoggerFactory.getLogger(UsuarioService.class);

        private final UsuarioRepository usuarioRepository;
        private final ZonaRepository zonaRepository;

        public UsuarioService(UsuarioRepository usuarioRepository, ZonaRepository zonaRepository) {
                this.usuarioRepository = usuarioRepository;
                this.zonaRepository = zonaRepository;
        }

        private UsuarioResponse toResponse(Usuario u) {
                return new UsuarioResponse(
                                u.getId(),
                                u.getIdentificacion(),
                                u.getNombre(),
                                u.getEmail(),
                                u.getRol(),
                                u.getFoto(),
                                u.getTelefono(),
                                u.getCargo(),
                                u.getCiudades());
        }

        public UsuarioResponse crearUsuario(UsuarioRequest request) {

                if (usuarioRepository.findByIdentificacion(request.identificacion()).isPresent()) {
                        throw new RuntimeException("La identificacion ya esta registrada");
                }

                Usuario usuario = new Usuario();
                usuario.setIdentificacion(request.identificacion());
                usuario.setNombre(request.nombre());
                usuario.setEmail(request.email());
                usuario.setRol(request.rol());
                usuario.setFoto(request.foto());
                usuario.setTelefono(request.telefono());
                usuario.setCargo(request.cargo());
                usuario.setCiudades(request.ciudades());

                Usuario guardado = usuarioRepository.save(usuario);

                return toResponse(guardado);
        }

        public UsuarioResponse actualizarUsuario(Long id, UsuarioRequest request) {
                Usuario usuario = usuarioRepository.findById(id)
                                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

                usuario.setIdentificacion(request.identificacion());
                usuario.setNombre(request.nombre());
                usuario.setEmail(request.email());
                usuario.setRol(request.rol());
                usuario.setFoto(request.foto());
                usuario.setTelefono(request.telefono());
                usuario.setCargo(request.cargo());
                usuario.setCiudades(request.ciudades());

                Usuario actualizado = usuarioRepository.save(usuario);

                return toResponse(actualizado);
        }

        public Usuario obtenerPorId(Long id) {
                return usuarioRepository.findById(id).orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        }

        public UsuarioResponse obtenerUsuarioResponsePorId(Long id) {
                Usuario u = usuarioRepository.findById(id)
                                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
                return toResponse(u);
        }

        public Usuario obtenerPorIdentificacion(String identificacion) {
                return usuarioRepository.findByIdentificacion(identificacion)
                                .orElse(null);
        }

        public UsuarioResponse obtenerUsuarioResponsePorIdentificacion(String identificacion) {
                Usuario u = usuarioRepository.findByIdentificacion(identificacion)
                                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
                return toResponse(u);
        }

        public UsuarioResponse actualizarPorIdentificacion(String identificacion, UsuarioRequest request) {

                Usuario usuario = usuarioRepository.findByIdentificacion(identificacion)
                                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

                usuario.setNombre(request.nombre());
                usuario.setEmail(request.email());
                usuario.setRol(request.rol());
                usuario.setFoto(request.foto());
                usuario.setTelefono(request.telefono());
                usuario.setCargo(request.cargo());
                usuario.setCiudades(request.ciudades());

                Usuario actualizado = usuarioRepository.save(usuario);

                return toResponse(actualizado);
        }

        public List<UsuarioResponse> obtenerTodos() {
                return usuarioRepository.findAll()
                                .stream()
                                .map(this::toResponse)
                                .toList();
        }

        /**
         * Filtra usuarios según el cargo Y las ciudades del admin autenticado.
         * - ADMIN o null: ve todos
         * - ADMIN_TEC: ve solo usuarios con cargo USER_TEC en sus ciudades
         * - ADMIN_COO: ve solo usuarios con cargo USER_COO en sus ciudades
         */
        public List<UsuarioResponse> obtenerTodosFiltrados(Usuario admin) {
                String cargoAdmin = admin != null ? admin.getCargo() : null;
                List<String> ciudadesAdmin = admin != null ? admin.getCiudades() : List.of();

                if (cargoAdmin == null || "ADMIN".equals(cargoAdmin)) {
                        return obtenerTodos();
                }

                String cargoEsperado = "ADMIN_TEC".equals(cargoAdmin) ? "USER_TEC" : "USER_COO";

                return usuarioRepository.findAll().stream()
                                .filter(u -> cargoEsperado.equals(u.getCargo()))
                                .filter(u -> usuarioEnCiudades(u, ciudadesAdmin))
                                .map(this::toResponse)
                                .toList();
        }

        /**
         * Retorna true si el usuario pertenece a al menos una de las ciudades del
         * admin.
         * Si el admin no tiene ciudades, ve todos los usuarios de su cargo.
         */
        private boolean usuarioEnCiudades(Usuario usuario, List<String> ciudadesAdmin) {
                if (ciudadesAdmin == null || ciudadesAdmin.isEmpty()) {
                        return true;
                }
                List<String> ciudadesUsuario = usuario.getCiudades();
                if (ciudadesUsuario == null || ciudadesUsuario.isEmpty()) {
                        return false;
                }
                return ciudadesAdmin.stream().anyMatch(ciudadesUsuario::contains);
        }

        /**
         * 🔄 Actualizar usuario parcialmente por ID (ADMIN)
         * Solo actualiza los campos que se envían en el request (no nulos y no vacíos)
         */
        public UsuarioResponse actualizarUsuarioParcial(Long id, UsuarioUpdateRequest request) {
                Usuario usuario = usuarioRepository.findById(id)
                                .orElseThrow(() -> new RuntimeException("Usuario no encontrado con ID: " + id));

                // Solo actualizar si el campo viene con valor
                // 🆔 Actualizar identificación (validar que no exista otra igual)
                if (request.identificacion() != null && !request.identificacion().isBlank()) {
                        // Verificar que la nueva identificación no esté en uso por OTRO usuario
                        usuarioRepository.findByIdentificacion(request.identificacion())
                                        .ifPresent(existente -> {
                                                if (!existente.getId().equals(id)) {
                                                        throw new RuntimeException(
                                                                        "La identificación ya está en uso por otro usuario");
                                                }
                                        });
                        usuario.setIdentificacion(request.identificacion());
                }
                if (request.nombre() != null && !request.nombre().isBlank()) {
                        usuario.setNombre(request.nombre());
                }
                if (request.email() != null && !request.email().isBlank()) {
                        usuario.setEmail(request.email());
                }
                if (request.rol() != null && !request.rol().isBlank()) {
                        usuario.setRol(request.rol());
                }
                if (request.foto() != null) {
                        usuario.setFoto(request.foto());
                }
                if (request.telefono() != null && !request.telefono().isBlank()) {
                        usuario.setTelefono(request.telefono());
                }
                if (request.cargo() != null) {
                        usuario.setCargo(request.cargo());
                }
                if (request.ciudades() != null) {
                        usuario.setCiudades(request.ciudades());
                }

                Usuario actualizado = usuarioRepository.save(usuario);

                return toResponse(actualizado);
        }

        // ============================
        // 📍 GESTIÓN DE ZONAS ASIGNADAS
        // ============================

        /**
         * Asigna zonas a un usuario (reemplaza las zonas existentes)
         */
        @Transactional
        public UsuarioConZonasResponse asignarZonas(Long usuarioId, List<Long> zonaIds) {
                Usuario usuario = usuarioRepository.findById(usuarioId)
                                .orElseThrow(() -> new RuntimeException("Usuario no encontrado con ID: " + usuarioId));

                // Obtener las zonas por sus IDs
                Set<Zona> nuevasZonas = new HashSet<>();
                for (Long zonaId : zonaIds) {
                        Zona zona = zonaRepository.findById(zonaId)
                                        .orElseThrow(() -> new RuntimeException(
                                                        "Zona no encontrada con ID: " + zonaId));
                        nuevasZonas.add(zona);
                }

                // Asignar las nuevas zonas (reemplaza las existentes)
                usuario.setZonasAsignadas(nuevasZonas);
                Usuario actualizado = usuarioRepository.save(usuario);

                logger.info("📍 Zonas asignadas al usuario {}: {} zonas", usuario.getNombre(), zonaIds.size());

                return UsuarioConZonasResponse.fromEntity(actualizado);
        }

        /**
         * Obtiene un usuario con sus zonas asignadas
         */
        @Transactional(readOnly = true)
        public UsuarioConZonasResponse obtenerUsuarioConZonas(Long usuarioId) {
                Usuario usuario = usuarioRepository.findById(usuarioId)
                                .orElseThrow(() -> new RuntimeException("Usuario no encontrado con ID: " + usuarioId));

                // Forzar carga de zonas (LAZY)
                usuario.getZonasAsignadas().size();

                return UsuarioConZonasResponse.fromEntity(usuario);
        }

        /**
         * Obtiene todos los usuarios con sus zonas asignadas
         */
        @Transactional(readOnly = true)
        public List<UsuarioConZonasResponse> obtenerTodosConZonas() {
                return usuarioRepository.findAll().stream()
                                .peek(u -> u.getZonasAsignadas().size()) // Forzar carga LAZY
                                .map(UsuarioConZonasResponse::fromEntity)
                                .toList();
        }

        /**
         * Agrega zonas a un usuario (sin eliminar las existentes)
         */
        @Transactional
        public UsuarioConZonasResponse agregarZonas(Long usuarioId, List<Long> zonaIds) {
                Usuario usuario = usuarioRepository.findById(usuarioId)
                                .orElseThrow(() -> new RuntimeException("Usuario no encontrado con ID: " + usuarioId));

                // Forzar carga LAZY
                usuario.getZonasAsignadas().size();

                List<Zona> zonasAAgregar = zonaRepository.findAllById(zonaIds);

                if (zonasAAgregar.size() != zonaIds.size()) {
                        throw new RuntimeException("Una o más zonas no fueron encontradas");
                }

                usuario.getZonasAsignadas().addAll(zonasAAgregar);
                Usuario actualizado = usuarioRepository.save(usuario);

                logger.info("📍 {} zonas agregadas al usuario {}", zonasAAgregar.size(), usuario.getNombre());

                return UsuarioConZonasResponse.fromEntity(actualizado);
        }

        /**
         * Elimina una zona de un usuario
         */
        @Transactional
        public UsuarioConZonasResponse quitarZona(Long usuarioId, Long zonaId) {
                Usuario usuario = usuarioRepository.findById(usuarioId)
                                .orElseThrow(() -> new RuntimeException("Usuario no encontrado con ID: " + usuarioId));

                // Forzar carga LAZY
                usuario.getZonasAsignadas().size();

                usuario.getZonasAsignadas().removeIf(z -> z.getId().equals(zonaId));
                Usuario actualizado = usuarioRepository.save(usuario);

                logger.info("📍 Zona {} removida del usuario {}", zonaId, usuario.getNombre());

                return UsuarioConZonasResponse.fromEntity(actualizado);
        }

        /**
         * Obtiene todos los usuarios técnicos (cargo USER_TEC)
         */
        public List<UsuarioConZonasResponse> obtenerTodosTecnicos() {
                return usuarioRepository.findAllTecnicos()
                                .stream()
                                .map(UsuarioConZonasResponse::fromEntity)
                                .toList();
        }

        /**
         * Obtiene todos los usuarios coobradores (cargo USER_COO)
         */
        public List<UsuarioConZonasResponse> obtenerTodosCoobradores() {
                return usuarioRepository.findAllCoobradores()
                                .stream()
                                .map(UsuarioConZonasResponse::fromEntity)
                                .toList();
        }

        /**
         * Filtra usuarios según el cargo del admin autenticado
         * - ADMIN (super admin) ve todos los usuarios
         * - ADMIN_TEC ve solo usuarios con cargo USER_TEC
         * - ADMIN_COO ve solo usuarios con cargo USER_COO
         */
        public List<UsuarioConZonasResponse> obtenerUsuariosFiltrados(String cargoAdmin) {
                if (cargoAdmin == null || "ADMIN".equals(cargoAdmin)) {
                        // SUPER ADMIN ve todos
                        return obtenerTodosConZonas();
                } else if ("ADMIN_TEC".equals(cargoAdmin)) {
                        // Admin Técnico ve solo técnicos
                        return obtenerTodosTecnicos();
                } else if ("ADMIN_COO".equals(cargoAdmin)) {
                        // Admin Coobrador ve solo coobradores
                        return obtenerTodosCoobradores();
                }
                return List.of();
        }
}
