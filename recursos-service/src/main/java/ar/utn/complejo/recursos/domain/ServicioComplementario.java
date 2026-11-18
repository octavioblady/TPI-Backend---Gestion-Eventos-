package ar.utn.complejo.recursos.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "servicio_complementario")
@Getter
@Setter
@NoArgsConstructor
public class ServicioComplementario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String nombre;

    @Column(length = 500)
    private String descripcion;

    /** Precio por unidad y por evento. */
    @Column(name = "precio_unitario", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioUnitario;

    /** Máximo de unidades disponibles por día. */
    @Column(name = "stock_total", nullable = false)
    private int stockTotal;

    @Column(nullable = false)
    private boolean activo = true;
}
