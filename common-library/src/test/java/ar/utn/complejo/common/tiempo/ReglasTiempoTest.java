package ar.utn.complejo.common.tiempo;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReglasTiempoTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 12, 1, 10, 0);

    @Test
    void exactamente48HorasSeAcepta() {
        assertTrue(ReglasTiempo.cumpleAnticipacion(AHORA.plusHours(48), AHORA));
    }

    @Test
    void unMinutoAntesDe48HorasSeRechaza() {
        assertFalse(ReglasTiempo.cumpleAnticipacion(AHORA.plusHours(48).minusMinutes(1), AHORA));
    }

    @Test
    void eventoParaMananaSeRechaza() {
        assertFalse(ReglasTiempo.cumpleAnticipacion(AHORA.plusDays(1), AHORA));
    }

    @Test
    void cancelacionReembolsableSoloConAnticipacion() {
        assertTrue(ReglasTiempo.esCancelacionReembolsable(AHORA.plusHours(49), AHORA));
        assertFalse(ReglasTiempo.esCancelacionReembolsable(AHORA.plusHours(47), AHORA));
    }
}
