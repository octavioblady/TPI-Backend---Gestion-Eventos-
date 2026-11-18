package ar.utn.complejo.recursos.dto;

public final class DisponibilidadDtos {

    private DisponibilidadDtos() {
    }

    /** Resultado por instalación: si no está disponible, {@code motivo} explica por qué. */
    public record DisponibilidadItem(
            Long instalacionId,
            String nombre,
            int capacidad,
            boolean disponible,
            String codigoMotivo,
            String motivo) {
    }
}
