package ar.utn.complejo.recursos;

import ar.utn.complejo.recursos.domain.*;
import ar.utn.complejo.recursos.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.*;

/** Hora fija de los tests: martes 1/12/2026 10:00 (Córdoba). El mínimo para reservar es entonces jueves 3/12 a las 10:00. */
@SpringBootTest
@ActiveProfiles("test")
@Import(BaseIntegrationTest.RelojFijo.class)
public abstract class BaseIntegrationTest {

    protected static final LocalDateTime AHORA = LocalDateTime.of(2026, 12, 1, 10, 0);
    /** Jueves 10/12/2026: cumple de sobra las 48 h. */
    protected static final LocalDate FECHA_OK = LocalDate.of(2026, 12, 10);

    @TestConfiguration
    static class RelojFijo {
        @Bean
        @Primary
        Clock relojDeTest() {
            return Clock.fixed(AHORA.atZone(ZoneId.of("America/Argentina/Cordoba")).toInstant(), ZoneId.of("America/Argentina/Cordoba"));
        }
    }

    @Autowired protected InstalacionRepository instalaciones;
    @Autowired protected ServicioComplementarioRepository servicios;
    @Autowired protected ReservaEspacioRepository reservasEspacio;
    @Autowired protected ReservaServicioRepository reservasServicio;
    @Autowired protected StockServicioFechaRepository stock;
    @Autowired protected MantenimientoRepository mantenimientos;

    protected Instalacion cancha;   // 100 personas, 08-22
    protected Instalacion sum;      // 50 personas, 08-23
    protected Instalacion salon;    // 150 personas, 08-23, aire libre
    protected ServicioComplementario sillas; // stock 50

    @BeforeEach
    void datosBase() {
        reservasServicio.deleteAll();
        reservasEspacio.deleteAll();
        stock.deleteAll();
        mantenimientos.deleteAll();
        servicios.deleteAll();
        instalaciones.deleteAll();

        cancha = instalacion("Cancha multiuso", 100, false, LocalTime.of(22, 0));
        sum = instalacion("SUM mediano", 50, false, LocalTime.of(23, 0));
        salon = instalacion("Salón principal y patio", 150, true, LocalTime.of(23, 0));
        sillas = new ServicioComplementario();
        sillas.setNombre("Sillas");
        sillas.setPrecioUnitario(new BigDecimal("200.00"));
        sillas.setStockTotal(50);
        sillas = servicios.save(sillas);
    }

    protected Instalacion instalacion(String nombre, int capacidad, boolean aireLibre, LocalTime cierre) {
        Instalacion i = new Instalacion();
        i.setNombre(nombre);
        i.setCapacidad(capacidad);
        i.setAireLibre(aireLibre);
        i.setTarifaHora(new BigDecimal("10000.00"));
        java.util.List<HorarioInstalacion> horarios = new java.util.ArrayList<>();
        for (DayOfWeek d : DayOfWeek.values()) {
            HorarioInstalacion h = new HorarioInstalacion();
            h.setDia(d);
            h.setApertura(LocalTime.of(8, 0));
            h.setCierre(cierre);
            horarios.add(h);
        }
        i.reemplazarHorarios(horarios);
        return instalaciones.save(i);
    }
}
