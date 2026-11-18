package ar.utn.complejo.recursos.web;

import ar.utn.complejo.recursos.dto.InstalacionDtos.*;
import ar.utn.complejo.recursos.service.InstalacionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/instalaciones")
@Tag(name = "Instalaciones")
public class InstalacionController {

    private final InstalacionService service;

    public InstalacionController(InstalacionService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Listar instalaciones (por defecto solo las activas)")
    public List<InstalacionResponse> listar(@RequestParam(defaultValue = "true") boolean soloActivas) {
        return service.listar(soloActivas);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar una instalación")
    public InstalacionResponse obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar instalación (ADMIN)")
    public InstalacionResponse registrar(@Valid @RequestBody InstalacionRequest request) {
        return service.registrar(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modificar instalación (ADMIN)")
    public InstalacionResponse modificar(@PathVariable Long id, @Valid @RequestBody InstalacionRequest request) {
        return service.modificar(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Dar de baja (lógica) una instalación (ADMIN)")
    public void darDeBaja(@PathVariable Long id) {
        service.darDeBaja(id);
    }
}
