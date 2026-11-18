package ar.utn.complejo.common.error;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Estructura estándar de error de todas las APIs del sistema.
 *
 * @param timestamp momento del error
 * @param status    código HTTP
 * @param error     texto estándar del código HTTP
 * @param codigo    código funcional estable (ej. CONFLICTO_DISPONIBILIDAD) pensado para que otros servicios decidan
 * @param mensaje   explicación legible
 * @param path      ruta solicitada
 * @param detalles  detalles adicionales (por ejemplo, errores de validación por campo)
 */
public record ApiError(
        OffsetDateTime timestamp,
        int status,
        String error,
        String codigo,
        String mensaje,
        String path,
        List<String> detalles) {
}
