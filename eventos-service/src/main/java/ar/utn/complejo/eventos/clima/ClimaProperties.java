package ar.utn.complejo.eventos.clima;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuración de la integración meteorológica. Ubicación: Córdoba capital.
 * Pronóstico adverso: lluvia por encima del umbral, granizo o ráfagas fuertes.
 */
@ConfigurationProperties(prefix = "clima")
public record ClimaProperties(
        String url,
        double latitud,
        double longitud,
        int umbralLluviaPorcentaje,
        double umbralVientoKmh,
        int horizonteDias,
        Duration timeout) {
}
