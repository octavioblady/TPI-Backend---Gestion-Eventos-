package ar.utn.complejo.eventos;

import ar.utn.complejo.common.tiempo.Intervalo;
import ar.utn.complejo.eventos.recursos.RecursosDtos.*;
import ar.utn.complejo.eventos.service.PrecioCalculator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PrecioCalculatorTest {

    private static final LocalDate DIA = LocalDate.of(2026, 12, 10);

    private static InstalacionInfo inst(long id) {
        return new InstalacionInfo(id, "I" + id, 50, false, new BigDecimal("10000.00"));
    }

    @Test
    void unaInstalacionPorHora_masServicios_ySeniaDel50() {
        CotizacionResponse c = new CotizacionResponse(List.of(inst(1)),
                List.of(new ServicioInfo(1L, "Sillas", 20, new BigDecimal("200.00"))));
        var d = PrecioCalculator.calcular(c, Intervalo.de(DIA, LocalTime.of(18, 0), LocalTime.of(22, 0)), 50);

        assertEquals(new BigDecimal("40000.00"), d.instalaciones().get(0).getSubtotal());
        assertEquals(new BigDecimal("4000.00"), d.servicios().get(0).getSubtotal());
        assertEquals(new BigDecimal("44000.00"), d.total());
        assertEquals(new BigDecimal("22000.00"), d.senia());
    }

    @Test
    void cadaInstalacionSeFacturaPorSeparado() {
        CotizacionResponse c = new CotizacionResponse(List.of(inst(1), inst(2), inst(3)), List.of());
        var d = PrecioCalculator.calcular(c, Intervalo.de(DIA, LocalTime.of(10, 0), LocalTime.of(12, 0)), 50);
        assertEquals(new BigDecimal("60000.00"), d.total());
    }

    @Test
    void lasFraccionesDeHoraSeRedondeanHaciaArriba() {
        CotizacionResponse c = new CotizacionResponse(List.of(inst(1)), List.of());
        var d = PrecioCalculator.calcular(c, Intervalo.de(DIA, LocalTime.of(10, 0), LocalTime.of(12, 30)), 50);
        assertEquals(3, d.instalaciones().get(0).getHorasFacturadas());
        assertEquals(new BigDecimal("30000.00"), d.total());
    }

    @Test
    void laSeniaSeRedondeaAlCentavo() {
        CotizacionResponse c = new CotizacionResponse(List.of(), List.of(new ServicioInfo(1L, "X", 1, new BigDecimal("0.03"))));
        var d = PrecioCalculator.calcular(c, Intervalo.de(DIA, LocalTime.of(10, 0), LocalTime.of(11, 0)), 50);
        assertEquals(new BigDecimal("0.02"), d.senia()); // 0,015 -> 0,02 (HALF_UP)
    }
}
