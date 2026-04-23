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

    // 📍 Buscar usuarios técnicos (rol USER y cargo USER_TEC)
    @Query("SELECT u FROM Usuario u WHERE u.rol = 'USER' AND u.cargo = 'USER_TEC'")
    List<Usuario> findAllTecnicos();

    // 📍 Buscar usuarios coobradores (rol USER y cargo USER_COO)
    @Query("SELECT u FROM Usuario u WHERE u.rol = 'USER' AND u.cargo = 'USER_COO'")
    List<Usuario> findAllCoobradores();

    // 📍 Buscar todos los admins (rol ADMIN)
    @Query("SELECT u FROM Usuario u WHERE u.rol = 'ADMIN'")
    List<Usuario> findAllAdmins();

    // 📍 Buscar solo SUPER ADMINs (rol ADMIN y cargo ADMIN o null)
    @Query("SELECT u FROM Usuario u WHERE u.rol = 'ADMIN' AND (u.cargo = 'ADMIN' OR u.cargo IS NULL)")
    List<Usuario> findAllSuperAdmins();

    // 📍 Buscar ADMINs Técnicos (rol ADMIN y cargo ADMIN_TEC)
    @Query("SELECT u FROM Usuario u WHERE u.rol = 'ADMIN' AND u.cargo = 'ADMIN_TEC'")
    List<Usuario> findAllAdminsTecnicos();

    // 📍 Buscar ADMINs Coobradores (rol ADMIN y cargo ADMIN_COO)
    @Query("SELECT u FROM Usuario u WHERE u.rol = 'ADMIN' AND u.cargo = 'ADMIN_COO'")
    List<Usuario> findAllAdminsCoobradores();

}
