package ar.utn.complejo.eventos.clima;

import ar.utn.complejo.common.tiempo.Intervalo;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Cliente de Open-Meteo (API pública, sin API key). Pide el pronóstico horario de lluvia, ráfagas de viento y
 * código de clima para la fecha del evento en la ubicación del complejo y lo resume a la franja del evento.
 */
@Component
public class OpenMeteoClient implements PronosticoProvider {

    /** Códigos WMO 96 y 99: tormenta con granizo. */
    private static final List<Integer> CODIGOS_GRANIZO = List.of(96, 99);

    private final RestClient http;
    private final double latitud;
    private final double longitud;

    public OpenMeteoClient(@Qualifier("climaRestClient") RestClient http, ClimaProperties props) {
        this.http = http;
        this.latitud = props.latitud();
        this.longitud = props.longitud();
    }

    @Override
    public Pronostico pronosticoPara(Intervalo franja) {
        String fecha = franja.inicio().toLocalDate().toString();
        Respuesta respuesta;
        try {
            respuesta = http.get().uri(uri -> uri
                            .queryParam("latitude", latitud)
                            .queryParam("longitude", longitud)
                            .queryParam("hourly", "precipitation_probability,wind_gusts_10m,weather_code")
                            .queryParam("timezone", "America/Argentina/Cordoba")
                            .queryParam("wind_speed_unit", "kmh")
                            .queryParam("start_date", fecha)
                            .queryParam("end_date", fecha)
                            .build())
                    .retrieve().body(Respuesta.class);
        } catch (Exception e) {
            throw new ClimaNoDisponibleException("Falló la consulta a Open-Meteo: " + e.getMessage(), e);
        }
        return resumir(respuesta, franja);
    }

    static Pronostico resumir(Respuesta respuesta, Intervalo franja) {
        if (respuesta == null || respuesta.hourly() == null || respuesta.hourly().time() == null
                || respuesta.hourly().precipitationProbability() == null || respuesta.hourly().windGusts() == null
                || respuesta.hourly().weatherCode() == null) {
            throw new ClimaNoDisponibleException("Respuesta inesperada de Open-Meteo: faltan datos horarios", null);
        }
        Hourly h = respuesta.hourly();
        int lluvia = 0;
        double viento = 0;
        boolean granizo = false;
        int horasEvaluadas = 0;
        try {
            LocalDateTime desde = franja.inicio().withMinute(0).withSecond(0).withNano(0);
            for (int i = 0; i < h.time().size(); i++) {
                LocalDateTime t = LocalDateTime.parse(h.time().get(i));
                if (t.isBefore(desde) || !t.isBefore(franja.fin())) continue;
                horasEvaluadas++;
                Integer p = h.precipitationProbability().get(i);
                Double w = h.windGusts().get(i);
                Integer c = h.weatherCode().get(i);
                if (p != null) lluvia = Math.max(lluvia, p);
                if (w != null) viento = Math.max(viento, w);
                if (c != null && CODIGOS_GRANIZO.contains(c)) granizo = true;
            }
        } catch (RuntimeException e) {
            throw new ClimaNoDisponibleException("Respuesta inesperada de Open-Meteo: " + e.getMessage(), e);
        }
        if (horasEvaluadas == 0) {
            throw new ClimaNoDisponibleException("Open-Meteo no devolvió datos para la franja del evento", null);
        }
        return new Pronostico(lluvia, viento, granizo);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Respuesta(Hourly hourly) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Hourly(List<String> time,
                  @JsonProperty("precipitation_probability") List<Integer> precipitationProbability,
                  @JsonProperty("wind_gusts_10m") List<Double> windGusts,
                  @JsonProperty("weather_code") List<Integer> weatherCode) {
    }
}
