package ar.utn.complejo.recursos.web;

import ar.utn.complejo.recursos.dto.ReservaDtos.CotizacionRequest;
import ar.utn.complejo.recursos.dto.ReservaDtos.CotizacionResponse;
import ar.utn.complejo.recursos.dto.ReservaDtos.ReservaRequest;
import ar.utn.complejo.recursos.dto.ReservaDtos.ReservaResponse;
import ar.utn.complejo.recursos.service.ReservaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** Endpoints internos: solo para eventos-service (rol SERVICIO). El Gateway no tiene ruta hacia /internal/**. */
@RestController
@RequestMapping("/internal/reservas")
@Tag(name = "Reservas internas (rol SERVICIO)")
public class ReservaInternaController {

    private final ReservaService service;

    public ReservaInternaController(ReservaService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Reservar de forma atómica instalaciones y servicios para un evento (idempotente por eventoId)")
    public ReservaResponse reservar(@Valid @RequestBody ReservaRequest request) {
        return service.reservar(request);
    }

    @PostMapping("/cotizacion")
    @Operation(summary = "Obtener tarifas y precios del catálogo para cotizar un evento (no reserva nada)")
    public CotizacionResponse cotizar(@Valid @RequestBody CotizacionRequest request) {
        return service.cotizar(request);
    }

    @DeleteMapping("/{eventoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Liberar espacios y stock de un evento (idempotente)")
    public void liberar(@PathVariable Long eventoId) {
        service.liberar(eventoId);
    }
}
