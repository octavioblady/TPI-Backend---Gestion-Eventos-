package ar.utn.complejo.eventos.clima;

import ar.utn.complejo.eventos.domain.Evento;
import ar.utn.complejo.eventos.domain.EventoInstalacion;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

class ClimaServiceTest {

    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Cordoba");
    private static final Clock RELOJ = Clock.fixed(LocalDateTime.of(2026, 12, 1, 10, 0).atZone(ZONA).toInstant(), ZONA);
    private static final ClimaProperties PROPS = new ClimaProperties("http://x", -31.4201, -64.1888, 40, 50, 16, Duration.ofSeconds(2));

    private int llamadas;

    private ClimaService servicio(PronosticoProvider p) {
        return new ClimaService(franja -> {
            llamadas++;
            return p.pronosticoPara(franja);
        }, PROPS, RELOJ);
    }

    private Evento evento(boolean aireLibre, LocalDateTime inicio) {
        Evento e = new Evento();
        e.setInicio(inicio);
        e.setFin(inicio.plusHours(4));
        EventoInstalacion i = new EventoInstalacion();
        i.setAireLibre(aireLibre);
        e.getInstalaciones().add(i);
        return e;
    }

    private static final LocalDateTime EN_9_DIAS = LocalDateTime.of(2026, 12, 10, 18, 0);

    @Test
    void eventoSinAireLibreNoConsultaNada() {
        var r = servicio(f -> new Pronostico(100, 100, true)).evaluar(evento(false, EN_9_DIAS));
        assertEquals(ClimaService.Estado.NO_APLICA, r.estado());
        assertEquals(0, llamadas);
    }

    @Test
    void lluviaDel41PorCientoAdvierte_peroElIgualAlUmbralNo() {
        assertEquals(ClimaService.Estado.ADVERTENCIA,
                servicio(f -> new Pronostico(41, 10, false)).evaluar(evento(true, EN_9_DIAS)).estado());
        assertEquals(ClimaService.Estado.SIN_ADVERTENCIA,
                servicio(f -> new Pronostico(40, 10, false)).evaluar(evento(true, EN_9_DIAS)).estado());
    }

    @Test
    void granizoAdvierteAunSinLluviaAlta() {
        var r = servicio(f -> new Pronostico(0, 0, true)).evaluar(evento(true, EN_9_DIAS));
        assertEquals(ClimaService.Estado.ADVERTENCIA, r.estado());
        assertTrue(r.advertencia().orElseThrow().isGranizo());
    }

    @Test
    void ventoFuerteAdvierteDesdeElUmbral() {
        assertEquals(ClimaService.Estado.ADVERTENCIA, servicio(f -> new Pronostico(0, 50, false)).evaluar(evento(true, EN_9_DIAS)).estado());
        assertEquals(ClimaService.Estado.SIN_ADVERTENCIA, servicio(f -> new Pronostico(0, 49.9, false)).evaluar(evento(true, EN_9_DIAS)).estado());
    }

    @Test
    void masDe16DiasSeOmiteLaConsulta() {
        var r = servicio(f -> new Pronostico(100, 100, true)).evaluar(evento(true, LocalDateTime.of(2026, 12, 18, 18, 0)));
        assertEquals(ClimaService.Estado.FUERA_DE_HORIZONTE, r.estado());
        assertEquals(0, llamadas);
    }

    @Test
    void exactamente16DiasSeConsulta() {
        var r = servicio(f -> new Pronostico(0, 0, false)).evaluar(evento(true, LocalDateTime.of(2026, 12, 17, 18, 0)));
        assertEquals(ClimaService.Estado.SIN_ADVERTENCIA, r.estado());
        assertEquals(1, llamadas);
    }

    @Test
    void siElServicioExternoFallaNoHayAdvertenciaYNoSePropagaElError() {
        var r = servicio(f -> { throw new ClimaNoDisponibleException("caído", null); }).evaluar(evento(true, EN_9_DIAS));
        assertEquals(ClimaService.Estado.NO_DISPONIBLE, r.estado());
        assertTrue(r.advertencia().isEmpty());
    }
}
