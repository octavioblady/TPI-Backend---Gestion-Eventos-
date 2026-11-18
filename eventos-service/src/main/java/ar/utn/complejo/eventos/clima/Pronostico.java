package ar.utn.complejo.eventos.clima;

/** Resumen del pronóstico para la franja del evento. */
public record Pronostico(int probabilidadLluviaMaxima, double rafagaVientoMaximaKmh, boolean granizo) {
}
