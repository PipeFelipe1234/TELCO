package com.practica.backend.entity;

import jakarta.persistence.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "usuarios")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String identificacion;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String rol; // ADMIN / USER

    @Column
    private String foto;

    @Column
    private String telefono;

    @Column
    private String cargo;

    /**
     * Ciudades asignadas al usuario. Valores permitidos: PASTO, IPIALES, TUMACO.
     * Se almacenan separadas por coma (ej: "PASTO,IPIALES").
     * Válido para usuarios de tipo ADMIN_TEC, ADMIN_COO, USER_TEC, USER_COO.
     */
    @Column(name = "ciudades", length = 100)
    private String ciudades;

    /**
     * Zonas asignadas al usuario.
     * Un usuario puede tener múltiples zonas donde puede trabajar.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "usuario_zonas", joinColumns = @JoinColumn(name = "usuario_id"), inverseJoinColumns = @JoinColumn(name = "zona_id"))
    private Set<Zona> zonasAsignadas = new HashSet<>();

    public Usuario() {
    }

    public Usuario(Long id, String identificacion, String nombre, String email, String rol, String foto,
            String telefono, String cargo) {
        this.id = id;
        this.identificacion = identificacion;
        this.nombre = nombre;
        this.email = email;
        this.rol = rol;
        this.foto = foto;
        this.telefono = telefono;
        this.cargo = cargo;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getIdentificacion() {
        return identificacion;
    }

    public void setIdentificacion(String identificacion) {
        this.identificacion = identificacion;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRol() {
        return rol;
    }

    public void setRol(String rol) {
        this.rol = rol;
    }

    public String getFoto() {
        return foto;
    }

    public void setFoto(String foto) {
        this.foto = foto;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public String getCargo() {
        return cargo;
    }

    public void setCargo(String cargo) {
        this.cargo = cargo;
    }

    public List<String> getCiudades() {
        if (ciudades == null || ciudades.isBlank()) {
            return List.of();
        }
        return Arrays.stream(ciudades.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    public void setCiudades(List<String> ciudadesList) {
        if (ciudadesList == null || ciudadesList.isEmpty()) {
            this.ciudades = null;
        } else {
            this.ciudades = ciudadesList.stream()
                    .map(String::toUpperCase)
                    .collect(Collectors.joining(","));
        }
    }

    public Set<Zona> getZonasAsignadas() {
        return zonasAsignadas;
    }

    public void setZonasAsignadas(Set<Zona> zonasAsignadas) {
        this.zonasAsignadas = zonasAsignadas;
    }

    /**
     * Verifica si el usuario tiene al menos una zona asignada
     */
    public boolean tieneZonasAsignadas() {
        return zonasAsignadas != null && !zonasAsignadas.isEmpty();
    }
}
