package ar.utn.complejo.recursos.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

public final class InstalacionDtos {

    private InstalacionDtos() {
    }

    public record HorarioDto(
            @NotNull DayOfWeek dia,
            @NotNull LocalTime apertura,
            @NotNull LocalTime cierre) {
    }

    public record EquipamientoDto(
            @NotBlank @Size(max = 100) String nombre,
            @Min(1) int cantidad) {
    }

    public record InstalacionRequest(
            @NotBlank @Size(max = 100) String nombre,
            @Size(max = 500) String descripcion,
            @NotNull @Positive Integer capacidad,
            boolean aireLibre,
            /** Si no se informa se usa la tarifa por defecto del complejo ($10.000 por hora). */
            @Positive BigDecimal tarifaHora,
            @NotEmpty List<@Valid HorarioDto> horarios,
            List<@Valid EquipamientoDto> equipamiento) {
    }

    public record InstalacionResponse(
            Long id,
            String nombre,
            String descripcion,
            int capacidad,
            boolean aireLibre,
            boolean activa,
            BigDecimal tarifaHora,
            List<HorarioDto> horarios,
            List<EquipamientoDto> equipamiento) {
    }
}
