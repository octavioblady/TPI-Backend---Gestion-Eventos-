package ar.utn.complejo.eventos.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Pronóstico adverso detectado para un evento al aire libre (F-07). Solo advierte: no cambia el estado del evento. */
@Entity
@Table(name = "advertencia_meteorologica")
@Getter
@Setter
@NoArgsConstructor
public class AdvertenciaMeteorologica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evento_id")
    private Evento evento;

    @Column(name = "consultada_en", nullable = false)
    private LocalDateTime consultadaEn;

    @Column(name = "probabilidad_lluvia", nullable = false)
    private int probabilidadLluvia;

    @Column(name = "rafaga_viento_kmh", nullable = false)
    private double rafagaVientoKmh;

    @Column(nullable = false)
    private boolean granizo;

    @Column(nullable = false, length = 500)
    private String detalle;
}
