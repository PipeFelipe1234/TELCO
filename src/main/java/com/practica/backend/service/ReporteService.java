package com.practica.backend.service;

import com.practica.backend.dto.ReportePaginadoResponse;
import com.practica.backend.dto.ReporteListaResponse;
import com.practica.backend.entity.RegistroReporte;
import com.practica.backend.repository.RegistroReporteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Service
public class ReporteService {

    private static final Logger logger = LoggerFactory.getLogger(ReporteService.class);
    private final RegistroReporteRepository registroReporteRepository;

    public ReporteService(RegistroReporteRepository registroReporteRepository) {
        this.registroReporteRepository = registroReporteRepository;
    }

    @Transactional(readOnly = true)
    public ReportePaginadoResponse obtenerReportes(
            String ccCliente,
            String nombreCliente,
            String rolUsuario,
            String identificacionUsuario,
            String estadoVisita,
            Long novedadId,
            LocalDate fechaDesde,
            LocalDate fechaHasta,
            int page,
            int size) {

        logger.info(
                "📊 Buscando reportes con filtros: ccCliente={}, nombreCliente={}, tipoUsuario={}, identificacionUsuario={}, estadoVisita={}, novedadId={}, fechaDesde={}, fechaHasta={}, page={}, size={}",
                ccCliente, nombreCliente, rolUsuario, identificacionUsuario, estadoVisita, novedadId, fechaDesde,
                fechaHasta, page, size);

        // Construir Specification dinámico
        Specification<RegistroReporte> spec = Specification.where(null);

        // Filtro 1: CC Cliente
        if (ccCliente != null && !ccCliente.trim().isEmpty()) {
            spec = spec.and((root, query, cb) -> cb.like(
                    cb.lower(root.get("ccCliente")),
                    "%" + ccCliente.toLowerCase() + "%"));
        }

        // Filtro 2: Nombre Cliente
        if (nombreCliente != null && !nombreCliente.trim().isEmpty()) {
            spec = spec.and((root, query, cb) -> cb.like(
                    cb.lower(root.get("cliente")),
                    "%" + nombreCliente.toLowerCase() + "%"));
        }

        // Filtro 3: Tipo Usuario (USER_TEC, USER_COO) - busca en el campo cargo
        if (rolUsuario != null && !rolUsuario.trim().isEmpty()) {
            spec = spec.and((root, query, cb) -> cb.equal(
                    root.get("registro").get("usuario").get("cargo"),
                    rolUsuario));
        }

        // Filtro 3.5: Identificación Usuario (cédula del cobrador/técnico)
        if (identificacionUsuario != null && !identificacionUsuario.trim().isEmpty()) {
            spec = spec.and((root, query, cb) -> cb.like(
                    cb.lower(root.get("registro").get("usuario").get("identificacion")),
                    "%" + identificacionUsuario.toLowerCase() + "%"));
        }

        // Filtro 4: Estado Visita (solo para cobradores)
        if (estadoVisita != null && !estadoVisita.trim().isEmpty()) {
            spec = spec.and((root, query, cb) -> cb.equal(
                    root.get("estadoVisita"),
                    estadoVisita));
        }

        // Filtro 5: Novedad ID
        if (novedadId != null && novedadId > 0) {
            spec = spec.and((root, query, cb) -> cb.equal(
                    root.get("novedadId"),
                    novedadId));
        }

        // Filtro 6: Rango de fechas
        LocalDateTime inicioDia = null;
        LocalDateTime finDia = null;

        if (fechaDesde != null) {
            inicioDia = LocalDateTime.of(fechaDesde, LocalTime.MIDNIGHT);
        }

        if (fechaHasta != null) {
            finDia = LocalDateTime.of(fechaHasta, LocalTime.MAX);
        }

        // Variables finales para usar en lambdas
        final LocalDateTime finalInicioDia = inicioDia;
        final LocalDateTime finalFinDia = finDia;

        if (finalInicioDia != null && finalFinDia != null) {
            spec = spec.and((root, query, cb) -> cb.between(
                    root.get("fechaHora"),
                    finalInicioDia,
                    finalFinDia));
        } else if (finalInicioDia != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(
                    root.get("fechaHora"),
                    finalInicioDia));
        } else if (finalFinDia != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(
                    root.get("fechaHora"),
                    finalFinDia));
        }

        // Paginación y ordenamiento
        Pageable pageable = PageRequest.of(page, size, Sort.by("fechaHora").descending());

        // Ejecutar query
        Page<RegistroReporte> pageResult = registroReporteRepository.findAll(spec, pageable);

        // Mapear a DTO
        var content = pageResult.getContent()
                .stream()
                .map(this::mapToReporteListaResponse)
                .toList();

        logger.info("✅ Se encontraron {} reportes de un total de {}", content.size(), pageResult.getTotalElements());

        return new ReportePaginadoResponse(
                content,
                (int) pageResult.getTotalElements(),
                pageResult.getTotalPages(),
                page,
                size);
    }

    private ReporteListaResponse mapToReporteListaResponse(RegistroReporte reporte) {
        return new ReporteListaResponse(
                reporte.getId(),
                reporte.getRegistro().getUsuario().getCargo(), // USER_COO, USER_TEC, ADMIN_COO, etc.
                reporte.getRegistro().getUsuario().getId(),
                reporte.getRegistro().getUsuario().getNombre(),
                reporte.getRegistro().getUsuario().getIdentificacion(),
                reporte.getReporte(),
                reporte.getPicture(),
                reporte.getUbicacion(),
                reporte.getFechaHora(),
                reporte.getNovedadId(),
                reporte.getCliente(),
                reporte.getCcCliente(),
                reporte.getEstadoVisita(),
                reporte.getEsSalida());
    }
}
