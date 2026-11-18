package ar.utn.complejo.recursos.repository;

import ar.utn.complejo.recursos.domain.EstadoMantenimiento;
import ar.utn.complejo.recursos.domain.Mantenimiento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface MantenimientoRepository extends JpaRepository<Mantenimiento, Long> {

    @Query("""
            select count(m) > 0 from Mantenimiento m
            where m.instalacionId = :instalacionId
              and m.estado = ar.utn.complejo.recursos.domain.EstadoMantenimiento.ACTIVO
              and m.desde < :fin and m.hasta > :inicio""")
    boolean existeActivoEnPeriodo(@Param("instalacionId") Long instalacionId,
                                  @Param("inicio") LocalDateTime inicio,
                                  @Param("fin") LocalDateTime fin);

    @Query("""
            select m from Mantenimiento m
            where (:instalacionId is null or m.instalacionId = :instalacionId)
              and (:estado is null or m.estado = :estado)
            order by m.desde desc""")
    List<Mantenimiento> buscar(@Param("instalacionId") Long instalacionId, @Param("estado") EstadoMantenimiento estado);
}
