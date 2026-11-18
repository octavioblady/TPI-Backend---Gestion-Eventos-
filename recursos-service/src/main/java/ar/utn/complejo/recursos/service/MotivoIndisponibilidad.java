package ar.utn.complejo.recursos.service;

import ar.utn.complejo.common.error.BusinessException;

/** Motivos por los que un espacio o servicio no puede reservarse. El código es estable y lo consume eventos-service. */
public enum MotivoIndisponibilidad {
    ANTICIPACION_INSUFICIENTE("La reserva debe solicitarse con al menos 48 h de anticipación"),
    FUERA_DE_HORARIO("El horario solicitado está fuera del horario de funcionamiento de la instalación"),
    MANTENIMIENTO("Mantenimiento"),
    CAPACIDAD_EXCEDIDA("La cantidad de asistentes supera la capacidad de la instalación"),
    CONFLICTO_DISPONIBILIDAD("La instalación ya está reservada en ese horario"),
    INSTALACION_INACTIVA("La instalación está dada de baja"),
    STOCK_INSUFICIENTE("No hay stock suficiente del servicio en la fecha solicitada"),
    SERVICIO_INACTIVO("El servicio no está disponible");

    private final String mensaje;

    MotivoIndisponibilidad(String mensaje) {
        this.mensaje = mensaje;
    }

    public String mensaje() {
        return mensaje;
    }

    public BusinessException comoExcepcion(String detalle) {
        return BusinessException.conflicto(name(), detalle == null ? mensaje : mensaje + ": " + detalle);
    }
}
