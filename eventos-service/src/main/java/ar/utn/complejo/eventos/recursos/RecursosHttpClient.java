package ar.utn.complejo.eventos.recursos;

import ar.utn.complejo.common.error.ApiError;
import ar.utn.complejo.eventos.recursos.RecursosDtos.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;

/**
 * Cliente HTTP hacia recursos-service con timeout, reintento acotado y circuit breaker (Resilience4j).
 * Reintento y circuito solo se activan ante fallas técnicas; las respuestas de negocio (4xx) pasan directo.
 */
@Component
public class RecursosHttpClient implements RecursosClient {

    private static final Logger log = LoggerFactory.getLogger(RecursosHttpClient.class);
    private static final String INSTANCIA = "recursos";

    private final RestClient http;
    private final ObjectMapper mapper;

    public RecursosHttpClient(@Qualifier("recursosRestClient") RestClient http, ObjectMapper mapper) {
        this.http = http;
        this.mapper = mapper;
    }

    @Override
    @Retry(name = INSTANCIA)
    @CircuitBreaker(name = INSTANCIA)
    public CotizacionResponse cotizar(CotizacionRequest request) {
        return ejecutar(() -> http.post().uri("/internal/reservas/cotizacion").body(request)
                .retrieve().onStatus(HttpStatusCode::isError, this::traducirError).body(CotizacionResponse.class));
    }

    @Override
    @Retry(name = INSTANCIA)
    @CircuitBreaker(name = INSTANCIA)
    public ReservaResponse reservar(ReservaRequest request) {
        return ejecutar(() -> http.post().uri("/internal/reservas").body(request)
                .retrieve().onStatus(HttpStatusCode::isError, this::traducirError).body(ReservaResponse.class));
    }

    @Override
    @Retry(name = INSTANCIA)
    @CircuitBreaker(name = INSTANCIA)
    public void liberar(Long eventoId) {
        ejecutar(() -> http.delete().uri("/internal/reservas/{id}", eventoId)
                .retrieve().onStatus(HttpStatusCode::isError, this::traducirError).toBodilessEntity());
    }

    private <T> T ejecutar(java.util.function.Supplier<T> llamada) {
        try {
            return llamada.get();
        } catch (ResourceAccessException e) {
            boolean timeout = hayCausa(e, SocketTimeoutException.class) || hayCausa(e, HttpTimeoutException.class);
            log.warn("recursos-service no respondió ({}): {}", timeout ? "timeout" : "conexión", e.getMessage());
            throw new RecursosNoDisponibleException(timeout ? "recursos-service no respondió a tiempo" : "No se pudo conectar con recursos-service", timeout, e);
        }
    }

    private void traducirError(org.springframework.http.HttpRequest req, org.springframework.http.client.ClientHttpResponse res) throws java.io.IOException {
        int status = res.getStatusCode().value();
        // 401/403 entre servicios indica un problema de configuración, no una regla de negocio.
        if (status >= 500 || status == 401 || status == 403) {
            throw new RecursosNoDisponibleException("recursos-service respondió HTTP " + status, false, null);
        }
        String codigo = "ERROR_RECURSOS";
        String mensaje = "recursos-service rechazó la operación (HTTP " + status + ")";
        try {
            ApiError error = mapper.readValue(res.getBody(), ApiError.class);
            if (error.codigo() != null) codigo = error.codigo();
            if (error.mensaje() != null) mensaje = error.mensaje();
        } catch (Exception ignorada) {
            // cuerpo no estándar: se usa el mensaje genérico
        }
        throw new RecursosRechazoException(status, codigo, mensaje);
    }

    private static boolean hayCausa(Throwable t, Class<? extends Throwable> tipo) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (tipo.isInstance(c)) return true;
        }
        return false;
    }
}
