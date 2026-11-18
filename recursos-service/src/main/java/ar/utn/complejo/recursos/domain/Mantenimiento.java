package ar.utn.complejo.recursos.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "mantenimiento")
@Getter
@Setter
@NoArgsConstructor
public class Mantenimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "instalacion_id", nullable = false)
    private Long instalacionId;

    @Column(nullable = false)
    private LocalDateTime desde;

    @Column(nullable = false)
    private LocalDateTime hasta;

    @Column(length = 300)
    private String motivo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private EstadoMantenimiento estado = EstadoMantenimiento.ACTIVO;
}
