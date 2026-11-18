package ar.utn.complejo.eventos.repository;

import ar.utn.complejo.eventos.domain.Evento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventoRepository extends JpaRepository<Evento, Long> {

    List<Evento> findByClienteIdOrderByInicioDesc(String clienteId);

    List<Evento> findAllByOrderByInicioDesc();
}
