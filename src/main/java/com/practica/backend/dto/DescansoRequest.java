package com.practica.backend.dto;

public record DescansoRequest(
        Double latitud,
        Double longitud,
        String fechaCreacion) {
}
