package ar.utn.complejo.recursos.repository;

import ar.utn.complejo.recursos.domain.StockServicioFecha;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface StockServicioFechaRepository extends JpaRepository<StockServicioFecha, Long> {

    Optional<StockServicioFecha> findByServicioIdAndFecha(Long servicioId, LocalDate fecha);

    @Query("select coalesce(max(s.cantidadReservada), 0) from StockServicioFecha s where s.servicioId = :servicioId and s.fecha >= :desde")
    int maximaReservaDesde(@Param("servicioId") Long servicioId, @Param("desde") LocalDate desde);
}
