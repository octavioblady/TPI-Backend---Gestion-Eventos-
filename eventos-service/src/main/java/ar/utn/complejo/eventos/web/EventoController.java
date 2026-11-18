package ar.utn.complejo.eventos.web;

import ar.utn.complejo.common.error.BusinessException;
import ar.utn.complejo.eventos.dto.EventoDtos.*;
import ar.utn.complejo.eventos.service.EventoService;
import ar.utn.complejo.eventos.service.Usuario;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/eventos")
@Tag(name = "Eventos")
public class EventoController {

    private final EventoService service;

    public EventoController(EventoService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Solicitar un evento (T-01): se cotiza y queda SOLICITADO a la espera de la seña. "
            + "Un ADMIN puede informar clienteId para cargarlo en nombre de un cliente.")
    public EventoResponse solicitar(@Valid @RequestBody CrearEventoRequest request, Authentication auth) {
        return service.solicitar(request, Usuario.de(auth));
    }

    @GetMapping
    @Operation(summary = "Listar eventos: el ADMIN ve todos, el CLIENTE solo los suyos")
    public List<EventoResponse> listar(Authentication auth) {
        return service.listar(Usuario.de(auth));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar el detalle de un evento (con precio, historial y advertencias)")
    public EventoResponse obtener(@PathVariable Long id, Authentication auth) {
        return service.obtener(id, Usuario.de(auth));
    }

    @PatchMapping("/{id}/senia")
    @Operation(summary = "Registrar la seña (ADMIN) y validar todas las condiciones: el evento pasa a CONFIRMADO o RECHAZADO con motivo. "
            + "Responde 503/504 si Recursos falla; en ese caso el evento queda RECHAZADO y el cliente puede volver a solicitarlo.")
    public EventoResponse registrarSenia(@PathVariable Long id, @RequestBody(required = false) SeniaRequest request, Authentication auth) {
        boolean abonada = request == null || request.abonada() == null || request.abonada();
        EventoService.Evaluacion resultado = service.evaluar(id, abonada, Usuario.de(auth));
        if (resultado.fallaTecnica() != null) {
            if (resultado.fallaTecnica() instanceof BusinessException be) {
                throw be;
            }
            throw resultado.fallaTecnica();
        }
        return resultado.evento();
    }

    @PatchMapping("/{id}/cancelacion")
    @Operation(summary = "Cancelar un evento CONFIRMADO y liberar espacios y stock. Con menos de 48 h la seña no es reembolsable.")
    public EventoResponse cancelar(@PathVariable Long id, Authentication auth) {
        return service.cancelar(id, Usuario.de(auth));
    }

    @PatchMapping("/{id}/inicio")
    @Operation(summary = "Marcar el evento como EN_CURSO (ADMIN)")
    public EventoResponse iniciar(@PathVariable Long id, Authentication auth) {
        return service.iniciar(id, Usuario.de(auth));
    }

    @PatchMapping("/{id}/finalizacion")
    @Operation(summary = "Marcar el evento como FINALIZADO (ADMIN)")
    public EventoResponse finalizar(@PathVariable Long id, Authentication auth) {
        return service.finalizar(id, Usuario.de(auth));
    }

    @PostMapping("/{id}/pronostico")
    @Operation(summary = "Consultar el pronóstico de un evento al aire libre y registrar una advertencia si es adverso")
    public PronosticoResponse pronostico(@PathVariable Long id, Authentication auth) {
        return service.consultarPronostico(id, Usuario.de(auth));
    }
}
