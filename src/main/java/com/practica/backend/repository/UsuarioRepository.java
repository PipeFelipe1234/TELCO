package com.practica.backend.repository;

import com.practica.backend.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByIdentificacion(String identificacion);

    // 📍 Buscar usuarios por rol
    List<Usuario> findByRol(String rol);

    // 📍 Buscar el primer admin del sistema
    @Query("SELECT u FROM Usuario u WHERE u.rol = :rol ORDER BY u.id ASC")
    List<Usuario> findByRolOrderByIdAsc(@Param("rol") String rol);

}
