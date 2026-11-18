package ar.utn.complejo.recursos.web;

import ar.utn.complejo.recursos.domain.EstadoMantenimiento;
import ar.utn.complejo.recursos.dto.MantenimientoDtos.*;
import ar.utn.complejo.recursos.service.MantenimientoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/mantenimientos")
@Tag(name = "Mantenimientos")
public class MantenimientoController {

    private final MantenimientoService service;

    public MantenimientoController(MantenimientoService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Bloquear una instalación por mantenimiento (ADMIN)")
    public MantenimientoResponse registrar(@Valid @RequestBody MantenimientoRequest request) {
        return service.registrar(request);
    }

    @PatchMapping("/{id}/finalizacion")
    @Operation(summary = "Finalizar un bloqueo por mantenimiento (ADMIN)")
    public MantenimientoResponse finalizar(@PathVariable Long id) {
        return service.finalizar(id);
    }

    @GetMapping
    @Operation(summary = "Listar mantenimientos (ADMIN)")
    public List<MantenimientoResponse> listar(@RequestParam(required = false) Long instalacionId,
                                              @RequestParam(required = false) EstadoMantenimiento estado) {
        return service.listar(instalacionId, estado);
    }
}
