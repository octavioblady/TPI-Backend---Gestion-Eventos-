package ar.utn.complejo.recursos.service;

import ar.utn.complejo.common.error.BusinessException;
import ar.utn.complejo.common.tiempo.ReglasTiempo;
import ar.utn.complejo.recursos.domain.ServicioComplementario;
import ar.utn.complejo.recursos.dto.ServicioDtos.*;
import ar.utn.complejo.recursos.repository.ServicioComplementarioRepository;
import ar.utn.complejo.recursos.repository.StockServicioFechaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Service
public class ServicioComplementarioService {

    private final ServicioComplementarioRepository servicios;
    private final StockServicioFechaRepository stock;
    private final Clock reloj;

    public ServicioComplementarioService(ServicioComplementarioRepository servicios, StockServicioFechaRepository stock, Clock reloj) {
        this.servicios = servicios;
        this.stock = stock;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public List<ServicioResponse> listar() {
        return servicios.findByActivoTrueOrderByNombre().stream().map(ServicioComplementarioService::aDto).toList();
    }

    @Transactional
    public ServicioResponse registrar(ServicioRequest req) {
        if (servicios.existsByNombreIgnoreCase(req.nombre())) {
            throw BusinessException.conflicto("SERVICIO_DUPLICADO", "Ya existe un servicio con ese nombre");
        }
        ServicioComplementario s = new ServicioComplementario();
        aplicar(s, req);
        return aDto(servicios.save(s));
    }

    /** F-05: modifica precio, descripción o stock. No permite bajar el stock por debajo de lo ya comprometido. */
    @Transactional
    public ServicioResponse modificar(Long id, ServicioRequest req) {
        ServicioComplementario s = buscar(id);
        if (servicios.existsByNombreIgnoreCaseAndIdNot(req.nombre(), id)) {
            throw BusinessException.conflicto("SERVICIO_DUPLICADO", "Ya existe un servicio con ese nombre");
        }
        int comprometido = stock.maximaReservaDesde(id, ReglasTiempo.ahora(reloj).toLocalDate());
        if (req.stockTotal() < comprometido) {
            throw BusinessException.conflicto("STOCK_MENOR_A_RESERVADO",
                    "El stock no puede ser menor a las " + comprometido + " unidades ya comprometidas");
        }
        aplicar(s, req);
        return aDto(s);
    }

    @Transactional(readOnly = true)
    public StockResponse stock(Long id, LocalDate fecha) {
        ServicioComplementario s = buscar(id);
        int reservado = stock.findByServicioIdAndFecha(id, fecha).map(x -> x.getCantidadReservada()).orElse(0);
        return new StockResponse(id, fecha, s.getStockTotal(), reservado, Math.max(0, s.getStockTotal() - reservado));
    }

    private ServicioComplementario buscar(Long id) {
        return servicios.findById(id).orElseThrow(() ->
                BusinessException.noEncontrado("SERVICIO_NO_ENCONTRADO", "No existe el servicio " + id));
    }

    private static void aplicar(ServicioComplementario s, ServicioRequest req) {
        s.setNombre(req.nombre().trim());
        s.setDescripcion(req.descripcion());
        s.setPrecioUnitario(req.precioUnitario());
        s.setStockTotal(req.stockTotal());
    }

    private static ServicioResponse aDto(ServicioComplementario s) {
        return new ServicioResponse(s.getId(), s.getNombre(), s.getDescripcion(), s.getPrecioUnitario(), s.getStockTotal(), s.isActivo());
    }
}
