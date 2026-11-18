package ar.utn.complejo.eventos.domain;

import java.util.EnumSet;
import java.util.Set;

/** Estados T-01 a T-06 y transiciones permitidas. */
public enum EstadoEvento {
    SOLICITADO, CONFIRMADO, RECHAZADO, CANCELADO, EN_CURSO, FINALIZADO;

    public Set<EstadoEvento> destinosPermitidos() {
        return switch (this) {
            case SOLICITADO -> EnumSet.of(CONFIRMADO, RECHAZADO);
            case CONFIRMADO -> EnumSet.of(CANCELADO, EN_CURSO);
            case EN_CURSO -> EnumSet.of(FINALIZADO);
            case RECHAZADO, CANCELADO, FINALIZADO -> EnumSet.noneOf(EstadoEvento.class);
        };
    }

    public boolean puedePasarA(EstadoEvento destino) {
        return destinosPermitidos().contains(destino);
    }
}
