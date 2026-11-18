package ar.utn.complejo.recursos.repository;

import ar.utn.complejo.recursos.domain.Instalacion;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface InstalacionRepository extends JpaRepository<Instalacion, Long> {

    List<Instalacion> findByActivaTrueOrderByNombre();

    List<Instalacion> findAllByOrderByNombre();

    boolean existsByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Long id);

    /** Bloquea las filas (en orden de id, para evitar deadlocks) y serializa reservas concurrentes sobre la misma instalación. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Instalacion i where i.id in :ids order by i.id")
    List<Instalacion> findAllByIdForUpdate(@Param("ids") Collection<Long> ids);
}
