package com.practica.backend.dto;

public record PersonalNoIniciado(
                Long registroId,
                String identificacion,
                String nombre,
                String cargo,
                String ciudad,
                String picture) {
}
