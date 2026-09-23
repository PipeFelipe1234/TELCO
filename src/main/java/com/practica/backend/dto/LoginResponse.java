package com.practica.backend.dto;

public record LoginResponse(
        String token,
        String identificacion,
        String nombre,
        String rol,
        String foto,
        String cargo) {
}
