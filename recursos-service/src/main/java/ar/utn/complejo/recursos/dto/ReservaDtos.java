package ar.utn.complejo.recursos.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class ReservaDtos {

    private ReservaDtos() {
    }

    public record ServicioSolicitado(@NotNull Long servicioId, @NotNull @Min(1) Integer cantidad) {
    }

    /** Pedido de reserva atómica: o se reservan todas las instalaciones y servicios, o ninguno. */
    public record ReservaRequest(
            @NotNull Long eventoId,
            @NotEmpty List<@NotNull Long> instalacionIds,
            @NotNull LocalDateTime inicio,
            @NotNull LocalDateTime fin,
            @NotNull @Min(1) Integer asistentes,
            List<@Valid ServicioSolicitado> servicios) {
    }

    public record InstalacionReservada(Long instalacionId, String nombre, int capacidad, boolean aireLibre, BigDecimal tarifaHora) {
    }

    public record ServicioReservado(Long servicioId, String nombre, int cantidad, BigDecimal precioUnitario) {
    }

    /** Pedido de cotización: datos del catálogo necesarios para calcular el precio, sin reservar nada. */
    public record CotizacionRequest(
            @NotEmpty List<@NotNull Long> instalacionIds,
            List<@Valid ServicioSolicitado> servicios) {
    }

    public record CotizacionResponse(List<InstalacionReservada> instalaciones, List<ServicioReservado> servicios) {
    }

    public record ReservaResponse(
            Long eventoId,
            LocalDateTime inicio,
            LocalDateTime fin,
            List<InstalacionReservada> instalaciones,
            List<ServicioReservado> servicios) {
    }
}
