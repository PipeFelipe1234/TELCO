package com.practica.backend.repository;

import com.practica.backend.entity.SolicitudUbicacion;
import com.practica.backend.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SolicitudUbicacionRepository extends JpaRepository<SolicitudUbicacion, Long> {

        // Buscar solicitudes pendientes de un empleado
        List<SolicitudUbicacion> findByEmpleadoAndEstado(Usuario empleado, String estado);

        // Buscar solicitudes por admin
        List<SolicitudUbicacion> findByAdminOrderByFechaSolicitudDesc(Usuario admin);

        // Buscar solicitud por ID y empleado (para validar que el empleado puede
        // responder)
        Optional<SolicitudUbicacion> findByIdAndEmpleado(Long id, Usuario empleado);

        // Buscar solicitudes pendientes que hayan expirado (más de X minutos)
        @Query("SELECT s FROM SolicitudUbicacion s WHERE s.estado = 'PENDIENTE' AND s.fechaSolicitud < :fechaLimite")
        List<SolicitudUbicacion> findSolicitudesExpiradas(@Param("fechaLimite") LocalDateTime fechaLimite);

        // Buscar última solicitud pendiente de un empleado
        @Query("SELECT s FROM SolicitudUbicacion s WHERE s.empleado = :empleado AND s.estado = 'PENDIENTE' ORDER BY s.fechaSolicitud DESC")
        List<SolicitudUbicacion> findSolicitudesPendientesByEmpleado(@Param("empleado") Usuario empleado);

        // ============================
        // 📊 HISTORIAL Y FILTROS (SOLO MANUALES - excluye automáticas)
        // ============================

        // Obtener todas las solicitudes MANUALES ordenadas por fecha descendente
        @Query("SELECT s FROM SolicitudUbicacion s WHERE (s.esAutomatica IS NULL OR s.esAutomatica = false) ORDER BY s.fechaSolicitud DESC")
        List<SolicitudUbicacion> findAllManualesOrderByFechaSolicitudDesc();

        // Obtener todas las solicitudes ordenadas por fecha descendente (legacy -
        // incluye todas)
        List<SolicitudUbicacion> findAllByOrderByFechaSolicitudDesc();

        // Obtener solicitudes MANUALES por rango de fechas
        @Query("SELECT s FROM SolicitudUbicacion s WHERE (s.esAutomatica IS NULL OR s.esAutomatica = false) AND DATE(s.fechaSolicitud) >= :fechaInicio AND DATE(s.fechaSolicitud) <= :fechaFin ORDER BY s.fechaSolicitud DESC")
        List<SolicitudUbicacion> findManualesByFechaRange(
                        @Param("fechaInicio") LocalDate fechaInicio,
                        @Param("fechaFin") LocalDate fechaFin);

        // Obtener solicitudes por rango de fechas (legacy - incluye todas)
        @Query("SELECT s FROM SolicitudUbicacion s WHERE DATE(s.fechaSolicitud) >= :fechaInicio AND DATE(s.fechaSolicitud) <= :fechaFin ORDER BY s.fechaSolicitud DESC")
        List<SolicitudUbicacion> findByFechaRange(
                        @Param("fechaInicio") LocalDate fechaInicio,
                        @Param("fechaFin") LocalDate fechaFin);

        // Obtener solicitudes de un empleado específico
        List<SolicitudUbicacion> findByEmpleadoOrderByFechaSolicitudDesc(Usuario empleado);

        // Obtener solicitudes MANUALES por mes y año (para historial y exportación)
        @Query("SELECT s FROM SolicitudUbicacion s WHERE (s.esAutomatica IS NULL OR s.esAutomatica = false) AND MONTH(s.fechaSolicitud) = :mes AND YEAR(s.fechaSolicitud) = :anio ORDER BY s.fechaSolicitud DESC")
        List<SolicitudUbicacion> findManualesByMesYAnio(@Param("mes") int mes, @Param("anio") int anio);

        // Obtener solicitudes por mes y año (legacy - incluye todas)
        @Query("SELECT s FROM SolicitudUbicacion s WHERE MONTH(s.fechaSolicitud) = :mes AND YEAR(s.fechaSolicitud) = :anio ORDER BY s.fechaSolicitud DESC")
        List<SolicitudUbicacion> findByMesYAnio(@Param("mes") int mes, @Param("anio") int anio);

        // ============================
        // 🗑️ LIMPIEZA AUTOMÁTICA
        // ============================

        // Contar solicitudes MANUALES de un mes y año específico (para advertencias de
        // limpieza)
        @Query("SELECT COUNT(s) FROM SolicitudUbicacion s WHERE (s.esAutomatica IS NULL OR s.esAutomatica = false) AND MONTH(s.fechaSolicitud) = :mes AND YEAR(s.fechaSolicitud) = :anio")
        long countManualesByMesYAnio(@Param("mes") int mes, @Param("anio") int anio);

        // Contar solicitudes de un mes y año específico (todas - incluye automáticas)
        @Query("SELECT COUNT(s) FROM SolicitudUbicacion s WHERE MONTH(s.fechaSolicitud) = :mes AND YEAR(s.fechaSolicitud) = :anio")
        long countByMesYAnio(@Param("mes") int mes, @Param("anio") int anio);

        // Eliminar solicitudes de un mes y año específico
        @Modifying
        @Transactional
        @Query("DELETE FROM SolicitudUbicacion s WHERE MONTH(s.fechaSolicitud) = :mes AND YEAR(s.fechaSolicitud) = :anio")
        int deleteByMesYAnio(@Param("mes") int mes, @Param("anio") int anio);

        // Eliminar solicitudes AUTOMÁTICAS más antiguas que una fecha límite
        @Modifying
        @Transactional
        @Query("DELETE FROM SolicitudUbicacion s WHERE s.esAutomatica = true AND s.fechaSolicitud < :fechaLimite")
        int deleteAutomaticasAnterioresA(@Param("fechaLimite") LocalDateTime fechaLimite);

        // ============================
        // 📅 MESES DISPONIBLES (SOLO MANUALES para historial)
        // ============================

        // Obtener el mes más antiguo con solicitudes MANUALES
        @Query("SELECT MIN(s.fechaSolicitud) FROM SolicitudUbicacion s WHERE (s.esAutomatica IS NULL OR s.esAutomatica = false)")
        LocalDateTime findFechaMasAntiguaManuales();

        // Obtener el mes más reciente con solicitudes MANUALES
        @Query("SELECT MAX(s.fechaSolicitud) FROM SolicitudUbicacion s WHERE (s.esAutomatica IS NULL OR s.esAutomatica = false)")
        LocalDateTime findFechaMasRecienteManuales();

        // Obtener el mes más antiguo con solicitudes (todas - legacy)
        @Query("SELECT MIN(s.fechaSolicitud) FROM SolicitudUbicacion s")
        LocalDateTime findFechaMasAntigua();

        // Obtener el mes más reciente con solicitudes (todas - legacy)
        @Query("SELECT MAX(s.fechaSolicitud) FROM SolicitudUbicacion s")
        LocalDateTime findFechaMasReciente();
}
