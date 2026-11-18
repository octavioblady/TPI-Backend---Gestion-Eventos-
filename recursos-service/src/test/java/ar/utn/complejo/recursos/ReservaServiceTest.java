package ar.utn.complejo.recursos;

import ar.utn.complejo.common.error.BusinessException;
import ar.utn.complejo.recursos.domain.EstadoReserva;
import ar.utn.complejo.recursos.domain.Mantenimiento;
import ar.utn.complejo.recursos.dto.ReservaDtos.*;
import ar.utn.complejo.recursos.service.ReservaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Reglas de negocio 1 a 5 y la atomicidad de la reserva multiespacio (compensación del lado de Recursos). */
class ReservaServiceTest extends BaseIntegrationTest {

    @Autowired
    ReservaService reservaService;

    private ReservaRequest pedido(long eventoId, List<Long> ids, String desde, String hasta, int asistentes, List<ServicioSolicitado> srv) {
        LocalDate fecha = FECHA_OK;
        return new ReservaRequest(eventoId, ids, fecha.atTime(LocalTime.parse(desde)), fecha.atTime(LocalTime.parse(hasta)), asistentes, srv);
    }

    private void assertRechazo(String codigo, Runnable accion) {
        BusinessException ex = assertThrows(BusinessException.class, accion::run);
        assertEquals(codigo, ex.getCodigo());
        assertEquals(409, ex.getStatus());
    }

    private int reservado() {
        return stock.findByServicioIdAndFecha(sillas.getId(), FECHA_OK).map(s -> s.getCantidadReservada()).orElse(0);
    }

    @Test
    void reservaExitosaDescuentaStockYDevuelveTarifas() {
        ReservaResponse r = reservaService.reservar(pedido(1, List.of(sum.getId()), "18:00", "22:00", 30,
                List.of(new ServicioSolicitado(sillas.getId(), 20))));

        assertEquals(1, r.instalaciones().size());
        assertEquals(0, r.instalaciones().get(0).tarifaHora().compareTo(new java.math.BigDecimal("10000")));
        assertEquals(20, r.servicios().get(0).cantidad());
        assertEquals(20, reservado());
    }

    @Test
    void reservaCombinadaDeCanchaYSalon() {
        ReservaResponse r = reservaService.reservar(pedido(2, List.of(cancha.getId(), salon.getId()), "10:00", "14:00", 80, null));
        assertEquals(2, r.instalaciones().size());
        assertEquals(2, reservasEspacio.findByEventoIdAndEstado(2L, EstadoReserva.ACTIVA).size());
    }

    @Test
    void reintentoDelMismoEventoEsIdempotente() {
        ReservaRequest req = pedido(3, List.of(sum.getId()), "10:00", "12:00", 10, List.of(new ServicioSolicitado(sillas.getId(), 5)));
        reservaService.reservar(req);
        reservaService.reservar(req);
        assertEquals(1, reservasEspacio.findByEventoIdAndEstado(3L, EstadoReserva.ACTIVA).size());
        assertEquals(5, reservado());
    }

    @Test
    void regla1_rechazaConMenosDe48Horas() {
        ReservaRequest manana = new ReservaRequest(4L, List.of(sum.getId()), LocalDateTime.of(2026, 12, 2, 18, 0),
                LocalDateTime.of(2026, 12, 2, 20, 0), 10, null);
        assertRechazo("ANTICIPACION_INSUFICIENTE", () -> reservaService.reservar(manana));
    }

    @Test
    void regla1_aceptaExactamente48Horas() {
        // AHORA = 1/12 10:00 -> 3/12 10:00 es exactamente 48 h.
        ReservaRequest limite = new ReservaRequest(5L, List.of(sum.getId()), LocalDateTime.of(2026, 12, 3, 10, 0),
                LocalDateTime.of(2026, 12, 3, 12, 0), 10, null);
        assertDoesNotThrow(() -> reservaService.reservar(limite));
    }

    @Test
    void regla2_rechazaInstalacionEnMantenimiento() {
        Mantenimiento m = new Mantenimiento();
        m.setInstalacionId(sum.getId());
        m.setDesde(FECHA_OK.atTime(0, 0));
        m.setHasta(FECHA_OK.plusDays(2).atTime(0, 0));
        mantenimientos.save(m);
        assertRechazo("MANTENIMIENTO", () -> reservaService.reservar(pedido(6, List.of(sum.getId()), "18:00", "20:00", 10, null)));
    }

    @Test
    void regla3_capacidadSeValidaContraLaInstalacionMasChicaDelConjunto() {
        // salón (150) alcanza, pero el SUM (50) no.
        assertRechazo("CAPACIDAD_EXCEDIDA",
                () -> reservaService.reservar(pedido(7, List.of(salon.getId(), sum.getId()), "10:00", "12:00", 80, null)));
        assertTrue(reservasEspacio.findByEventoIdAndEstado(7L, EstadoReserva.ACTIVA).isEmpty());
    }

    @Test
    void regla3_capacidadExactaSeAcepta() {
        assertDoesNotThrow(() -> reservaService.reservar(pedido(8, List.of(sum.getId()), "10:00", "12:00", 50, null)));
    }

    @Test
    void regla4_rechazaFueraDeHorarioDeLaCancha() {
        // La cancha cierra a las 22:00.
        assertRechazo("FUERA_DE_HORARIO", () -> reservaService.reservar(pedido(9, List.of(cancha.getId()), "21:00", "23:00", 10, null)));
        assertRechazo("FUERA_DE_HORARIO", () -> reservaService.reservar(pedido(9, List.of(cancha.getId()), "03:00", "05:00", 10, null)));
    }

    @Test
    void regla4_aceptaTerminarExactamenteAlCierre() {
        assertDoesNotThrow(() -> reservaService.reservar(pedido(10, List.of(cancha.getId()), "20:00", "22:00", 10, null)));
    }

    @Test
    void rechazaSuperposicionEnLaMismaFranja() {
        reservaService.reservar(pedido(11, List.of(sum.getId()), "18:00", "22:00", 10, null));
        assertRechazo("CONFLICTO_DISPONIBILIDAD", () -> reservaService.reservar(pedido(12, List.of(sum.getId()), "20:00", "23:00", 10, null)));
    }

    @Test
    void reservasConsecutivasNoSeSuperponen() {
        reservaService.reservar(pedido(13, List.of(sum.getId()), "10:00", "12:00", 10, null));
        assertDoesNotThrow(() -> reservaService.reservar(pedido(14, List.of(sum.getId()), "12:00", "14:00", 10, null)));
    }

    @Test
    void multiespacioEsTodoONada_siLaSegundaInstalacionEstaOcupadaNoQuedaNadaReservado() {
        reservaService.reservar(pedido(20, List.of(salon.getId()), "10:00", "14:00", 10, null));
        assertRechazo("CONFLICTO_DISPONIBILIDAD", () -> reservaService.reservar(pedido(21,
                List.of(cancha.getId(), salon.getId()), "11:00", "13:00", 10, List.of(new ServicioSolicitado(sillas.getId(), 10)))));

        assertTrue(reservasEspacio.findByEventoIdAndEstado(21L, EstadoReserva.ACTIVA).isEmpty(), "la cancha no debe quedar reservada");
        assertTrue(reservasServicio.findByEventoIdAndEstado(21L, EstadoReserva.ACTIVA).isEmpty());
        assertEquals(0, reservado(), "el stock no debe quedar descontado");
    }

    @Test
    void rechazaStockInsuficiente() {
        assertRechazo("STOCK_INSUFICIENTE", () -> reservaService.reservar(pedido(30, List.of(sum.getId()), "10:00", "12:00", 10,
                List.of(new ServicioSolicitado(sillas.getId(), 51)))));
        assertTrue(reservasEspacio.findByEventoIdAndEstado(30L, EstadoReserva.ACTIVA).isEmpty());
    }

    @Test
    void elStockSeAcumulaPorFecha() {
        reservaService.reservar(pedido(31, List.of(sum.getId()), "10:00", "12:00", 10, List.of(new ServicioSolicitado(sillas.getId(), 30))));
        assertRechazo("STOCK_INSUFICIENTE", () -> reservaService.reservar(pedido(32, List.of(cancha.getId()), "10:00", "12:00", 10,
                List.of(new ServicioSolicitado(sillas.getId(), 21)))));
        assertDoesNotThrow(() -> reservaService.reservar(pedido(33, List.of(cancha.getId()), "10:00", "12:00", 10,
                List.of(new ServicioSolicitado(sillas.getId(), 20)))));
    }

    @Test
    void liberarDevuelveEspaciosYStock_yEsIdempotente() {
        reservaService.reservar(pedido(40, List.of(sum.getId()), "10:00", "12:00", 10, List.of(new ServicioSolicitado(sillas.getId(), 25))));
        reservaService.liberar(40L);
        reservaService.liberar(40L);

        assertEquals(0, reservado());
        assertTrue(reservasEspacio.findByEventoIdAndEstado(40L, EstadoReserva.ACTIVA).isEmpty());
        // y el espacio vuelve a poder reservarse
        assertDoesNotThrow(() -> reservaService.reservar(pedido(41, List.of(sum.getId()), "10:00", "12:00", 10, null)));
    }

    @Test
    void instalacionInexistenteDevuelve404() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> reservaService.reservar(pedido(50, List.of(9999L), "10:00", "12:00", 10, null)));
        assertEquals(404, ex.getStatus());
    }
}
