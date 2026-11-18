package ar.utn.complejo.recursos.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.DayOfWeek;
import java.time.LocalTime;

@Entity
@Table(name = "horario_instalacion",
        uniqueConstraints = @UniqueConstraint(name = "uk_horario_instalacion_dia", columnNames = {"instalacion_id", "dia"}))
@Getter
@Setter
@NoArgsConstructor
public class HorarioInstalacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instalacion_id")
    private Instalacion instalacion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private DayOfWeek dia;

    @Column(nullable = false)
    private LocalTime apertura;

    @Column(nullable = false)
    private LocalTime cierre;
}
