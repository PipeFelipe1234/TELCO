package com.practica.backend.dto;

import java.util.List;

public record UsuarioResponse(
                Long id,
                String identificacion,
                String nombre,
                String email,
                String rol,
                String foto,
                String telefono,
                String cargo,
                List<String> ciudades,
                Integer tiempoLimiteMinutos) {
}
