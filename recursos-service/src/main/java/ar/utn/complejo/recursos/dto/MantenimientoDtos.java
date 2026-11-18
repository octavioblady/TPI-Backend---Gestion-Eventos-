package ar.utn.complejo.recursos.dto;

import ar.utn.complejo.recursos.domain.EstadoMantenimiento;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class MantenimientoDtos {

    private MantenimientoDtos() {
    }

    public record MantenimientoRequest(
            @NotNull Long instalacionId,
            @NotNull LocalDateTime desde,
            @NotNull LocalDateTime hasta,
            @Size(max = 300) String motivo) {
    }

    public record MantenimientoResponse(
            Long id,
            Long instalacionId,
            LocalDateTime desde,
            LocalDateTime hasta,
            String motivo,
            EstadoMantenimiento estado) {
    }
}
