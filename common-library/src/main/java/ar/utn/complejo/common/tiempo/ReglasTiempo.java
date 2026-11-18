package ar.utn.complejo.common.tiempo;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Reglas de tiempo compartidas. Todo el sistema trabaja con la zona horaria del complejo
 * (America/Argentina/Cordoba) y con un {@link Clock} inyectable para poder probar los bordes.
 */
public final class ReglasTiempo {

    public static final ZoneId ZONA = ZoneId.of("America/Argentina/Cordoba");
    public static final Duration ANTICIPACION_MINIMA = Duration.ofHours(48);

    private ReglasTiempo() {
    }

    public static Clock relojDelComplejo() {
        return Clock.system(ZONA);
    }

    public static LocalDateTime ahora(Clock reloj) {
        return LocalDateTime.now(reloj.withZone(ZONA));
    }

    /** Regla 1: se acepta si faltan 48 h o más (exactamente 48 h está permitido). */
    public static boolean cumpleAnticipacion(LocalDateTime inicioEvento, LocalDateTime ahora) {
        return !inicioEvento.isBefore(ahora.plus(ANTICIPACION_MINIMA));
    }

    /** Regla 5: la seña es reembolsable solo si se cancela con 48 h o más de anticipación. */
    public static boolean esCancelacionReembolsable(LocalDateTime inicioEvento, LocalDateTime ahora) {
        return cumpleAnticipacion(inicioEvento, ahora);
    }
}
