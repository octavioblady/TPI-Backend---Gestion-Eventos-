package ar.utn.complejo.recursos.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "reserva_espacio")
@Getter
@Setter
@NoArgsConstructor
public class ReservaEspacio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Identificador del evento en eventos-service (sin clave foránea: bases separadas). */
    @Column(name = "evento_id", nullable = false)
    private Long eventoId;

    @Column(name = "instalacion_id", nullable = false)
    private Long instalacionId;

    @Column(nullable = false)
    private LocalDateTime inicio;

    @Column(nullable = false)
    private LocalDateTime fin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EstadoReserva estado = EstadoReserva.ACTIVA;
}
