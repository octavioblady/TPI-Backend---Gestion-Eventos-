package ar.utn.complejo.eventos.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Identidad tomada siempre del JWT validado; nunca de encabezados enviados por el cliente. */
public record Usuario(String id, boolean admin) {

    public static Usuario de(Authentication auth) {
        if (!(auth instanceof JwtAuthenticationToken jwt)) {
            throw new IllegalStateException("Se esperaba una autenticación JWT");
        }
        boolean admin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        return new Usuario(jwt.getToken().getSubject(), admin);
    }
}
