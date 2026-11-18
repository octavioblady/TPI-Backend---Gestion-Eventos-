package ar.utn.complejo.eventos.recursos;

/** recursos-service respondió que la operación no es posible (regla de negocio o recurso inexistente). No es una falla técnica. */
public class RecursosRechazoException extends RuntimeException {

    private final int status;
    private final String codigo;

    public RecursosRechazoException(int status, String codigo, String mensaje) {
        super(mensaje);
        this.status = status;
        this.codigo = codigo;
    }

    public int getStatus() {
        return status;
    }

    public String getCodigo() {
        return codigo;
    }
}
