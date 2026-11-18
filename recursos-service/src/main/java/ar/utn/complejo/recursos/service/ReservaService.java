package ar.utn.complejo.recursos.service;

import ar.utn.complejo.common.error.BusinessException;
import ar.utn.complejo.common.tiempo.Intervalo;
import ar.utn.complejo.recursos.domain.*;
import ar.utn.complejo.recursos.dto.ReservaDtos.*;
import ar.utn.complejo.recursos.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Reserva y liberación atómicas de espacios y servicios para un evento. Todo ocurre en una sola transacción:
 * si algo falla (por ejemplo, la segunda instalación está ocupada) no queda nada a medias.
 */
@Service
public class ReservaService {

    private final InstalacionRepository instalaciones;
    private final ServicioComplementarioRepository servicios;
    private final StockServicioFechaRepository stock;
    private final ReservaEspacioRepository reservasEspacio;
    private final ReservaServicioRepository reservasServicio;
    private final ValidadorDisponibilidad validador;

    public ReservaService(InstalacionRepository instalaciones, ServicioComplementarioRepository servicios,
                          StockServicioFechaRepository stock, ReservaEspacioRepository reservasEspacio,
                          ReservaServicioRepository reservasServicio, ValidadorDisponibilidad validador) {
        this.instalaciones = instalaciones;
        this.servicios = servicios;
        this.stock = stock;
        this.reservasEspacio = reservasEspacio;
        this.reservasServicio = reservasServicio;
        this.validador = validador;
    }

    @Transactional
    public ReservaResponse reservar(ReservaRequest req) {
        // Idempotencia: un reintento por timeout no duplica la reserva del mismo evento.
        List<ReservaEspacio> existentes = reservasEspacio.findByEventoIdAndEstado(req.eventoId(), EstadoReserva.ACTIVA);
        if (!existentes.isEmpty()) {
            return armarRespuesta(req.eventoId());
        }

        Intervalo intervalo = intervalo(req);
        validador.validarAnticipacion(intervalo).ifPresent(m -> { throw m.comoExcepcion(null); });

        Set<Long> idsInstalaciones = new TreeSet<>(req.instalacionIds());
        List<Instalacion> bloqueadas = instalaciones.findAllByIdForUpdate(idsInstalaciones);
        if (bloqueadas.size() != idsInstalaciones.size()) {
            Set<Long> encontradas = new HashSet<>();
            bloqueadas.forEach(i -> encontradas.add(i.getId()));
            idsInstalaciones.removeAll(encontradas);
            throw BusinessException.noEncontrado("INSTALACION_NO_ENCONTRADA", "No existen las instalaciones " + idsInstalaciones);
        }
        for (Instalacion instalacion : bloqueadas) {
            validador.validarInstalacion(instalacion, intervalo, req.asistentes())
                    .ifPresent(m -> { throw m.comoExcepcion(instalacion.getNombre()); });
        }

        Map<Long, Integer> pedidos = consolidar(req.servicios());
        List<ServicioComplementario> serviciosBloqueados =
                pedidos.isEmpty() ? List.of() : servicios.findAllByIdForUpdate(pedidos.keySet());
        if (serviciosBloqueados.size() != pedidos.size()) {
            throw BusinessException.noEncontrado("SERVICIO_NO_ENCONTRADO", "Alguno de los servicios solicitados no existe");
        }
        var fecha = intervalo.inicio().toLocalDate();
        for (ServicioComplementario s : serviciosBloqueados) {
            if (!s.isActivo()) {
                throw MotivoIndisponibilidad.SERVICIO_INACTIVO.comoExcepcion(s.getNombre());
            }
            StockServicioFecha fila = stock.findByServicioIdAndFecha(s.getId(), fecha).orElseGet(() -> {
                StockServicioFecha nueva = new StockServicioFecha();
                nueva.setServicioId(s.getId());
                nueva.setFecha(fecha);
                return nueva;
            });
            int pedido = pedidos.get(s.getId());
            if (fila.getCantidadReservada() + pedido > s.getStockTotal()) {
                throw MotivoIndisponibilidad.STOCK_INSUFICIENTE.comoExcepcion(s.getNombre()
                        + " (pedido " + pedido + ", disponible " + (s.getStockTotal() - fila.getCantidadReservada()) + ")");
            }
            fila.setCantidadReservada(fila.getCantidadReservada() + pedido);
            stock.save(fila);

            ReservaServicio rs = new ReservaServicio();
            rs.setEventoId(req.eventoId());
            rs.setServicioId(s.getId());
            rs.setFecha(fecha);
            rs.setCantidad(pedido);
            reservasServicio.save(rs);
        }
        for (Instalacion instalacion : bloqueadas) {
            ReservaEspacio re = new ReservaEspacio();
            re.setEventoId(req.eventoId());
            re.setInstalacionId(instalacion.getId());
            re.setInicio(intervalo.inicio());
            re.setFin(intervalo.fin());
            reservasEspacio.save(re);
        }
        return armarRespuesta(req.eventoId());
    }

    /** Libera espacios y devuelve el stock. Es idempotente: liberar dos veces el mismo evento no falla. */
    @Transactional
    public void liberar(Long eventoId) {
        List<ReservaServicio> deServicios = reservasServicio.findByEventoIdAndEstado(eventoId, EstadoReserva.ACTIVA);
        if (!deServicios.isEmpty()) {
            Set<Long> ids = new TreeSet<>();
            deServicios.forEach(r -> ids.add(r.getServicioId()));
            servicios.findAllByIdForUpdate(ids);
            for (ReservaServicio rs : deServicios) {
                stock.findByServicioIdAndFecha(rs.getServicioId(), rs.getFecha()).ifPresent(fila ->
                        fila.setCantidadReservada(Math.max(0, fila.getCantidadReservada() - rs.getCantidad())));
                rs.setEstado(EstadoReserva.LIBERADA);
            }
        }
        reservasEspacio.findByEventoIdAndEstado(eventoId, EstadoReserva.ACTIVA)
                .forEach(r -> r.setEstado(EstadoReserva.LIBERADA));
    }

    private static Intervalo intervalo(ReservaRequest req) {
        try {
            return new Intervalo(req.inicio(), req.fin());
        } catch (IllegalArgumentException e) {
            throw BusinessException.validacion("PERIODO_INVALIDO", "'inicio' debe ser anterior a 'fin'");
        }
    }

    private static Map<Long, Integer> consolidar(List<ServicioSolicitado> solicitados) {
        Map<Long, Integer> pedidos = new TreeMap<>();
        if (solicitados != null) {
            solicitados.forEach(s -> pedidos.merge(s.servicioId(), s.cantidad(), Integer::sum));
        }
        return pedidos;
    }

    private static InstalacionReservada aReservada(Instalacion i) {
        return new InstalacionReservada(i.getId(), i.getNombre(), i.getCapacidad(), i.isAireLibre(), i.getTarifaHora());
    }

    /** Devuelve tarifas y datos del catálogo para que eventos-service calcule el precio. No reserva ni bloquea nada. */
    @Transactional(readOnly = true)
    public CotizacionResponse cotizar(CotizacionRequest req) {
        Set<Long> ids = new TreeSet<>(req.instalacionIds());
        List<Instalacion> encontradas = instalaciones.findAllById(ids);
        if (encontradas.size() != ids.size()) {
            Set<Long> hay = new HashSet<>();
            encontradas.forEach(i -> hay.add(i.getId()));
            ids.removeAll(hay);
            throw BusinessException.noEncontrado("INSTALACION_NO_ENCONTRADA", "No existen las instalaciones " + ids);
        }
        for (Instalacion i : encontradas) {
            if (!i.isActiva()) {
                throw MotivoIndisponibilidad.INSTALACION_INACTIVA.comoExcepcion(i.getNombre());
            }
        }
        Map<Long, Integer> pedidos = consolidar(req.servicios());
        Map<Long, ServicioComplementario> catalogo = new HashMap<>();
        servicios.findAllById(pedidos.keySet()).forEach(s -> catalogo.put(s.getId(), s));
        if (catalogo.size() != pedidos.size()) {
            throw BusinessException.noEncontrado("SERVICIO_NO_ENCONTRADO", "Alguno de los servicios solicitados no existe");
        }
        List<ServicioReservado> srvs = new ArrayList<>();
        pedidos.forEach((id, cantidad) -> {
            ServicioComplementario s = catalogo.get(id);
            if (!s.isActivo()) {
                throw MotivoIndisponibilidad.SERVICIO_INACTIVO.comoExcepcion(s.getNombre());
            }
            srvs.add(new ServicioReservado(id, s.getNombre(), cantidad, s.getPrecioUnitario()));
        });
        return new CotizacionResponse(encontradas.stream().sorted(Comparator.comparing(Instalacion::getId))
                .map(ReservaService::aReservada).toList(), srvs);
    }

    private ReservaResponse armarRespuesta(Long eventoId) {
        List<ReservaEspacio> espacios = reservasEspacio.findByEventoIdAndEstado(eventoId, EstadoReserva.ACTIVA);
        List<InstalacionReservada> insts = instalaciones.findAllById(espacios.stream().map(ReservaEspacio::getInstalacionId).toList())
                .stream().sorted(Comparator.comparing(Instalacion::getId))
                .map(i -> aReservada(i)).toList();
        List<ReservaServicio> deServicios = reservasServicio.findByEventoIdAndEstado(eventoId, EstadoReserva.ACTIVA);
        Map<Long, ServicioComplementario> catalogo = new HashMap<>();
        servicios.findAllById(deServicios.stream().map(ReservaServicio::getServicioId).toList())
                .forEach(s -> catalogo.put(s.getId(), s));
        List<ServicioReservado> srvs = deServicios.stream().sorted(Comparator.comparing(ReservaServicio::getServicioId))
                .map(rs -> new ServicioReservado(rs.getServicioId(), catalogo.get(rs.getServicioId()).getNombre(),
                        rs.getCantidad(), catalogo.get(rs.getServicioId()).getPrecioUnitario())).toList();
        ReservaEspacio primera = espacios.get(0);
        return new ReservaResponse(eventoId, primera.getInicio(), primera.getFin(), insts, srvs);
    }
}
