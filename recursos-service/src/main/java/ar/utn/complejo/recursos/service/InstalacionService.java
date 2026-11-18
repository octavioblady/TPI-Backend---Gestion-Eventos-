package ar.utn.complejo.recursos.service;

import ar.utn.complejo.common.error.BusinessException;
import ar.utn.complejo.common.tiempo.ReglasTiempo;
import ar.utn.complejo.recursos.domain.EquipamientoInstalacion;
import ar.utn.complejo.recursos.domain.HorarioInstalacion;
import ar.utn.complejo.recursos.domain.Instalacion;
import ar.utn.complejo.recursos.dto.InstalacionDtos.*;
import ar.utn.complejo.recursos.repository.InstalacionRepository;
import ar.utn.complejo.recursos.repository.ReservaEspacioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class InstalacionService {

    private final InstalacionRepository instalaciones;
    private final ReservaEspacioRepository reservas;
    private final Clock reloj;
    private final BigDecimal tarifaPorDefecto;

    public InstalacionService(InstalacionRepository instalaciones, ReservaEspacioRepository reservas, Clock reloj,
                              @Value("${complejo.tarifa-hora-default:10000}") BigDecimal tarifaPorDefecto) {
        this.instalaciones = instalaciones;
        this.reservas = reservas;
        this.reloj = reloj;
        this.tarifaPorDefecto = tarifaPorDefecto;
    }

    @Transactional(readOnly = true)
    public List<InstalacionResponse> listar(boolean soloActivas) {
        List<Instalacion> lista = soloActivas ? instalaciones.findByActivaTrueOrderByNombre() : instalaciones.findAllByOrderByNombre();
        return lista.stream().map(InstalacionService::aDto).toList();
    }

    @Transactional(readOnly = true)
    public InstalacionResponse obtener(Long id) {
        return aDto(buscar(id));
    }

    @Transactional
    public InstalacionResponse registrar(InstalacionRequest req) {
        if (instalaciones.existsByNombreIgnoreCase(req.nombre())) {
            throw BusinessException.conflicto("INSTALACION_DUPLICADA", "Ya existe una instalación con ese nombre");
        }
        Instalacion instalacion = new Instalacion();
        aplicar(instalacion, req);
        return aDto(instalaciones.save(instalacion));
    }

    @Transactional
    public InstalacionResponse modificar(Long id, InstalacionRequest req) {
        Instalacion instalacion = buscar(id);
        if (instalaciones.existsByNombreIgnoreCaseAndIdNot(req.nombre(), id)) {
            throw BusinessException.conflicto("INSTALACION_DUPLICADA", "Ya existe una instalación con ese nombre");
        }
        aplicar(instalacion, req);
        return aDto(instalacion);
    }

    /** Baja lógica. No se permite si la instalación tiene reservas activas pendientes. */
    @Transactional
    public void darDeBaja(Long id) {
        Instalacion instalacion = buscar(id);
        if (reservas.existeActivaPosteriorA(id, ReglasTiempo.ahora(reloj))) {
            throw BusinessException.conflicto("INSTALACION_CON_RESERVAS",
                    "La instalación tiene reservas activas pendientes; cancelelas antes de darla de baja");
        }
        instalacion.setActiva(false);
    }

    Instalacion buscar(Long id) {
        return instalaciones.findById(id).orElseThrow(() ->
                BusinessException.noEncontrado("INSTALACION_NO_ENCONTRADA", "No existe la instalación " + id));
    }

    private void aplicar(Instalacion instalacion, InstalacionRequest req) {
        instalacion.setNombre(req.nombre().trim());
        instalacion.setDescripcion(req.descripcion());
        instalacion.setCapacidad(req.capacidad());
        instalacion.setAireLibre(req.aireLibre());
        instalacion.setTarifaHora(req.tarifaHora() != null ? req.tarifaHora() : tarifaPorDefecto);

        Set<DayOfWeek> dias = new HashSet<>();
        List<HorarioInstalacion> horarios = req.horarios().stream().map(h -> {
            if (!h.cierre().isAfter(h.apertura())) {
                throw BusinessException.validacion("HORARIO_INVALIDO", "El cierre debe ser posterior a la apertura (" + h.dia() + ")");
            }
            if (!dias.add(h.dia())) {
                throw BusinessException.validacion("HORARIO_DUPLICADO", "Día repetido en los horarios: " + h.dia());
            }
            HorarioInstalacion entidad = new HorarioInstalacion();
            entidad.setDia(h.dia());
            entidad.setApertura(h.apertura());
            entidad.setCierre(h.cierre());
            return entidad;
        }).toList();
        instalacion.reemplazarHorarios(horarios);

        List<EquipamientoInstalacion> equipamiento = (req.equipamiento() == null ? List.<EquipamientoDto>of() : req.equipamiento())
                .stream().map(e -> {
                    EquipamientoInstalacion entidad = new EquipamientoInstalacion();
                    entidad.setNombre(e.nombre());
                    entidad.setCantidad(e.cantidad());
                    return entidad;
                }).toList();
        instalacion.reemplazarEquipamiento(equipamiento);
    }

    static InstalacionResponse aDto(Instalacion i) {
        return new InstalacionResponse(i.getId(), i.getNombre(), i.getDescripcion(), i.getCapacidad(), i.isAireLibre(),
                i.isActiva(), i.getTarifaHora(),
                i.getHorarios().stream().map(h -> new HorarioDto(h.getDia(), h.getApertura(), h.getCierre())).toList(),
                i.getEquipamiento().stream().map(e -> new EquipamientoDto(e.getNombre(), e.getCantidad())).toList());
    }
}
