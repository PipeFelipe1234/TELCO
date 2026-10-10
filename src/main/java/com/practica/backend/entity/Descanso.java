package com.practica.backend.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "descansos", indexes = {
        @Index(name = "idx_descanso_registro", columnList = "registro_id"),
        @Index(name = "idx_descanso_hora_fin", columnList = "hora_fin")
})
public class Descanso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registro_id", nullable = false)
    private Registro registro;

    @Column(nullable = false)
    private String identificacion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoDescanso tipo = TipoDescanso.ALMUERZO;

    // Hora de Colombia (America/Bogota), igual que el resto de registros
    @Column(name = "hora_inicio", nullable = false)
    private LocalDateTime horaInicio;

    @Column(name = "hora_fin")
    private LocalDateTime horaFin;

    private Integer minutosDuracion;

    private Double latitudInicio;
    private Double longitudInicio;
    private Double latitudFin;
    private Double longitudFin;

    @Column(nullable = false)
    private Boolean excedioTiempoLimite = false;

    @Column(nullable = false)
    private Boolean notificado = false;

    @Column(nullable = false)
    private Boolean cerradoAutomaticamente = false;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Registro getRegistro() {
        return registro;
    }

    public void setRegistro(Registro registro) {
        this.registro = registro;
    }

    public String getIdentificacion() {
        return identificacion;
    }

    public void setIdentificacion(String identificacion) {
        this.identificacion = identificacion;
    }

    public TipoDescanso getTipo() {
        return tipo;
    }

    public void setTipo(TipoDescanso tipo) {
        this.tipo = tipo;
    }

    public LocalDateTime getHoraInicio() {
        return horaInicio;
    }

    public void setHoraInicio(LocalDateTime horaInicio) {
        this.horaInicio = horaInicio;
    }

    public LocalDateTime getHoraFin() {
        return horaFin;
    }

    public void setHoraFin(LocalDateTime horaFin) {
        this.horaFin = horaFin;
    }

    public Integer getMinutosDuracion() {
        return minutosDuracion;
    }

    public void setMinutosDuracion(Integer minutosDuracion) {
        this.minutosDuracion = minutosDuracion;
    }

    public Double getLatitudInicio() {
        return latitudInicio;
    }

    public void setLatitudInicio(Double latitudInicio) {
        this.latitudInicio = latitudInicio;
    }

    public Double getLongitudInicio() {
        return longitudInicio;
    }

    public void setLongitudInicio(Double longitudInicio) {
        this.longitudInicio = longitudInicio;
    }

    public Double getLatitudFin() {
        return latitudFin;
    }

    public void setLatitudFin(Double latitudFin) {
        this.latitudFin = latitudFin;
    }

    public Double getLongitudFin() {
        return longitudFin;
    }

    public void setLongitudFin(Double longitudFin) {
        this.longitudFin = longitudFin;
    }

    public Boolean getExcedioTiempoLimite() {
        return excedioTiempoLimite;
    }

    public void setExcedioTiempoLimite(Boolean excedioTiempoLimite) {
        this.excedioTiempoLimite = excedioTiempoLimite;
    }

    public Boolean getNotificado() {
        return notificado;
    }

    public void setNotificado(Boolean notificado) {
        this.notificado = notificado;
    }

    public Boolean getCerradoAutomaticamente() {
        return cerradoAutomaticamente;
    }

    public void setCerradoAutomaticamente(Boolean cerradoAutomaticamente) {
        this.cerradoAutomaticamente = cerradoAutomaticamente;
    }
}
