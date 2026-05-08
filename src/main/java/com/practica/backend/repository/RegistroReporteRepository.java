package com.practica.backend.repository;

import com.practica.backend.entity.Registro;
import com.practica.backend.entity.RegistroReporte;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RegistroReporteRepository extends JpaRepository<RegistroReporte, Long> {

    List<RegistroReporte> findByRegistroOrderByFechaHoraAsc(Registro registro);
}
