package com.practica.backend.repository;

import com.practica.backend.entity.RastreoZona;
import com.practica.backend.entity.Usuario;
import com.practica.backend.entity.Zona;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RastreoZonaRepository extends JpaRepository<RastreoZona, Long> {

    // Buscar rastreo por empleado
    Optional<RastreoZona> findByEmpleado(Usuario empleado);

    // Buscar rastreo por empleado ID
    @Query("SELECT r FROM RastreoZona r WHERE r.empleado.id = :empleadoId")
    Optional<RastreoZona> findByEmpleadoId(@Param("empleadoId") Long empleadoId);

    // Buscar todos los rastreos en una zona específica
    List<RastreoZona> findByZonaActual(Zona zona);

    // Buscar rastreos con estado PREOCUPANTE
    @Query("SELECT r FROM RastreoZona r WHERE r.estadoTiempo = 'PREOCUPANTE'")
    List<RastreoZona> findByEstadoPreocupante();

    // Buscar rastreos activos (con zona actual)
    @Query("SELECT r FROM RastreoZona r WHERE r.zonaActual IS NOT NULL")
    List<RastreoZona> findAllConZonaActual();

    // Obtener todos los rastreos ordenados por estado (PREOCUPANTE primero)
    @Query("SELECT r FROM RastreoZona r ORDER BY " +
            "CASE r.estadoTiempo WHEN 'PREOCUPANTE' THEN 1 WHEN 'NORMAL' THEN 2 ELSE 3 END")
    List<RastreoZona> findAllOrderByEstado();
}
