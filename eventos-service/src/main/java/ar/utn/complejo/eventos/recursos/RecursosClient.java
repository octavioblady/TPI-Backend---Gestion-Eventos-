package ar.utn.complejo.eventos.recursos;

import ar.utn.complejo.eventos.recursos.RecursosDtos.*;

/** Puerto hacia recursos-service. Todas las operaciones son idempotentes, por lo que se pueden reintentar. */
public interface RecursosClient {

    CotizacionResponse cotizar(CotizacionRequest request);

    ReservaResponse reservar(ReservaRequest request);

    void liberar(Long eventoId);
}
