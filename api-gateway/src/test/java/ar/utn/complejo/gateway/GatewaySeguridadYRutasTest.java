package ar.utn.complejo.gateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import reactor.core.publisher.Mono;
import java.time.Instant;
import java.util.Map;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

/**
 * Security Filter y enrutamiento: 401/403 resueltos en el borde (sin llegar al servicio), rutas hacia cada microservicio,
 * reenvío del token y ausencia de ruta para /internal/**. Los microservicios se simulan con servidores locales.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
@Import(GatewaySeguridadYRutasTest.DecoderDeTest.class)
class GatewaySeguridadYRutasTest {

    /** Decodificador simulado: acepta 'token-admin' y 'token-cliente' (con sus roles de realm) y rechaza todo lo demás, como lo haría una firma inválida. */
    @TestConfiguration
    static class DecoderDeTest {
        @Bean
        @Primary
        ReactiveJwtDecoder decoder() {
            return token -> switch (token) {
                case "token-admin" -> Mono.just(jwt(token, "ADMIN"));
                case "token-cliente" -> Mono.just(jwt(token, "CLIENTE"));
                default -> Mono.error(new BadJwtException("firma inválida"));
            };
        }

        private static Jwt jwt(String token, String rol) {
            return new Jwt(token, Instant.now(), Instant.now().plusSeconds(300), Map.of("alg", "none"),
                    Map.of("sub", "u-" + rol, "realm_access", Map.of("roles", java.util.List.of(rol))));
        }
    }

    record Visita(String servicio, String metodo, String uri, String authorization) {
    }

    private static final List<Visita> VISITAS = new CopyOnWriteArrayList<>();
    private static final HttpServer RECURSOS = servidor("recursos");
    private static final HttpServer EVENTOS = servidor("eventos");

    private static HttpServer servidor(String nombre) {
        try {
            HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            s.createContext("/", ex -> {
                VISITAS.add(new Visita(nombre, ex.getRequestMethod(), ex.getRequestURI().toString(), ex.getRequestHeaders().getFirst("Authorization")));
                byte[] bytes = ("{\"servicio\":\"" + nombre + "\"}").getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().add("Content-Type", "application/json");
                ex.sendResponseHeaders(200, bytes.length);
                ex.getResponseBody().write(bytes);
                ex.close();
            });
            s.start();
            return s;
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void rutas(DynamicPropertyRegistry registry) {
        registry.add("RECURSOS_SERVICE_URL", () -> "http://127.0.0.1:" + RECURSOS.getAddress().getPort());
        registry.add("EVENTOS_SERVICE_URL", () -> "http://127.0.0.1:" + EVENTOS.getAddress().getPort());
    }

    @AfterAll
    static void parar() {
        RECURSOS.stop(0);
        EVENTOS.stop(0);
    }

    @Autowired
    WebTestClient web;

    @BeforeEach
    void limpiar() {
        VISITAS.clear();
    }

    private WebTestClient como(String rol) {
        return web.mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("ROLE_" + rol)));
    }

    // ---- 401 / 403 respondidos por el Gateway: la solicitud no llega al servicio ----

    @Test
    void sinTokenResponde401ConErrorEstandar_yNoLlegaAlServicio() {
        web.get().uri("/api/v1/instalaciones").exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.codigo").isEqualTo("NO_AUTENTICADO");
        web.get().uri("/api/v1/disponibilidad?fecha=2026-12-10&horaInicio=10:00&horaFin=12:00").exchange().expectStatus().isUnauthorized();
        web.get().uri("/api/v1/eventos").exchange().expectStatus().isUnauthorized();
        assertTrue(VISITAS.isEmpty());
    }

    @Test
    void tokenInvalidoResponde401() {
        web.get().uri("/api/v1/instalaciones").header("Authorization", "Bearer esto.no.es-un-jwt").exchange()
                .expectStatus().isUnauthorized();
        assertTrue(VISITAS.isEmpty());
    }

    @Test
    void clienteNoPuedeEscribirInstalacionesNiServiciosNiMantenimientos() {
        como("CLIENTE").post().uri("/api/v1/instalaciones").bodyValue("{}").exchange()
                .expectStatus().isForbidden().expectBody().jsonPath("$.codigo").isEqualTo("SIN_PERMISOS");
        como("CLIENTE").put().uri("/api/v1/instalaciones/1").bodyValue("{}").exchange().expectStatus().isForbidden();
        como("CLIENTE").delete().uri("/api/v1/instalaciones/1").exchange().expectStatus().isForbidden();
        como("CLIENTE").post().uri("/api/v1/servicios").bodyValue("{}").exchange().expectStatus().isForbidden();
        como("CLIENTE").post().uri("/api/v1/mantenimientos").bodyValue("{}").exchange().expectStatus().isForbidden();
        como("CLIENTE").patch().uri("/api/v1/mantenimientos/1/finalizacion").exchange().expectStatus().isForbidden();
        assertTrue(VISITAS.isEmpty());
    }

    @Test
    void clienteNoPuedeRegistrarSeniaIniciarNiFinalizarEventos() {
        como("CLIENTE").patch().uri("/api/v1/eventos/1/senia").exchange().expectStatus().isForbidden();
        como("CLIENTE").patch().uri("/api/v1/eventos/1/inicio").exchange().expectStatus().isForbidden();
        como("CLIENTE").patch().uri("/api/v1/eventos/1/finalizacion").exchange().expectStatus().isForbidden();
        assertTrue(VISITAS.isEmpty());
    }

    @Test
    void tokenSinRolesConocidosEsRechazadoConForbidden() {
        como("OFFLINE_ACCESS").get().uri("/api/v1/instalaciones").exchange().expectStatus().isForbidden();
        como("SERVICIO").get().uri("/api/v1/instalaciones").exchange().expectStatus().isForbidden();
    }

    // ---- Permitidos: se enrutan al servicio correcto y se reenvía el token ----

    @Test
    void clienteConsultaInstalacionesYDisponibilidadEnRecursos() {
        como("CLIENTE").get().uri("/api/v1/instalaciones").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.servicio").isEqualTo("recursos");
        como("CLIENTE").get().uri("/api/v1/disponibilidad?fecha=2026-12-10&horaInicio=10:00&horaFin=12:00").exchange().expectStatus().isOk();
        assertEquals(2, VISITAS.stream().filter(v -> v.servicio().equals("recursos")).count());
    }

    @Test
    void adminAdministraRecursos() {
        como("ADMIN").post().uri("/api/v1/instalaciones").bodyValue("{}").exchange().expectStatus().isOk();
        como("ADMIN").post().uri("/api/v1/mantenimientos").bodyValue("{}").exchange().expectStatus().isOk();
        como("ADMIN").put().uri("/api/v1/servicios/1").bodyValue("{}").exchange().expectStatus().isOk();
        assertEquals(3, VISITAS.size());
    }

    @Test
    void losEventosVanAEventosService_yClienteYAdminPuedenUsarlos() {
        como("CLIENTE").post().uri("/api/v1/eventos").bodyValue("{}").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.servicio").isEqualTo("eventos");
        como("CLIENTE").patch().uri("/api/v1/eventos/1/cancelacion").exchange().expectStatus().isOk();
        como("ADMIN").patch().uri("/api/v1/eventos/1/senia").exchange().expectStatus().isOk();
        assertEquals(3, VISITAS.stream().filter(v -> v.servicio().equals("eventos")).count());
    }

    @Test
    void elTokenSeReenviaAlServicioTalCualLlego() {
        web.get().uri("/api/v1/instalaciones").header("Authorization", "Bearer token-admin").exchange().expectStatus().isOk();
        assertEquals("Bearer token-admin", VISITAS.get(0).authorization());
    }

    @Test
    void elFlujoCompletoConRolesDeRealmDelTokenFunciona() {
        // Sin simulaciones de seguridad: encabezado Bearer -> decodificación -> conversor de roles de Keycloak -> autorización por ruta.
        web.get().uri("/api/v1/instalaciones").header("Authorization", "Bearer token-cliente").exchange().expectStatus().isOk();
        web.post().uri("/api/v1/instalaciones").header("Authorization", "Bearer token-cliente").bodyValue("{}").exchange().expectStatus().isForbidden();
        web.post().uri("/api/v1/instalaciones").header("Authorization", "Bearer token-admin").bodyValue("{}").exchange().expectStatus().isOk();
    }

    // ---- Trusted zone ----

    @Test
    void internalNoTieneRuta_404ConCualquierToken() {
        web.post().uri("/internal/reservas").bodyValue("{}").exchange().expectStatus().isNotFound();
        como("ADMIN").post().uri("/internal/reservas").bodyValue("{}").exchange().expectStatus().isNotFound();
        como("SERVICIO").post().uri("/internal/reservas").bodyValue("{}").exchange().expectStatus().isNotFound();
        como("ADMIN").get().uri("/internal/reservas/cotizacion").exchange().expectStatus().isNotFound();
        assertTrue(VISITAS.isEmpty(), "ninguna solicitud /internal/** llega a los servicios");
    }

    @Test
    void rutasDesconocidasNoLlegan() {
        como("ADMIN").get().uri("/actuator/env").exchange().expectStatus().isNotFound();
        assertTrue(VISITAS.isEmpty());
    }

    // ---- Documentación OpenAPI vía Gateway ----

    @Test
    void laEspecificacionDeCadaServicioSePublicaSinTokenReescribiendoLaRuta() {
        web.get().uri("/docs/recursos/v3/api-docs").exchange().expectStatus().isOk();
        web.get().uri("/docs/eventos/v3/api-docs").exchange().expectStatus().isOk();
        assertEquals("/v3/api-docs", VISITAS.get(0).uri());
        assertEquals("recursos", VISITAS.get(0).servicio());
        assertEquals("eventos", VISITAS.get(1).servicio());
    }

    @Test
    void swaggerUiSeServeDesdeElGateway() {
        web.get().uri("/swagger-ui.html").exchange().expectStatus().is3xxRedirection();
        web.get().uri("/webjars/swagger-ui/index.html").exchange().expectStatus().isOk();
    }
}
