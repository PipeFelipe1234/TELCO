package com.practica.backend.dto;

import com.practica.backend.entity.Usuario;
import com.practica.backend.entity.Zona;
import java.util.List;
import java.util.Set;

/**
 * DTO extendido de Usuario que incluye las zonas asignadas
 */
public record UsuarioConZonasResponse(
        Long id,
        String identificacion,
        String nombre,
        String email,
        String rol,
        String foto,
        String telefono,
        String cargo,
        List<ZonaSimpleResponse> zonasAsignadas) {

    /**
     * DTO simplificado de Zona para incluir en la respuesta
     */
    public record ZonaSimpleResponse(
            Long id,
            String nombre,
            String color) {

        public static ZonaSimpleResponse fromEntity(Zona zona) {
            return new ZonaSimpleResponse(
                    zona.getId(),
                    zona.getNombre(),
                    zona.getColor());
        }
    }

    public static UsuarioConZonasResponse fromEntity(Usuario usuario) {
        List<ZonaSimpleResponse> zonas = List.of();

        if (usuario.getZonasAsignadas() != null && !usuario.getZonasAsignadas().isEmpty()) {
            zonas = usuario.getZonasAsignadas().stream()
                    .map(ZonaSimpleResponse::fromEntity)
                    .toList();
        }

        return new UsuarioConZonasResponse(
                usuario.getId(),
                usuario.getIdentificacion(),
                usuario.getNombre(),
                usuario.getEmail(),
                usuario.getRol(),
                usuario.getFoto(),
                usuario.getTelefono(),
                usuario.getCargo(),
                zonas);
    }
}
