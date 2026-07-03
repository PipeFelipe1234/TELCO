package com.practica.backend.dto;

public record CloudinaryConfigResponse(
        String cloudName,
        String uploadPreset,
        String folder) {
}