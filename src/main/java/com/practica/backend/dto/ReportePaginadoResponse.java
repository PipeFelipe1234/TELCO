package com.practica.backend.dto;

import java.util.List;

public record ReportePaginadoResponse(
        List<ReporteListaResponse> content,
        int totalElements,
        int totalPages,
        int currentPage,
        int pageSize) {
}
