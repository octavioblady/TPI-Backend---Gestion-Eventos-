package ar.utn.complejo.recursos.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** Unidades comprometidas de un servicio en una fecha. Disponible = stock_total - cantidad_reservada. */
@Entity
@Table(name = "stock_servicio_fecha",
        uniqueConstraints = @UniqueConstraint(name = "uk_stock_servicio_fecha", columnNames = {"servicio_id", "fecha"}))
@Getter
@Setter
@NoArgsConstructor
public class StockServicioFecha {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "servicio_id", nullable = false)
    private Long servicioId;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(name = "cantidad_reservada", nullable = false)
    private int cantidadReservada;
}
