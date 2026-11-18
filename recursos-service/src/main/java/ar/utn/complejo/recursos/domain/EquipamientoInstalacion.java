package ar.utn.complejo.recursos.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "equipamiento_instalacion")
@Getter
@Setter
@NoArgsConstructor
public class EquipamientoInstalacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instalacion_id")
    private Instalacion instalacion;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false)
    private int cantidad;
}
