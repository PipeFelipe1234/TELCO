package com.practica.backend.repository;

import com.practica.backend.entity.Descanso;
import com.practica.backend.entity.Registro;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DescansoRepository extends JpaRepository<Descanso, Long> {

    Optional<Descanso> findFirstByRegistroAndHoraFinIsNull(Registro registro);

    @Query("SELECT COALESCE(SUM(d.minutosDuracion), 0) FROM Descanso d WHERE d.registro = :registro AND d.horaFin IS NOT NULL")
    long sumMinutosFinalizados(@Param("registro") Registro registro);

    @Query("SELECT d FROM Descanso d WHERE d.registro.id IN :registroIds")
    List<Descanso> findByRegistroIds(@Param("registroIds") Collection<Long> registroIds);

    @Query("SELECT d FROM Descanso d JOIN FETCH d.registro r JOIN FETCH r.usuario WHERE d.horaFin IS NULL")
    List<Descanso> findAbiertosConUsuario();

    @Query("SELECT d FROM Descanso d JOIN FETCH d.registro r JOIN FETCH r.usuario u WHERE " +
            "(:identificacion IS NULL OR d.identificacion = :identificacion) AND " +
            "(:tipoUsuario IS NULL OR u.cargo = :tipoUsuario) AND " +
            "(:desde IS NULL OR d.horaInicio >= :desde) AND " +
            "(:hasta IS NULL OR d.horaInicio < :hasta) AND " +
            "(:soloExcedidos = false OR d.excedioTiempoLimite = true) " +
            "ORDER BY d.horaInicio DESC")
    List<Descanso> buscarHistorial(
            @Param("identificacion") String identificacion,
            @Param("tipoUsuario") String tipoUsuario,
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta,
            @Param("soloExcedidos") boolean soloExcedidos);
}
