package ar.utn.complejo.recursos.web;

import ar.utn.complejo.recursos.dto.ServicioDtos.*;
import ar.utn.complejo.recursos.service.ServicioComplementarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/servicios")
@Tag(name = "Servicios complementarios")
public class ServicioController {

    private final ServicioComplementarioService service;

    public ServicioController(ServicioComplementarioService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Listar servicios complementarios activos")
    public List<ServicioResponse> listar() {
        return service.listar();
    }

    @GetMapping("/{id}/stock")
    @Operation(summary = "Stock disponible de un servicio en una fecha")
    public StockResponse stock(@PathVariable Long id,
                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return service.stock(id, fecha);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar servicio complementario (ADMIN)")
    public ServicioResponse registrar(@Valid @RequestBody ServicioRequest request) {
        return service.registrar(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modificar precio, descripción o stock (ADMIN)")
    public ServicioResponse modificar(@PathVariable Long id, @Valid @RequestBody ServicioRequest request) {
        return service.modificar(id, request);
    }
}
