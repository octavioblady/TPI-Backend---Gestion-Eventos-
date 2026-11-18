package ar.utn.complejo.recursos.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Entity
@Table(name = "instalacion")
@Getter
@Setter
@NoArgsConstructor
public class Instalacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String nombre;

    @Column(length = 500)
    private String descripcion;

    @Column(nullable = false)
    private int capacidad;

    /** Habilita la advertencia meteorológica en eventos que la usan. */
    @Column(name = "aire_libre", nullable = false)
    private boolean aireLibre;

    /** false = baja lógica. */
    @Column(nullable = false)
    private boolean activa = true;

    @Column(name = "tarifa_hora", nullable = false, precision = 12, scale = 2)
    private BigDecimal tarifaHora;

    @OneToMany(mappedBy = "instalacion", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<HorarioInstalacion> horarios = new ArrayList<>();

    @OneToMany(mappedBy = "instalacion", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EquipamientoInstalacion> equipamiento = new ArrayList<>();

    public Optional<HorarioInstalacion> horarioDe(DayOfWeek dia) {
        return horarios.stream().filter(h -> h.getDia() == dia).findFirst();
    }

    /** Actualiza in situ por día (evita insertar antes de borrar y violar la unicidad instalación+día). */
    public void reemplazarHorarios(List<HorarioInstalacion> nuevos) {
        horarios.removeIf(actual -> nuevos.stream().noneMatch(n -> n.getDia() == actual.getDia()));
        for (HorarioInstalacion nuevo : nuevos) {
            Optional<HorarioInstalacion> existente = horarioDe(nuevo.getDia());
            if (existente.isPresent()) {
                existente.get().setApertura(nuevo.getApertura());
                existente.get().setCierre(nuevo.getCierre());
            } else {
                nuevo.setInstalacion(this);
                horarios.add(nuevo);
            }
        }
    }

    public void reemplazarEquipamiento(List<EquipamientoInstalacion> nuevos) {
        equipamiento.clear();
        nuevos.forEach(e -> {
            e.setInstalacion(this);
            equipamiento.add(e);
        });
    }
}
