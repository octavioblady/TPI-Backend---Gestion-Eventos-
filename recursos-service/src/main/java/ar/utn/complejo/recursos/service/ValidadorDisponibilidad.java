package ar.utn.complejo.recursos.service;

import ar.utn.complejo.common.tiempo.Intervalo;
import ar.utn.complejo.common.tiempo.ReglasTiempo;
import ar.utn.complejo.recursos.domain.HorarioInstalacion;
import ar.utn.complejo.recursos.domain.Instalacion;
import ar.utn.complejo.recursos.repository.MantenimientoRepository;
import ar.utn.complejo.recursos.repository.ReservaEspacioRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Optional;

/**
 * Reglas de negocio de disponibilidad de espacios (reglas 1 a 4 y conflicto de superposición).
 * Es la única fuente de verdad: la consulta de disponibilidad y la reserva usan esta misma lógica.
 */
@Component
public class ValidadorDisponibilidad {

    private final Clock reloj;
    private final MantenimientoRepository mantenimientos;
    private final ReservaEspacioRepository reservas;

    public ValidadorDisponibilidad(Clock reloj, MantenimientoRepository mantenimientos, ReservaEspacioRepository reservas) {
        this.reloj = reloj;
        this.mantenimientos = mantenimientos;
        this.reservas = reservas;
    }

    /** Regla 1: anticipación mínima de 48 h. */
    public Optional<MotivoIndisponibilidad> validarAnticipacion(Intervalo intervalo) {
        return ReglasTiempo.cumpleAnticipacion(intervalo.inicio(), ReglasTiempo.ahora(reloj))
                ? Optional.empty()
                : Optional.of(MotivoIndisponibilidad.ANTICIPACION_INSUFICIENTE);
    }

    /** Reglas 2, 3 y 4 y superposición, evaluadas en ese orden de prioridad para una instalación. */
    public Optional<MotivoIndisponibilidad> validarInstalacion(Instalacion instalacion, Intervalo intervalo, Integer asistentes) {
        if (!instalacion.isActiva()) {
            return Optional.of(MotivoIndisponibilidad.INSTALACION_INACTIVA);
        }
        Optional<HorarioInstalacion> horario = instalacion.horarioDe(intervalo.inicio().getDayOfWeek());
        if (horario.isEmpty() || !intervalo.dentroDeFranja(horario.get().getApertura(), horario.get().getCierre())) {
            return Optional.of(MotivoIndisponibilidad.FUERA_DE_HORARIO);
        }
        if (mantenimientos.existeActivoEnPeriodo(instalacion.getId(), intervalo.inicio(), intervalo.fin())) {
            return Optional.of(MotivoIndisponibilidad.MANTENIMIENTO);
        }
        if (asistentes != null && asistentes > instalacion.getCapacidad()) {
            return Optional.of(MotivoIndisponibilidad.CAPACIDAD_EXCEDIDA);
        }
        if (reservas.existeActivaEnPeriodo(instalacion.getId(), intervalo.inicio(), intervalo.fin())) {
            return Optional.of(MotivoIndisponibilidad.CONFLICTO_DISPONIBILIDAD);
        }
        return Optional.empty();
    }
}
