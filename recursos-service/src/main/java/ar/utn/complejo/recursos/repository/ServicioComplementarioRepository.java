package ar.utn.complejo.recursos.repository;

import ar.utn.complejo.recursos.domain.ServicioComplementario;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ServicioComplementarioRepository extends JpaRepository<ServicioComplementario, Long> {

    List<ServicioComplementario> findByActivoTrueOrderByNombre();

    boolean existsByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Long id);

    /** Bloquea los servicios para serializar descuentos de stock concurrentes. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ServicioComplementario s where s.id in :ids order by s.id")
    List<ServicioComplementario> findAllByIdForUpdate(@Param("ids") Collection<Long> ids);
}
