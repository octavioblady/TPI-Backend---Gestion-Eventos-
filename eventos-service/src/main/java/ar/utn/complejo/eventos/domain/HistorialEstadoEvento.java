package ar.utn.complejo.eventos.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Trazabilidad: una fila por cada cambio de estado. */
@Entity
@Table(name = "historial_estado_evento")
@Getter
@Setter
@NoArgsConstructor
public class HistorialEstadoEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evento_id")
    private Evento evento;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_anterior", length = 12)
    private EstadoEvento estadoAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_nuevo", nullable = false, length = 12)
    private EstadoEvento estadoNuevo;

    @Column(length = 500)
    private String motivo;

    @Column(nullable = false, length = 100)
    private String usuario;

    @Column(nullable = false)
    private LocalDateTime fecha;
}
