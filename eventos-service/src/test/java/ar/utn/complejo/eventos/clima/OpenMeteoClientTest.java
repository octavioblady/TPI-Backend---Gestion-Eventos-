package ar.utn.complejo.eventos.clima;

import ar.utn.complejo.common.tiempo.Intervalo;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

/** Contrato con Open-Meteo probado contra un servidor simulado (respuesta normal, error HTTP, JSON inesperado y demora). */
class OpenMeteoClientTest {

    private HttpServer server;

    @AfterEach
    void parar() {
        if (server != null) server.stop(0);
    }

    private OpenMeteoClient cliente(int status, String cuerpo, long demoraMs) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/forecast", ex -> {
            try {
                Thread.sleep(demoraMs);
            } catch (InterruptedException ignorada) {
                Thread.currentThread().interrupt();
            }
            byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
        });
        server.start();
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/forecast";
        JdkClientHttpRequestFactory f = new JdkClientHttpRequestFactory(HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build());
        f.setReadTimeout(Duration.ofMillis(300));
        RestClient http = RestClient.builder().baseUrl(url).requestFactory(f).build();
        return new OpenMeteoClient(http, new ClimaProperties(url, -31.4201, -64.1888, 40, 50, 16, Duration.ofMillis(300)));
    }

    private static final Intervalo FRANJA = Intervalo.de(LocalDate.of(2026, 12, 10), LocalTime.of(18, 0), LocalTime.of(22, 0));

    private static final String JSON = """
            {"latitude":-31.4,"longitude":-64.2,"hourly":{
              "time":["2026-12-10T16:00","2026-12-10T17:00","2026-12-10T18:00","2026-12-10T19:00","2026-12-10T20:00","2026-12-10T21:00","2026-12-10T22:00"],
              "precipitation_probability":[90,90,10,55,20,null,95],
              "wind_gusts_10m":[80.0,80.0,12.5,30.0,61.2,20.0,90.0],
              "weather_code":[99,99,1,3,96,2,99]}}""";

    @Test
    void resumeSoloLasHorasDeLaFranjaDelEvento() throws Exception {
        Pronostico p = cliente(200, JSON, 0).pronosticoPara(FRANJA);
        assertEquals(55, p.probabilidadLluviaMaxima()); // ignora 90 (antes) y 95 (después de las 22:00)
        assertEquals(61.2, p.rafagaVientoMaximaKmh());
        assertTrue(p.granizo()); // código 96 a las 20:00
    }

    @Test
    void errorHttpSeTraduceAClimaNoDisponible() throws Exception {
        assertThrows(ClimaNoDisponibleException.class, () -> cliente(500, "{\"error\":true}", 0).pronosticoPara(FRANJA));
    }

    @Test
    void jsonInesperadoSeTraduceAClimaNoDisponible() throws Exception {
        assertThrows(ClimaNoDisponibleException.class, () -> cliente(200, "{\"hourly\":{\"time\":[\"x\"]}}", 0).pronosticoPara(FRANJA));
        assertThrows(ClimaNoDisponibleException.class, () -> cliente(200, "no es json", 0).pronosticoPara(FRANJA));
    }

    @Test
    void sinHorasParaLaFranjaEsUnaRespuestaInesperada() throws Exception {
        String otroDia = JSON.replace("2026-12-10", "2026-12-11");
        assertThrows(ClimaNoDisponibleException.class, () -> cliente(200, otroDia, 0).pronosticoPara(FRANJA));
    }

    @Test
    void siElServicioDemoraMasQueElTimeoutFalla() throws Exception {
        assertThrows(ClimaNoDisponibleException.class, () -> cliente(200, JSON, 1500).pronosticoPara(FRANJA));
    }
}
