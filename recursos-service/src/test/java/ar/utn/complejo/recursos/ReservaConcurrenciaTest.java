package ar.utn.complejo.recursos;

import ar.utn.complejo.common.error.BusinessException;
import ar.utn.complejo.recursos.domain.EstadoReserva;
import ar.utn.complejo.recursos.dto.ReservaDtos.ReservaRequest;
import ar.utn.complejo.recursos.dto.ReservaDtos.ServicioSolicitado;
import ar.utn.complejo.recursos.service.ReservaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Desafío 2: dos clientes piden la misma franja a la vez. Solo uno puede quedarse con ella. */
class ReservaConcurrenciaTest extends BaseIntegrationTest {

    @Autowired
    ReservaService reservaService;

    @Test
    void ochoSolicitudesSimultaneasDeLaMismaFranja_ganaSoloUna() throws Exception {
        int hilos = 8;
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch largada = new CountDownLatch(1);
        AtomicInteger exitos = new AtomicInteger();
        AtomicInteger conflictos = new AtomicInteger();
        List<Future<?>> futuros = new ArrayList<>();
        for (int n = 1; n <= hilos; n++) {
            long eventoId = 100 + n;
            futuros.add(pool.submit(() -> {
                try {
                    largada.await();
                    reservaService.reservar(new ReservaRequest(eventoId, List.of(sum.getId()), FECHA_OK.atTime(18, 0),
                            FECHA_OK.atTime(20, 0), 10, null));
                    exitos.incrementAndGet();
                } catch (BusinessException e) {
                    if ("CONFLICTO_DISPONIBILIDAD".equals(e.getCodigo())) conflictos.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return null;
            }));
        }
        largada.countDown();
        for (Future<?> f : futuros) f.get(30, TimeUnit.SECONDS);
        pool.shutdown();

        assertEquals(1, exitos.get());
        assertEquals(hilos - 1, conflictos.get());
        long activas = reservasEspacio.findAll().stream().filter(r -> r.getEstado() == EstadoReserva.ACTIVA).count();
        assertEquals(1, activas);
    }

    @Test
    void solicitudesSimultaneasNoSobrepasanElStock() throws Exception {
        int hilos = 6; // cada una pide 20 sillas sobre un stock de 50: solo 2 caben
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch largada = new CountDownLatch(1);
        AtomicInteger exitos = new AtomicInteger();
        List<Future<?>> futuros = new ArrayList<>();
        Long[] instalacionesIds = {cancha.getId(), sum.getId(), salon.getId()};
        for (int n = 0; n < hilos; n++) {
            long eventoId = 200 + n;
            // dos instalaciones distintas por pareja de hilos para que el stock sea lo único que compite
            long instalacionId = instalacionesIds[n % 3];
            int horaInicio = 8 + (n / 3) * 3;
            futuros.add(pool.submit(() -> {
                try {
                    largada.await();
                    reservaService.reservar(new ReservaRequest(eventoId, List.of(instalacionId), FECHA_OK.atTime(horaInicio, 0),
                            FECHA_OK.atTime(horaInicio + 2, 0), 10, List.of(new ServicioSolicitado(sillas.getId(), 20))));
                    exitos.incrementAndGet();
                } catch (BusinessException ignorada) {
                    // STOCK_INSUFICIENTE esperado
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return null;
            }));
        }
        largada.countDown();
        for (Future<?> f : futuros) f.get(30, TimeUnit.SECONDS);
        pool.shutdown();

        assertEquals(2, exitos.get());
        assertEquals(40, stock.findByServicioIdAndFecha(sillas.getId(), FECHA_OK).orElseThrow().getCantidadReservada());
    }
}
