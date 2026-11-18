package ar.utn.complejo.common.tiempo;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Intervalo semiabierto [inicio, fin): dos intervalos consecutivos (uno termina cuando el otro empieza)
 * NO se superponen.
 */
public record Intervalo(LocalDateTime inicio, LocalDateTime fin) {

    public Intervalo {
        if (inicio == null || fin == null) {
            throw new IllegalArgumentException("El intervalo requiere inicio y fin");
        }
        if (!inicio.isBefore(fin)) {
            throw new IllegalArgumentException("El inicio debe ser anterior al fin");
        }
    }

    public static Intervalo de(LocalDate fecha, LocalTime desde, LocalTime hasta) {
        return new Intervalo(fecha.atTime(desde), fecha.atTime(hasta));
    }

    public boolean solapaCon(Intervalo otro) {
        return inicio.isBefore(otro.fin) && otro.inicio.isBefore(fin);
    }

    public boolean solapaCon(LocalDateTime otroInicio, LocalDateTime otroFin) {
        return inicio.isBefore(otroFin) && otroInicio.isBefore(fin);
    }

    public boolean contiene(Intervalo otro) {
        return !otro.inicio.isBefore(inicio) && !otro.fin.isAfter(fin);
    }

    public boolean esMismoDia() {
        return inicio.toLocalDate().equals(fin.toLocalDate())
                || (fin.toLocalTime().equals(LocalTime.MIDNIGHT) && fin.toLocalDate().equals(inicio.toLocalDate().plusDays(1)));
    }

    /** Indica si el intervalo cae completo dentro de la franja horaria diaria [apertura, cierre]. */
    public boolean dentroDeFranja(LocalTime apertura, LocalTime cierre) {
        if (!esMismoDia()) {
            return false;
        }
        LocalTime desde = inicio.toLocalTime();
        LocalTime hasta = fin.toLocalTime();
        return !desde.isBefore(apertura) && !hasta.isAfter(cierre) && hasta.isAfter(desde);
    }

    public Duration duracion() {
        return Duration.between(inicio, fin);
    }

    /** Horas facturables: se redondea hacia arriba a la hora completa. */
    public long horasFacturables() {
        long minutos = duracion().toMinutes();
        return (minutos + 59) / 60;
    }
}
