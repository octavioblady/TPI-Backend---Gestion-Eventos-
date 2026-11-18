package ar.utn.complejo.eventos.clima;

public class ClimaNoDisponibleException extends RuntimeException {

    public ClimaNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
