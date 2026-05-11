package com.practica.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import com.practica.backend.security.ValidPhoneNumber;
import java.util.List;

public record UsuarioRequest(
                @NotBlank String identificacion,
                @NotBlank String nombre,
                @Email String email,
                @NotBlank String rol,
                String foto,
                @ValidPhoneNumber String telefono,
                String cargo,
                List<String> ciudades,
                Integer tiempoLimiteMinutos) {
}