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

                Usuario guardado = usuarioRepository.save(usuario);

                return new UsuarioResponse(
                                guardado.getId(),
                                guardado.getIdentificacion(),
                                guardado.getNombre(),
                                guardado.getEmail(),
                                guardado.getRol(),
                                guardado.getFoto(),
                                guardado.getTelefono(),
                                guardado.getCargo());
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

                Usuario actualizado = usuarioRepository.save(usuario);

                return new UsuarioResponse(
                                actualizado.getId(),
                                actualizado.getIdentificacion(),
                                actualizado.getNombre(),
                                actualizado.getEmail(),
                                actualizado.getRol(),
                                actualizado.getFoto(),
                                actualizado.getTelefono(),
                                actualizado.getCargo());
        }

        public Usuario obtenerPorId(Long id) {
                return usuarioRepository.findById(id).orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        }

        public UsuarioResponse obtenerUsuarioResponsePorId(Long id) {
                Usuario u = usuarioRepository.findById(id)
                                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
                return new UsuarioResponse(
                                u.getId(),
                                u.getIdentificacion(),
                                u.getNombre(),
                                u.getEmail(),
                                u.getRol(),
                                u.getFoto(),
                                u.getTelefono(),
                                u.getCargo());
        }

        public Usuario obtenerPorIdentificacion(String identificacion) {
                return usuarioRepository.findByIdentificacion(identificacion)
                                .orElse(null);
        }

        public UsuarioResponse obtenerUsuarioResponsePorIdentificacion(String identificacion) {
                Usuario u = usuarioRepository.findByIdentificacion(identificacion)
                                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
                return new UsuarioResponse(
                                u.getId(),
                                u.getIdentificacion(),
                                u.getNombre(),
                                u.getEmail(),
                                u.getRol(),
                                u.getFoto(),
                                u.getTelefono(),
                                u.getCargo());
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

                Usuario actualizado = usuarioRepository.save(usuario);

                return new UsuarioResponse(
                                actualizado.getId(),
                                actualizado.getIdentificacion(),
                                actualizado.getNombre(),
                                actualizado.getEmail(),
                                actualizado.getRol(),
                                actualizado.getFoto(),
                                actualizado.getTelefono(),
                                actualizado.getCargo());
        }

        public List<UsuarioResponse> obtenerTodos() {
                return usuarioRepository.findAll()
                                .stream()
                                .map(u -> new UsuarioResponse(
                                                u.getId(),
                                                u.getIdentificacion(),
                                                u.getNombre(),
                                                u.getEmail(),
                                                u.getRol(),
                                                u.getFoto(),
                                                u.getTelefono(),
                                                u.getCargo()))
                                .toList();
        }

        /**
         * Filtra usuarios (sin zonas) según el cargo del admin autenticado.
         * - ADMIN o null: ve todos
         * - ADMIN_TEC: ve solo usuarios con cargo USER_TEC
         * - ADMIN_COO: ve solo usuarios con cargo USER_COO
         */
        public List<UsuarioResponse> obtenerTodosFiltrados(String cargoAdmin) {
                if (cargoAdmin == null || "ADMIN".equals(cargoAdmin)) {
                        return obtenerTodos();
                }

                if ("ADMIN_TEC".equals(cargoAdmin)) {
                        return usuarioRepository.findAllTecnicos()
                                        .stream()
                                        .map(u -> new UsuarioResponse(
                                                        u.getId(),
                                                        u.getIdentificacion(),
                                                        u.getNombre(),
                                                        u.getEmail(),
                                                        u.getRol(),
                                                        u.getFoto(),
                                                        u.getTelefono(),
                                                        u.getCargo()))
                                        .toList();
                }

                if ("ADMIN_COO".equals(cargoAdmin)) {
                        return usuarioRepository.findAllCoobradores()
                                        .stream()
                                        .map(u -> new UsuarioResponse(
                                                        u.getId(),
                                                        u.getIdentificacion(),
                                                        u.getNombre(),
                                                        u.getEmail(),
                                                        u.getRol(),
                                                        u.getFoto(),
                                                        u.getTelefono(),
                                                        u.getCargo()))
                                        .toList();
                }

                return List.of();
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

                Usuario actualizado = usuarioRepository.save(usuario);

                return new UsuarioResponse(
                                actualizado.getId(),
                                actualizado.getIdentificacion(),
                                actualizado.getNombre(),
                                actualizado.getEmail(),
                                actualizado.getRol(),
                                actualizado.getFoto(),
                                actualizado.getTelefono(),
                                actualizado.getCargo());
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
