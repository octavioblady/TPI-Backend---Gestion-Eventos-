package ar.utn.complejo.eventos.config;

import ar.utn.complejo.common.error.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.List;

/** Dos operaciones simultáneas sobre el mismo evento: la segunda recibe 409 en lugar de pisar el estado. */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ConcurrenciaHandler {

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> conflicto(OptimisticLockingFailureException e, HttpServletRequest request) {
        ApiError body = new ApiError(OffsetDateTime.now(), 409, HttpStatus.CONFLICT.getReasonPhrase(), "CONCURRENCIA",
                "El evento fue modificado por otra operación; volvé a consultarlo e intentá de nuevo", request.getRequestURI(), List.of());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }
}
