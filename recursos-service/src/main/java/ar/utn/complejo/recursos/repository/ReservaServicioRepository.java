package ar.utn.complejo.recursos.repository;

import ar.utn.complejo.recursos.domain.EstadoReserva;
import ar.utn.complejo.recursos.domain.ReservaServicio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReservaServicioRepository extends JpaRepository<ReservaServicio, Long> {

    List<ReservaServicio> findByEventoIdAndEstado(Long eventoId, EstadoReserva estado);
}
