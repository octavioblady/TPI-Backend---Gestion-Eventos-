package ar.utn.complejo.common.tiempo;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

class IntervaloTest {

    private static final LocalDate DIA = LocalDate.of(2026, 12, 10);

    private Intervalo i(String desde, String hasta) {
        return Intervalo.de(DIA, LocalTime.parse(desde), LocalTime.parse(hasta));
    }

    @Test
    void rechazaInicioPosteriorOIgualAlFin() {
        assertThrows(IllegalArgumentException.class, () -> i("10:00", "10:00"));
        assertThrows(IllegalArgumentException.class, () -> i("11:00", "10:00"));
    }

    @Test
    void intervalosConsecutivosNoSeSolapan() {
        assertFalse(i("10:00", "12:00").solapaCon(i("12:00", "14:00")));
        assertFalse(i("12:00", "14:00").solapaCon(i("10:00", "12:00")));
    }

    @Test
    void detectaSolapamientoParcialYTotal() {
        assertTrue(i("10:00", "12:00").solapaCon(i("11:59", "13:00")));
        assertTrue(i("10:00", "14:00").solapaCon(i("11:00", "12:00")));
        assertTrue(i("11:00", "12:00").solapaCon(i("10:00", "14:00")));
    }

    @Test
    void franjaHorariaAceptaBordes() {
        LocalTime apertura = LocalTime.of(8, 0);
        LocalTime cierre = LocalTime.of(22, 0);
        assertTrue(i("08:00", "22:00").dentroDeFranja(apertura, cierre));
        assertFalse(i("07:59", "10:00").dentroDeFranja(apertura, cierre));
        assertFalse(i("21:00", "23:00").dentroDeFranja(apertura, cierre));
    }

    @Test
    void franjaHorariaRechazaIntervaloQueCruzaDeDia() {
        Intervalo cruza = new Intervalo(DIA.atTime(22, 0), DIA.plusDays(1).atTime(2, 0));
        assertFalse(cruza.dentroDeFranja(LocalTime.of(8, 0), LocalTime.of(23, 59)));
    }

    @Test
    void horasFacturablesRedondeanHaciaArriba() {
        assertEquals(2, i("10:00", "12:00").horasFacturables());
        assertEquals(3, i("10:00", "12:01").horasFacturables());
        assertEquals(1, i("10:00", "10:30").horasFacturables());
    }

    @Test
    void contieneIntervalosInternos() {
        assertTrue(i("08:00", "20:00").contiene(i("09:00", "10:00")));
        assertFalse(i("08:00", "20:00").contiene(i("07:00", "10:00")));
    }

    @Test
    void solapamientoConFechasExplicitas() {
        Intervalo base = i("10:00", "12:00");
        assertTrue(base.solapaCon(DIA.atTime(11, 0), DIA.atTime(13, 0)));
        assertFalse(base.solapaCon(LocalDateTime.of(DIA, LocalTime.of(12, 0)), DIA.atTime(13, 0)));
    }
}
