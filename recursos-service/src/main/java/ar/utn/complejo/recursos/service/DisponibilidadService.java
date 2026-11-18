package ar.utn.complejo.recursos.service;

import ar.utn.complejo.common.error.BusinessException;
import ar.utn.complejo.common.tiempo.Intervalo;
import ar.utn.complejo.recursos.domain.Instalacion;
import ar.utn.complejo.recursos.dto.DisponibilidadDtos.DisponibilidadItem;
import ar.utn.complejo.recursos.repository.InstalacionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Service
public class DisponibilidadService {

    private final InstalacionRepository instalaciones;
    private final ValidadorDisponibilidad validador;

    public DisponibilidadService(InstalacionRepository instalaciones, ValidadorDisponibilidad validador) {
        this.instalaciones = instalaciones;
        this.validador = validador;
    }

    /** F-03: espacios disponibles (o motivo de indisponibilidad) para fecha, horario y capacidad requerida. */
    @Transactional(readOnly = true)
    public List<DisponibilidadItem> consultar(LocalDate fecha, LocalTime horaInicio, LocalTime horaFin, Integer capacidad) {
        if (!horaInicio.isBefore(horaFin)) {
            throw BusinessException.validacion("HORARIO_INVALIDO", "'horaInicio' debe ser anterior a 'horaFin'");
        }
        Intervalo intervalo = Intervalo.de(fecha, horaInicio, horaFin);
        Optional<MotivoIndisponibilidad> anticipacion = validador.validarAnticipacion(intervalo);

        return instalaciones.findByActivaTrueOrderByNombre().stream().map(i -> {
            Optional<MotivoIndisponibilidad> motivo = anticipacion.isPresent() ? anticipacion : validador.validarInstalacion(i, intervalo, capacidad);
            return item(i, motivo);
        }).toList();
    }

    private static DisponibilidadItem item(Instalacion i, Optional<MotivoIndisponibilidad> motivo) {
        return new DisponibilidadItem(i.getId(), i.getNombre(), i.getCapacidad(), motivo.isEmpty(),
                motivo.map(Enum::name).orElse(null), motivo.map(MotivoIndisponibilidad::mensaje).orElse(null));
    }
}
