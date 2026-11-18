package ar.utn.complejo.eventos.recursos;

/** Falla técnica al hablar con recursos-service: timeout, conexión rechazada, 5xx o circuito abierto. */
public class RecursosNoDisponibleException extends RuntimeException {

    private final boolean timeout;

    public RecursosNoDisponibleException(String mensaje, boolean timeout, Throwable causa) {
        super(mensaje, causa);
        this.timeout = timeout;
    }

    public boolean esTimeout() {
        return timeout;
    }
}
