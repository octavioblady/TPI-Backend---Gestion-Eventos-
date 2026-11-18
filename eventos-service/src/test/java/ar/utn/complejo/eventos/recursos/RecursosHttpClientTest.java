package ar.utn.complejo.eventos.recursos;

import ar.utn.complejo.eventos.recursos.RecursosDtos.*;
import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Colaboración Eventos -> Recursos contra un servidor simulado y con Resilience4j real:
 * mapeo de respuestas, 4xx de negocio sin reintento, 5xx con reintento acotado, timeout y circuit breaker.
 */
@SpringBootTest
@ActiveProfiles("test")
class RecursosHttpClientTest {

    private static final HttpServer SERVER;
    private static final AtomicInteger LLAMADAS = new AtomicInteger();
    private static final AtomicReference<Respuesta> RESPUESTA = new AtomicReference<>();

    record Respuesta(int status, String cuerpo, long demoraMs) {
    }

    static {
        try {
            SERVER = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            SERVER.createContext("/", ex -> {
                LLAMADAS.incrementAndGet();
                ex.getRequestBody().readAllBytes();
                Respuesta r = RESPUESTA.get();
                try {
                    Thread.sleep(r.demoraMs());
                    byte[] bytes = r.cuerpo().getBytes(StandardCharsets.UTF_8);
                    ex.getResponseHeaders().add("Content-Type", "application/json");
                    ex.sendResponseHeaders(r.status(), bytes.length == 0 ? -1 : bytes.length);
                    if (bytes.length > 0) ex.getResponseBody().write(bytes);
                } catch (InterruptedException | IOException ignorada) {
                    // el cliente cortó por timeout
                } finally {
                    ex.close();
                }
            });
            SERVER.start();
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) {
        registry.add("recursos.url", () -> "http://127.0.0.1:" + SERVER.getAddress().getPort());
        registry.add("recursos.timeout.lectura", () -> "400ms");
    }

    @AfterAll
    static void parar() {
        SERVER.stop(0);
    }

    @Autowired RecursosClient cliente;
    @Autowired CircuitBreakerRegistry circuitos;

    private static final ReservaRequest PEDIDO = new ReservaRequest(1L, List.of(1L),
            LocalDateTime.of(2026, 12, 10, 18, 0), LocalDateTime.of(2026, 12, 10, 22, 0), 10, List.of());

    @BeforeEach
    void limpiar() {
        LLAMADAS.set(0);
        circuitos.circuitBreaker("recursos").reset();
        RESPUESTA.set(new Respuesta(200, "{}", 0));
    }

    @Test
    void reservaExitosaSeMapeaALaRespuesta() {
        RESPUESTA.set(new Respuesta(201, """
                {"eventoId":1,"inicio":"2026-12-10T18:00:00","fin":"2026-12-10T22:00:00",
                 "instalaciones":[{"instalacionId":1,"nombre":"SUM","capacidad":50,"aireLibre":false,"tarifaHora":10000.00}],"servicios":[]}""", 0));
        ReservaResponse r = cliente.reservar(PEDIDO);
        assertEquals(1, r.instalaciones().size());
        assertEquals(1, LLAMADAS.get());
    }

    @Test
    void unRechazoDeNegocioSePropagaConSuCodigo_sinReintentarNiAbrirElCircuito() {
        RESPUESTA.set(new Respuesta(409, """
                {"timestamp":"2026-12-01T10:00:00-03:00","status":409,"error":"Conflict","codigo":"CONFLICTO_DISPONIBILIDAD",
                 "mensaje":"La instalación ya está reservada en ese horario","path":"/internal/reservas","detalles":[]}""", 0));
        for (int i = 0; i < 8; i++) {
            RecursosRechazoException e = assertThrows(RecursosRechazoException.class, () -> cliente.reservar(PEDIDO));
            assertEquals("CONFLICTO_DISPONIBILIDAD", e.getCodigo());
            assertEquals(409, e.getStatus());
        }
        assertEquals(8, LLAMADAS.get(), "no se reintenta un rechazo de negocio");
        assertEquals(CircuitBreaker.State.CLOSED, circuitos.circuitBreaker("recursos").getState());
    }

    @Test
    void unRechazoSinCuerpoEstandarUsaMensajeGenerico() {
        RESPUESTA.set(new Respuesta(404, "", 0));
        RecursosRechazoException e = assertThrows(RecursosRechazoException.class, () -> cliente.reservar(PEDIDO));
        assertEquals(404, e.getStatus());
        assertEquals("ERROR_RECURSOS", e.getCodigo());
    }

    @Test
    void unErrorDelServidorSeReintentaUnaVezYSeInformaComoNoDisponible() {
        RESPUESTA.set(new Respuesta(500, "{}", 0));
        RecursosNoDisponibleException e = assertThrows(RecursosNoDisponibleException.class, () -> cliente.reservar(PEDIDO));
        assertFalse(e.esTimeout());
        assertEquals(2, LLAMADAS.get(), "un intento más (reintento acotado)");
    }

    @Test
    void siRecursosEsMasLentoQueElTimeoutSeInformaComoTimeout() {
        RESPUESTA.set(new Respuesta(200, "{}", 1500));
        RecursosNoDisponibleException e = assertThrows(RecursosNoDisponibleException.class, () -> cliente.reservar(PEDIDO));
        assertTrue(e.esTimeout());
    }

    @Test
    void trasVariasFallasElCircuitoSeAbreYLasLlamadasFallanDeInmediato() {
        RESPUESTA.set(new Respuesta(500, "{}", 0));
        for (int i = 0; i < 3; i++) {
            // El circuito se abre en medio del tercer pedido: su reintento ya es rechazado por el circuito abierto.
            assertThrows(RuntimeException.class, () -> cliente.reservar(PEDIDO));
        }
        assertEquals(CircuitBreaker.State.OPEN, circuitos.circuitBreaker("recursos").getState());

        int antes = LLAMADAS.get();
        long t0 = System.nanoTime();
        assertThrows(CallNotPermittedException.class, () -> cliente.reservar(PEDIDO));
        assertEquals(antes, LLAMADAS.get(), "con el circuito abierto no se llega a Recursos");
        assertTrue((System.nanoTime() - t0) / 1_000_000 < 200, "falla rápido, sin esperar el timeout");
    }

    @Test
    void liberarYCotizarTambienUsanElMismoContrato() {
        RESPUESTA.set(new Respuesta(204, "", 0));
        assertDoesNotThrow(() -> cliente.liberar(1L));
        RESPUESTA.set(new Respuesta(200, "{\"instalaciones\":[],\"servicios\":[]}", 0));
        assertNotNull(cliente.cotizar(new CotizacionRequest(List.of(1L), List.of())));
    }
}
