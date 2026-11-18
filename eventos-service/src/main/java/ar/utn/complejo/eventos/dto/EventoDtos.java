package ar.utn.complejo.eventos.dto;

import ar.utn.complejo.eventos.domain.EstadoEvento;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class EventoDtos {

    private EventoDtos() {
    }

    public record ServicioPedido(@NotNull Long servicioId, @NotNull @Min(1) Integer cantidad) {
    }

    /**
     * Solicitud de evento. {@code clienteId} solo lo puede informar un ADMIN (para cargar un evento en nombre de un cliente);
     * si no se informa, el dueño es quien hace la solicitud.
     */
    public record CrearEventoRequest(
            String clienteId,
            @NotEmpty List<@NotNull Long> instalacionIds,
            @NotNull LocalDateTime inicio,
            @NotNull LocalDateTime fin,
            @NotNull @Min(1) Integer asistentes,
            List<@Valid ServicioPedido> servicios) {
    }

    /** Registro del pago de la seña (manual, sin cobro electrónico). {@code abonada} por defecto es true. */
    public record SeniaRequest(Boolean abonada) {
    }

    public record InstalacionEvento(Long instalacionId, String nombre, boolean aireLibre, BigDecimal tarifaHora,
                                    long horasFacturadas, BigDecimal subtotal) {
    }

    public record ServicioEvento(Long servicioId, String nombre, int cantidad, BigDecimal precioUnitario, BigDecimal subtotal) {
    }

    public record HistorialItem(EstadoEvento estadoAnterior, EstadoEvento estadoNuevo, String motivo, String usuario, LocalDateTime fecha) {
    }

    public record AdvertenciaItem(LocalDateTime consultadaEn, int probabilidadLluvia, double rafagaVientoKmh, boolean granizo, String detalle) {
    }

    public record EventoResponse(
            Long id,
            String clienteId,
            EstadoEvento estado,
            LocalDateTime inicio,
            LocalDateTime fin,
            int asistentes,
            List<InstalacionEvento> instalaciones,
            List<ServicioEvento> servicios,
            BigDecimal precioTotal,
            BigDecimal seniaMonto,
            boolean seniaAbonada,
            Boolean seniaReembolsable,
            String codigoRechazo,
            String motivoRechazo,
            List<AdvertenciaItem> advertencias,
            List<HistorialItem> historial) {
    }

    public record PronosticoResponse(String estado, String mensaje, AdvertenciaItem advertencia) {
    }
}
