package ar.utn.complejo.common.error;

/**
 * Error de negocio controlado. Lleva el código HTTP y un código funcional estable.
 * Evita depender de Spring para que pueda usarse en cualquier capa.
 */
public class BusinessException extends RuntimeException {

    private final int status;
    private final String codigo;

    public BusinessException(int status, String codigo, String mensaje) {
        super(mensaje);
        this.status = status;
        this.codigo = codigo;
    }

    public static BusinessException validacion(String codigo, String mensaje) {
        return new BusinessException(400, codigo, mensaje);
    }

    public static BusinessException noEncontrado(String codigo, String mensaje) {
        return new BusinessException(404, codigo, mensaje);
    }

    public static BusinessException conflicto(String codigo, String mensaje) {
        return new BusinessException(409, codigo, mensaje);
    }

    public static BusinessException noDisponible(String codigo, String mensaje) {
        return new BusinessException(503, codigo, mensaje);
    }

    public int getStatus() {
        return status;
    }

    public String getCodigo() {
        return codigo;
    }
}
