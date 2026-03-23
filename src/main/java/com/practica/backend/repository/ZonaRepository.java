package com.practica.backend.repository;

import com.practica.backend.entity.Zona;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ZonaRepository extends JpaRepository<Zona, Long> {

    // Buscar zonas activas
    List<Zona> findByActivaTrue();

    // Buscar zona por nombre
    List<Zona> findByNombreContainingIgnoreCase(String nombre);

    // Contar zonas activas
    long countByActivaTrue();
}
