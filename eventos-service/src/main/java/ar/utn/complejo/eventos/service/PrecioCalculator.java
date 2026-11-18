package ar.utn.complejo.eventos.service;

import ar.utn.complejo.common.tiempo.Intervalo;
import ar.utn.complejo.eventos.domain.EventoInstalacion;
import ar.utn.complejo.eventos.domain.EventoServicio;
import ar.utn.complejo.eventos.recursos.RecursosDtos.CotizacionResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Cálculo de precio (F-04). Instalaciones: tarifa por hora × horas facturables (se redondea hacia arriba a la hora completa),
 * por cada instalación. Servicios: precio unitario × cantidad. Seña: porcentaje fijo del total.
 */
public final class PrecioCalculator {

    public record Desglose(List<EventoInstalacion> instalaciones, List<EventoServicio> servicios, BigDecimal total, BigDecimal senia) {
    }

    private PrecioCalculator() {
    }

    public static Desglose calcular(CotizacionResponse cotizacion, Intervalo intervalo, int seniaPorcentaje) {
        long horas = intervalo.horasFacturables();
        List<EventoInstalacion> instalaciones = cotizacion.instalaciones().stream().map(i -> {
            EventoInstalacion ei = new EventoInstalacion();
            ei.setInstalacionId(i.instalacionId());
            ei.setNombre(i.nombre());
            ei.setAireLibre(i.aireLibre());
            ei.setTarifaHora(i.tarifaHora());
            ei.setHorasFacturadas(horas);
            ei.setSubtotal(i.tarifaHora().multiply(BigDecimal.valueOf(horas)).setScale(2, RoundingMode.HALF_UP));
            return ei;
        }).toList();
        List<EventoServicio> servicios = cotizacion.servicios().stream().map(s -> {
            EventoServicio es = new EventoServicio();
            es.setServicioId(s.servicioId());
            es.setNombre(s.nombre());
            es.setCantidad(s.cantidad());
            es.setPrecioUnitario(s.precioUnitario());
            es.setSubtotal(s.precioUnitario().multiply(BigDecimal.valueOf(s.cantidad())).setScale(2, RoundingMode.HALF_UP));
            return es;
        }).toList();
        BigDecimal total = instalaciones.stream().map(EventoInstalacion::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .add(servicios.stream().map(EventoServicio::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal senia = total.multiply(BigDecimal.valueOf(seniaPorcentaje)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        return new Desglose(instalaciones, servicios, total, senia);
    }
}
