package ar.utn.complejo.recursos.service;

import ar.utn.complejo.common.error.BusinessException;
import ar.utn.complejo.recursos.domain.EstadoMantenimiento;
import ar.utn.complejo.recursos.domain.Instalacion;
import ar.utn.complejo.recursos.domain.Mantenimiento;
import ar.utn.complejo.recursos.dto.MantenimientoDtos.*;
import ar.utn.complejo.recursos.repository.InstalacionRepository;
import ar.utn.complejo.recursos.repository.MantenimientoRepository;
import ar.utn.complejo.recursos.repository.ReservaEspacioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MantenimientoService {

    private final MantenimientoRepository mantenimientos;
    private final InstalacionRepository instalaciones;
    private final ReservaEspacioRepository reservas;

    public MantenimientoService(MantenimientoRepository mantenimientos, InstalacionRepository instalaciones,
                                ReservaEspacioRepository reservas) {
        this.mantenimientos = mantenimientos;
        this.instalaciones = instalaciones;
        this.reservas = reservas;
    }

    /** F-02: bloquea la instalación en el período. No se puede bloquear un período que ya tiene reservas activas. */
    @Transactional
    public MantenimientoResponse registrar(MantenimientoRequest req) {
        if (!req.desde().isBefore(req.hasta())) {
            throw BusinessException.validacion("PERIODO_INVALIDO", "'desde' debe ser anterior a 'hasta'");
        }
        // Se bloquea la fila para serializar contra reservas concurrentes sobre la misma instalación.
        Instalacion instalacion = instalaciones.findAllByIdForUpdate(List.of(req.instalacionId())).stream().findFirst()
                .orElseThrow(() -> BusinessException.noEncontrado("INSTALACION_NO_ENCONTRADA",
                        "No existe la instalación " + req.instalacionId()));
        if (!instalacion.isActiva()) {
            throw BusinessException.conflicto("INSTALACION_INACTIVA", "La instalación está dada de baja");
        }
        if (reservas.existeActivaEnPeriodo(instalacion.getId(), req.desde(), req.hasta())) {
            throw BusinessException.conflicto("MANTENIMIENTO_CON_RESERVAS",
                    "Existen reservas activas en el período; deben cancelarse antes de bloquearlo");
        }
        Mantenimiento m = new Mantenimiento();
        m.setInstalacionId(instalacion.getId());
        m.setDesde(req.desde());
        m.setHasta(req.hasta());
        m.setMotivo(req.motivo());
        return aDto(mantenimientos.save(m));
    }

    @Transactional
    public MantenimientoResponse finalizar(Long id) {
        Mantenimiento m = mantenimientos.findById(id).orElseThrow(() ->
                BusinessException.noEncontrado("MANTENIMIENTO_NO_ENCONTRADO", "No existe el mantenimiento " + id));
        if (m.getEstado() == EstadoMantenimiento.FINALIZADO) {
            throw BusinessException.conflicto("MANTENIMIENTO_YA_FINALIZADO", "El mantenimiento ya estaba finalizado");
        }
        m.setEstado(EstadoMantenimiento.FINALIZADO);
        return aDto(m);
    }

    @Transactional(readOnly = true)
    public List<MantenimientoResponse> listar(Long instalacionId, EstadoMantenimiento estado) {
        return mantenimientos.buscar(instalacionId, estado).stream().map(MantenimientoService::aDto).toList();
    }

    private static MantenimientoResponse aDto(Mantenimiento m) {
        return new MantenimientoResponse(m.getId(), m.getInstalacionId(), m.getDesde(), m.getHasta(), m.getMotivo(), m.getEstado());
    }
}
