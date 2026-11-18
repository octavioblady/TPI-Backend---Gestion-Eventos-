package ar.utn.complejo.eventos.recursos;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Contrato con recursos-service. Son copias propias: no se comparten clases de dominio entre servicios. */
public final class RecursosDtos {

    private RecursosDtos() {
    }

    public record ServicioSolicitado(Long servicioId, Integer cantidad) {
    }

    public record InstalacionInfo(Long instalacionId, String nombre, int capacidad, boolean aireLibre, BigDecimal tarifaHora) {
    }

    public record ServicioInfo(Long servicioId, String nombre, int cantidad, BigDecimal precioUnitario) {
    }

    public record CotizacionRequest(List<Long> instalacionIds, List<ServicioSolicitado> servicios) {
    }

    public record CotizacionResponse(List<InstalacionInfo> instalaciones, List<ServicioInfo> servicios) {
    }

    public record ReservaRequest(Long eventoId, List<Long> instalacionIds, LocalDateTime inicio, LocalDateTime fin,
                                 Integer asistentes, List<ServicioSolicitado> servicios) {
    }

    public record ReservaResponse(Long eventoId, LocalDateTime inicio, LocalDateTime fin,
                                  List<InstalacionInfo> instalaciones, List<ServicioInfo> servicios) {
    }
}
