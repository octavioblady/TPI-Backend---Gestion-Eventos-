package ar.utn.complejo.eventos.clima;

import ar.utn.complejo.common.tiempo.Intervalo;

/** Puerto hacia el servicio meteorológico externo. */
public interface PronosticoProvider {

    /** @throws ClimaNoDisponibleException si el servicio falla, demora o devuelve una respuesta inesperada */
    Pronostico pronosticoPara(Intervalo franja);
}
