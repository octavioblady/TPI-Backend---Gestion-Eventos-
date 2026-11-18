package ar.utn.complejo.eventos.clima;

import ar.utn.complejo.common.tiempo.Intervalo;
import ar.utn.complejo.common.tiempo.ReglasTiempo;
import ar.utn.complejo.eventos.domain.AdvertenciaMeteorologica;
import ar.utn.complejo.eventos.domain.Evento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Regla 6: ante un pronóstico adverso en un evento al aire libre, el sistema advierte al cliente y es él quien decide.
 * La integración es de mejor esfuerzo: si el servicio externo falla, el evento sigue su curso sin advertencia y se registra el error.
 */
@Service
public class ClimaService {

    private static final Logger log = LoggerFactory.getLogger(ClimaService.class);

    public enum Estado {
        /** El evento no usa instalaciones al aire libre. */
        NO_APLICA,
        /** Faltan más días que el horizonte del pronóstico: no se consulta. */
        FUERA_DE_HORIZONTE,
        SIN_ADVERTENCIA,
        ADVERTENCIA,
        /** El servicio externo falló o respondió algo inesperado. */
        NO_DISPONIBLE
    }

    public record Resultado(Estado estado, Optional<AdvertenciaMeteorologica> advertencia, String mensaje) {
    }

    private final PronosticoProvider provider;
    private final ClimaProperties props;
    private final Clock reloj;

    public ClimaService(PronosticoProvider provider, ClimaProperties props, Clock reloj) {
        this.provider = provider;
        this.props = props;
        this.reloj = reloj;
    }

    /** Evalúa el evento. No modifica la entidad: quien llama decide si persiste la advertencia. */
    public Resultado evaluar(Evento evento) {
        if (!evento.usaAireLibre()) {
            return new Resultado(Estado.NO_APLICA, Optional.empty(), "El evento no usa instalaciones al aire libre");
        }
        LocalDate hoy = ReglasTiempo.ahora(reloj).toLocalDate();
        if (evento.getInicio().toLocalDate().isAfter(hoy.plusDays(props.horizonteDias()))) {
            return new Resultado(Estado.FUERA_DE_HORIZONTE, Optional.empty(),
                    "El pronóstico solo está disponible hasta " + props.horizonteDias() + " días antes; se puede volver a consultar más cerca de la fecha");
        }
        Pronostico p;
        try {
            p = provider.pronosticoPara(new Intervalo(evento.getInicio(), evento.getFin()));
        } catch (ClimaNoDisponibleException e) {
            log.warn("No se pudo consultar el pronóstico del evento {}: {}", evento.getId(), e.getMessage());
            return new Resultado(Estado.NO_DISPONIBLE, Optional.empty(), "No se pudo consultar el pronóstico; el evento sigue sin advertencia");
        }
        List<String> motivos = new ArrayList<>();
        if (p.probabilidadLluviaMaxima() > props.umbralLluviaPorcentaje()) {
            motivos.add("probabilidad de lluvia " + p.probabilidadLluviaMaxima() + "% (umbral " + props.umbralLluviaPorcentaje() + "%)");
        }
        if (p.granizo()) {
            motivos.add("probabilidad de granizo");
        }
        if (p.rafagaVientoMaximaKmh() >= props.umbralVientoKmh()) {
            motivos.add("vientos fuertes con ráfagas de " + Math.round(p.rafagaVientoMaximaKmh()) + " km/h");
        }
        if (motivos.isEmpty()) {
            return new Resultado(Estado.SIN_ADVERTENCIA, Optional.empty(), "Pronóstico favorable");
        }
        AdvertenciaMeteorologica a = new AdvertenciaMeteorologica();
        a.setConsultadaEn(ReglasTiempo.ahora(reloj));
        a.setProbabilidadLluvia(p.probabilidadLluviaMaxima());
        a.setRafagaVientoKmh(p.rafagaVientoMaximaKmh());
        a.setGranizo(p.granizo());
        a.setDetalle("Pronóstico adverso para el evento al aire libre: " + String.join(", ", motivos)
                + ". Podés mantener el evento, cancelarlo o solicitar uno nuevo en otra fecha o en un espacio cubierto.");
        return new Resultado(Estado.ADVERTENCIA, Optional.of(a), a.getDetalle());
    }
}
