package ar.utn.complejo.eventos.service;

import ar.utn.complejo.common.error.BusinessException;
import ar.utn.complejo.common.tiempo.Intervalo;
import ar.utn.complejo.common.tiempo.ReglasTiempo;
import ar.utn.complejo.eventos.clima.ClimaService;
import ar.utn.complejo.eventos.domain.AdvertenciaMeteorologica;
import ar.utn.complejo.eventos.domain.EstadoEvento;
import ar.utn.complejo.eventos.domain.Evento;
import ar.utn.complejo.eventos.dto.EventoDtos.*;
import ar.utn.complejo.eventos.recursos.RecursosClient;
import ar.utn.complejo.eventos.recursos.RecursosDtos;
import ar.utn.complejo.eventos.recursos.RecursosNoDisponibleException;
import ar.utn.complejo.eventos.recursos.RecursosRechazoException;
import ar.utn.complejo.eventos.repository.EventoRepository;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;

/**
 * Orquesta el proceso central. Eventos es dueño del evento, su estado, precio, seña e historial;
 * Recursos es dueño de la disponibilidad y el stock. No hay transacción distribuida: la consistencia se resuelve
 * con operaciones idempotentes y compensación (liberar lo reservado si algo falla después).
 */
@Service
public class EventoService {

    private static final Logger log = LoggerFactory.getLogger(EventoService.class);

    private final EventoRepository eventos;
    private final RecursosClient recursos;
    private final ClimaService clima;
    private final TransactionTemplate tx;
    private final Clock reloj;
    private final int seniaPorcentaje;

    public EventoService(EventoRepository eventos, RecursosClient recursos, ClimaService clima,
                         PlatformTransactionManager txManager, Clock reloj,
                         @Value("${complejo.senia-porcentaje:50}") int seniaPorcentaje) {
        this.eventos = eventos;
        this.recursos = recursos;
        this.clima = clima;
        this.tx = new TransactionTemplate(txManager);
        this.reloj = reloj;
        this.seniaPorcentaje = seniaPorcentaje;
    }

    // ---------------------------------------------------------------- T-01 SOLICITADO

    /** F-04, paso 1: el cliente solicita el evento. Se cotiza y queda SOLICITADO a la espera de la seña. */
    public EventoResponse solicitar(CrearEventoRequest req, Usuario usuario) {
        String clienteId = resolverCliente(req, usuario);
        Intervalo intervalo = intervalo(req.inicio(), req.fin());
        if (!intervalo.esMismoDia()) {
            throw BusinessException.validacion("PERIODO_INVALIDO", "El evento debe comenzar y terminar el mismo día");
        }
        Set<Long> ids = new TreeSet<>(req.instalacionIds());
        List<RecursosDtos.ServicioSolicitado> servicios = req.servicios() == null ? List.of()
                : req.servicios().stream().map(s -> new RecursosDtos.ServicioSolicitado(s.servicioId(), s.cantidad())).toList();

        RecursosDtos.CotizacionResponse cotizacion = conRecursos(() ->
                recursos.cotizar(new RecursosDtos.CotizacionRequest(List.copyOf(ids), servicios)));
        PrecioCalculator.Desglose precio = PrecioCalculator.calcular(cotizacion, intervalo, seniaPorcentaje);

        LocalDateTime ahora = ReglasTiempo.ahora(reloj);
        return tx.execute(status -> {
            Evento e = new Evento();
            e.setClienteId(clienteId);
            e.setCreadoPor(usuario.id());
            e.setInicio(req.inicio());
            e.setFin(req.fin());
            e.setAsistentes(req.asistentes());
            e.setPrecioTotal(precio.total());
            e.setSeniaMonto(precio.senia());
            e.setCreadoEn(ahora);
            precio.instalaciones().forEach(i -> {
                i.setEvento(e);
                e.getInstalaciones().add(i);
            });
            precio.servicios().forEach(s -> {
                s.setEvento(e);
                e.getServicios().add(s);
            });
            e.registrarCreacion(usuario.id(), ahora);
            return aDto(eventos.save(e));
        });
    }

    // ---------------------------------------------------------------- T-02 / T-03 CONFIRMADO o RECHAZADO

    /** Resultado de la evaluación: si hubo una falla técnica, el evento igual quedó RECHAZADO y se informa al llamador. */
    public record Evaluacion(EventoResponse evento, RuntimeException fallaTecnica) {
    }

    /**
     * F-04, paso 2 (ADMIN): registra el pago de la seña y valida todas las condiciones juntas.
     * Si la seña no está abonada o alguna condición falla, SOLICITADO pasa a RECHAZADO con motivo; si todo se cumple, a CONFIRMADO.
     */
    public Evaluacion evaluar(Long id, boolean senia, Usuario usuario) {
        Evento snapshot = tx.execute(status -> {
            Evento e = buscar(id);
            if (e.getEstado() != EstadoEvento.SOLICITADO) {
                throw BusinessException.conflicto("TRANSICION_INVALIDA",
                        "Solo se puede registrar la seña de un evento SOLICITADO (estado actual: " + e.getEstado() + ")");
            }
            e.getInstalaciones().size();
            e.getServicios().size();
            return e;
        });

        if (!senia) {
            return new Evaluacion(rechazar(id, "SENIA_NO_ABONADA", "La seña no fue abonada", usuario), null);
        }

        RecursosDtos.ReservaRequest pedido = new RecursosDtos.ReservaRequest(id,
                snapshot.getInstalaciones().stream().map(i -> i.getInstalacionId()).toList(),
                snapshot.getInicio(), snapshot.getFin(), snapshot.getAsistentes(),
                snapshot.getServicios().stream().map(s -> new RecursosDtos.ServicioSolicitado(s.getServicioId(), s.getCantidad())).toList());
        try {
            recursos.reservar(pedido);
        } catch (RecursosRechazoException r) {
            return new Evaluacion(rechazar(id, r.getCodigo(), r.getMessage(), usuario), null);
        } catch (RecursosNoDisponibleException | CallNotPermittedException n) {
            EventoResponse rechazado = rechazar(id, "RECURSOS_NO_DISPONIBLE",
                    "No se pudo reservar: recursos no disponible. Podés volver a solicitar el evento.", usuario);
            // La respuesta pudo perderse con la reserva ya hecha: se intenta liberar para no dejar reservas huérfanas.
            liberarSinFallar(id);
            return new Evaluacion(rechazado, errorTecnico(n));
        }

        // Regla 6 (mejor esfuerzo): fuera de la transacción de base de datos porque hace una llamada HTTP.
        ClimaService.Resultado climaResultado = clima.evaluar(snapshot);

        try {
            EventoResponse confirmado = tx.execute(status -> {
                Evento e = buscar(id);
                e.setSeniaAbonada(true);
                climaResultado.advertencia().ifPresent(a -> {
                    a.setEvento(e);
                    e.getAdvertencias().add(a);
                });
                e.cambiarEstado(EstadoEvento.CONFIRMADO, "Seña abonada y condiciones validadas; recursos asignados",
                        usuario.id(), ReglasTiempo.ahora(reloj));
                return aDto(e);
            });
            return new Evaluacion(confirmado, null);
        } catch (RuntimeException ex) {
            compensarReserva(id);
            throw ex;
        }
    }

    private EventoResponse rechazar(Long id, String codigo, String motivo, Usuario usuario) {
        return tx.execute(status -> {
            Evento e = buscar(id);
            e.setCodigoRechazo(codigo);
            e.setMotivoRechazo(motivo);
            e.cambiarEstado(EstadoEvento.RECHAZADO, motivo, usuario.id(), ReglasTiempo.ahora(reloj));
            return aDto(e);
        });
    }

    /** Si falló la confirmación local pero la reserva ya existe en Recursos, se libera salvo que otro hilo haya confirmado el evento. */
    private void compensarReserva(Long id) {
        EstadoEvento estado = tx.execute(status -> eventos.findById(id).map(Evento::getEstado).orElse(null));
        if (estado != EstadoEvento.CONFIRMADO) {
            liberarSinFallar(id);
        }
    }

    private void liberarSinFallar(Long id) {
        try {
            recursos.liberar(id);
        } catch (RuntimeException e) {
            log.error("No se pudo liberar la reserva del evento {} en recursos-service; requiere conciliación manual: {}", id, e.getMessage());
        }
    }

    // ---------------------------------------------------------------- T-04 CANCELADO

    /** F-06: solo desde CONFIRMADO. Libera recursos y fija si la seña es reembolsable (regla 5: 48 h). */
    public EventoResponse cancelar(Long id, Usuario usuario) {
        tx.executeWithoutResult(status -> {
            Evento e = buscar(id);
            verificarAcceso(e, usuario);
            if (e.getEstado() != EstadoEvento.CONFIRMADO) {
                throw BusinessException.conflicto("TRANSICION_INVALIDA",
                        "Solo se puede cancelar un evento CONFIRMADO (estado actual: " + e.getEstado() + ")");
            }
        });
        conRecursos(() -> {
            recursos.liberar(id);
            return null;
        });
        return tx.execute(status -> {
            Evento e = buscar(id);
            LocalDateTime ahora = ReglasTiempo.ahora(reloj);
            boolean reembolsable = ReglasTiempo.esCancelacionReembolsable(e.getInicio(), ahora);
            e.cambiarEstado(EstadoEvento.CANCELADO,
                    "Cancelado" + (reembolsable ? " con 48 h o más de anticipación: seña reembolsable" : " con menos de 48 h: seña no reembolsable"),
                    usuario.id(), ahora);
            e.setSeniaReembolsable(reembolsable);
            return aDto(e);
        });
    }

    // ---------------------------------------------------------------- T-05 / T-06

    @Transactional
    public EventoResponse iniciar(Long id, Usuario usuario) {
        Evento e = buscar(id);
        e.cambiarEstado(EstadoEvento.EN_CURSO, "Comienza el evento", usuario.id(), ReglasTiempo.ahora(reloj));
        return aDto(e);
    }

    @Transactional
    public EventoResponse finalizar(Long id, Usuario usuario) {
        Evento e = buscar(id);
        e.cambiarEstado(EstadoEvento.FINALIZADO, "Finaliza el evento", usuario.id(), ReglasTiempo.ahora(reloj));
        return aDto(e);
    }

    // ---------------------------------------------------------------- Consultas

    @Transactional(readOnly = true)
    public List<EventoResponse> listar(Usuario usuario) {
        List<Evento> lista = usuario.admin() ? eventos.findAllByOrderByInicioDesc() : eventos.findByClienteIdOrderByInicioDesc(usuario.id());
        return lista.stream().map(EventoService::aDto).toList();
    }

    @Transactional(readOnly = true)
    public EventoResponse obtener(Long id, Usuario usuario) {
        Evento e = buscar(id);
        verificarAcceso(e, usuario);
        return aDto(e);
    }

    /** F-07: consulta (o reconsulta) del pronóstico de un evento al aire libre. Útil cuando al confirmar faltaban más de 16 días. */
    public PronosticoResponse consultarPronostico(Long id, Usuario usuario) {
        Evento snapshot = tx.execute(status -> {
            Evento e = buscar(id);
            verificarAcceso(e, usuario);
            if (e.getEstado() != EstadoEvento.SOLICITADO && e.getEstado() != EstadoEvento.CONFIRMADO) {
                throw BusinessException.conflicto("TRANSICION_INVALIDA",
                        "El pronóstico solo se consulta para eventos SOLICITADO o CONFIRMADO (estado actual: " + e.getEstado() + ")");
            }
            e.getInstalaciones().size();
            return e;
        });
        ClimaService.Resultado r = clima.evaluar(snapshot);
        AdvertenciaItem item = r.advertencia().map(a -> {
            tx.executeWithoutResult(status -> {
                Evento e = buscar(id);
                a.setEvento(e);
                e.getAdvertencias().add(a);
            });
            return aItem(a);
        }).orElse(null);
        return new PronosticoResponse(r.estado().name(), r.mensaje(), item);
    }

    // ---------------------------------------------------------------- Soporte

    private Evento buscar(Long id) {
        return eventos.findById(id).orElseThrow(() ->
                BusinessException.noEncontrado("EVENTO_NO_ENCONTRADO", "No existe el evento " + id));
    }

    /** Un CLIENTE solo accede a eventos cuyo cliente_id coincide con el sub de su token. */
    private static void verificarAcceso(Evento e, Usuario usuario) {
        if (!usuario.admin() && !e.getClienteId().equals(usuario.id())) {
            throw new BusinessException(403, "EVENTO_AJENO", "El evento pertenece a otro cliente");
        }
    }

    private static String resolverCliente(CrearEventoRequest req, Usuario usuario) {
        if (req.clienteId() == null || req.clienteId().isBlank()) {
            return usuario.id();
        }
        if (!usuario.admin() && !req.clienteId().equals(usuario.id())) {
            throw new BusinessException(403, "EVENTO_AJENO", "Solo un ADMIN puede solicitar eventos en nombre de otro cliente");
        }
        return req.clienteId();
    }

    private static Intervalo intervalo(LocalDateTime inicio, LocalDateTime fin) {
        try {
            return new Intervalo(inicio, fin);
        } catch (IllegalArgumentException e) {
            throw BusinessException.validacion("PERIODO_INVALIDO", "'inicio' debe ser anterior a 'fin'");
        }
    }

    /** Traduce las fallas de Recursos: negocio (4xx) pasa con su código; fallas técnicas se informan como 503 o 504. */
    private <T> T conRecursos(Supplier<T> llamada) {
        try {
            return llamada.get();
        } catch (RecursosRechazoException r) {
            throw new BusinessException(r.getStatus(), r.getCodigo(), r.getMessage());
        } catch (RecursosNoDisponibleException | CallNotPermittedException n) {
            throw errorTecnico(n);
        }
    }

    private static BusinessException errorTecnico(RuntimeException n) {
        boolean timeout = n instanceof RecursosNoDisponibleException r && r.esTimeout();
        return timeout
                ? new BusinessException(504, "RECURSOS_TIMEOUT", "recursos-service no respondió a tiempo; intentá nuevamente")
                : new BusinessException(503, "RECURSOS_NO_DISPONIBLE", "recursos-service no está disponible; intentá nuevamente");
    }

    static EventoResponse aDto(Evento e) {
        return new EventoResponse(e.getId(), e.getClienteId(), e.getEstado(), e.getInicio(), e.getFin(), e.getAsistentes(),
                e.getInstalaciones().stream().map(i -> new InstalacionEvento(i.getInstalacionId(), i.getNombre(), i.isAireLibre(),
                        i.getTarifaHora(), i.getHorasFacturadas(), i.getSubtotal())).toList(),
                e.getServicios().stream().map(s -> new ServicioEvento(s.getServicioId(), s.getNombre(), s.getCantidad(),
                        s.getPrecioUnitario(), s.getSubtotal())).toList(),
                e.getPrecioTotal(), e.getSeniaMonto(), e.isSeniaAbonada(), e.getSeniaReembolsable(),
                e.getCodigoRechazo(), e.getMotivoRechazo(),
                e.getAdvertencias().stream().map(EventoService::aItem).toList(),
                e.getHistorial().stream().map(h -> new HistorialItem(h.getEstadoAnterior(), h.getEstadoNuevo(), h.getMotivo(),
                        h.getUsuario(), h.getFecha())).toList());
    }

    private static AdvertenciaItem aItem(AdvertenciaMeteorologica a) {
        return new AdvertenciaItem(a.getConsultadaEn(), a.getProbabilidadLluvia(), a.getRafagaVientoKmh(), a.isGranizo(), a.getDetalle());
    }
}
