package ar.utn.complejo.recursos.repository;

import ar.utn.complejo.recursos.domain.EstadoReserva;
import ar.utn.complejo.recursos.domain.ReservaEspacio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ReservaEspacioRepository extends JpaRepository<ReservaEspacio, Long> {

    List<ReservaEspacio> findByEventoIdAndEstado(Long eventoId, EstadoReserva estado);

    @Query("""
            select count(r) > 0 from ReservaEspacio r
            where r.instalacionId = :instalacionId
              and r.estado = ar.utn.complejo.recursos.domain.EstadoReserva.ACTIVA
              and r.inicio < :fin and r.fin > :inicio""")
    boolean existeActivaEnPeriodo(@Param("instalacionId") Long instalacionId,
                                  @Param("inicio") LocalDateTime inicio,
                                  @Param("fin") LocalDateTime fin);

    @Query("""
            select count(r) > 0 from ReservaEspacio r
            where r.instalacionId = :instalacionId
              and r.estado = ar.utn.complejo.recursos.domain.EstadoReserva.ACTIVA
              and r.fin > :desde""")
    boolean existeActivaPosteriorA(@Param("instalacionId") Long instalacionId, @Param("desde") LocalDateTime desde);
}
