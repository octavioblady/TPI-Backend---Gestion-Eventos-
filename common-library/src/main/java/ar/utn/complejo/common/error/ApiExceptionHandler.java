package ar.utn.complejo.common.error;

import jakarta.validation.ConstraintViolationException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Traduce todas las excepciones a {@link ApiError}, de modo que ambos microservicios respondan igual.
 * Los errores de seguridad (401/403) los resuelve Spring Security y no pasan por acá.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Object> handleBusiness(BusinessException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatus());
        return build(status, ex.getCodigo(), ex.getMessage(), request.getRequestURI(), List.of());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> handleConstraint(ConstraintViolationException ex, HttpServletRequest request) {
        List<String> detalles = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage()).sorted().toList();
        return build(HttpStatus.BAD_REQUEST, "VALIDACION", "Datos inválidos", request.getRequestURI(), detalles);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Object> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "PARAMETRO_INVALIDO",
                "El parámetro '" + ex.getName() + "' tiene un formato inválido", request.getRequestURI(), List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex, HttpServletRequest request) throws Exception {
        // Las excepciones de Spring Security deben llegar a sus filtros para responder 401/403.
        if (ex.getClass().getName().startsWith("org.springframework.security")) {
            throw ex;
        }
        log.error("Error inesperado en {}", request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "ERROR_INTERNO",
                "Ocurrió un error inesperado", request.getRequestURI(), List.of());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        List<String> detalles = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage()).sorted().toList();
        return build(HttpStatus.BAD_REQUEST, "VALIDACION", "Datos inválidos", path(request), detalles);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        return build(HttpStatus.BAD_REQUEST, "CUERPO_INVALIDO",
                "El cuerpo de la solicitud es inválido o está mal formado", path(request), List.of());
    }

    /** Resto de excepciones estándar de Spring MVC (404, 405, 415, parámetros faltantes, etc.). */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        HttpStatus status = HttpStatus.valueOf(statusCode.value());
        return build(status, status.name(), mensajeSeguro(ex, status), path(request), List.of());
    }

    private static String mensajeSeguro(Exception ex, HttpStatus status) {
        return status.is4xxClientError() && ex.getMessage() != null ? ex.getMessage() : status.getReasonPhrase();
    }

    private static String path(WebRequest request) {
        return request instanceof ServletWebRequest swr ? swr.getRequest().getRequestURI() : "";
    }

    private static ResponseEntity<Object> build(HttpStatus status, String codigo, String mensaje,
                                                String path, List<String> detalles) {
        ApiError body = new ApiError(OffsetDateTime.now(), status.value(), status.getReasonPhrase(),
                codigo, mensaje, path, detalles);
        return ResponseEntity.status(status).body(body);
    }
}
