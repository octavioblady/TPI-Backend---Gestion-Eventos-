package ar.utn.complejo.eventos;

import ar.utn.complejo.eventos.clima.PronosticoProvider;
import ar.utn.complejo.eventos.recursos.RecursosClient;
import ar.utn.complejo.eventos.recursos.RecursosDtos.*;
import ar.utn.complejo.eventos.repository.EventoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

/** Hora fija: martes 1/12/2026 10:00 (Córdoba). Recursos y el servicio meteorológico se simulan con mocks. */
@SpringBootTest
@ActiveProfiles("test")
@Import(BaseEventosTest.RelojFijo.class)
public abstract class BaseEventosTest {

    protected static final LocalDateTime AHORA = LocalDateTime.of(2026, 12, 1, 10, 0);

    @TestConfiguration
    static class RelojFijo {
        @Bean
        @Primary
        Clock relojDeTest() {
            ZoneId zona = ZoneId.of("America/Argentina/Cordoba");
            return Clock.fixed(AHORA.atZone(zona).toInstant(), zona);
        }
    }

    @MockitoBean protected RecursosClient recursos;
    @MockitoBean protected PronosticoProvider pronostico;
    @Autowired protected EventoRepository eventos;

    @BeforeEach
    void preparar() {
        eventos.deleteAll();
        reset(recursos, pronostico);
        // Catálogo simulado: 1 = SUM (cubierto), 2 = Salón y patio (aire libre), 3 = Cancha. Tarifa $10.000/h.
        when(recursos.cotizar(any())).thenAnswer(inv -> {
            CotizacionRequest req = inv.getArgument(0);
            List<InstalacionInfo> insts = req.instalacionIds().stream().map(id ->
                    new InstalacionInfo(id, "Instalación " + id, 100, id == 2L, new BigDecimal("10000.00"))).toList();
            List<ServicioInfo> srvs = req.servicios() == null ? List.of() : req.servicios().stream().map(s ->
                    new ServicioInfo(s.servicioId(), "Servicio " + s.servicioId(), s.cantidad(), new BigDecimal("200.00"))).toList();
            return new CotizacionResponse(insts, srvs);
        });
        when(recursos.reservar(any())).thenAnswer(inv -> {
            ReservaRequest req = inv.getArgument(0);
            return new ReservaResponse(req.eventoId(), req.inicio(), req.fin(), List.of(), List.of());
        });
    }
}
