package com.practica.backend.repository;

import com.practica.backend.entity.Registro;
import com.practica.backend.entity.RegistroReporte;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RegistroReporteRepository extends JpaRepository<RegistroReporte, Long> {

        List<RegistroReporte> findByRegistroOrderByFechaHoraAsc(Registro registro);

        boolean existsByRegistroUsuarioIdAndFechaHora(Long usuarioId, LocalDateTime fechaHora);

        Optional<RegistroReporte> findTopByRegistroOrderByFechaHoraDesc(Registro registro);

        @Modifying
        @Transactional
        @Query("DELETE FROM RegistroReporte rr WHERE rr.registro.fecha < :fechaLimite")
        int deleteByRegistroFechaAnteriorA(@Param("fechaLimite") java.time.LocalDate fechaLimite);

        @Modifying
        @Transactional
        @Query("DELETE FROM RegistroReporte rr WHERE MONTH(rr.registro.fecha) = :mes AND YEAR(rr.registro.fecha) = :anio")
        int deleteByRegistroMesYAnio(@Param("mes") int mes, @Param("anio") int anio);

        // 📊 DASHBOARD: total de reportes agrupados por usuario en un rango de fechas
        @Query("SELECT rr.registro.usuario.id, rr.registro.usuario.nombre, COUNT(rr) " +
                        "FROM RegistroReporte rr " +
                        "WHERE rr.fechaHora >= :inicio AND rr.fechaHora <= :fin " +
                        "GROUP BY rr.registro.usuario.id, rr.registro.usuario.nombre " +
                        "ORDER BY COUNT(rr) DESC")
        List<Object[]> countReportesByUsuarioInRange(
                        @Param("inicio") LocalDateTime inicio,
                        @Param("fin") LocalDateTime fin);
}
