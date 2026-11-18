package ar.utn.complejo.eventos.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Instalación usada por el evento, con el precio aplicado al momento de la solicitud (no se recalcula si cambia el catálogo). */
@Entity
@Table(name = "evento_instalacion")
@Getter
@Setter
@NoArgsConstructor
public class EventoInstalacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evento_id")
    private Evento evento;

    /** Referencia a recursos-service, sin clave foránea (bases separadas). */
    @Column(name = "instalacion_id", nullable = false)
    private Long instalacionId;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(name = "aire_libre", nullable = false)
    private boolean aireLibre;

    @Column(name = "tarifa_hora", nullable = false, precision = 12, scale = 2)
    private BigDecimal tarifaHora;

    @Column(name = "horas_facturadas", nullable = false)
    private long horasFacturadas;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal subtotal;
}
