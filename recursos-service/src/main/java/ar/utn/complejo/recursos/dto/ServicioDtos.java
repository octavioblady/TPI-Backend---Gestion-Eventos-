package ar.utn.complejo.recursos.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class ServicioDtos {

    private ServicioDtos() {
    }

    public record ServicioRequest(
            @NotBlank @Size(max = 100) String nombre,
            @Size(max = 500) String descripcion,
            @NotNull @Positive BigDecimal precioUnitario,
            @NotNull @PositiveOrZero Integer stockTotal) {
    }

    public record ServicioResponse(
            Long id,
            String nombre,
            String descripcion,
            BigDecimal precioUnitario,
            int stockTotal,
            boolean activo) {
    }

    public record StockResponse(Long servicioId, LocalDate fecha, int stockTotal, int reservado, int disponible) {
    }
}
