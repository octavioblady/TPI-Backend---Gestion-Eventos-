package ar.utn.complejo.common.seguridad;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Convierte los roles de realm de Keycloak (claim {@code realm_access.roles}) en autoridades
 * {@code ROLE_<ROL>} de Spring Security.
 */
public class KeycloakRealmRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final String PREFIJO = "ROLE_";

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Object realmAccess = jwt.getClaim("realm_access");
        if (!(realmAccess instanceof Map<?, ?> mapa)) {
            return List.of();
        }
        Object roles = mapa.get("roles");
        if (!(roles instanceof Collection<?> lista)) {
            return List.of();
        }
        return lista.stream()
                .filter(String.class::isInstance)
                .map(rol -> (GrantedAuthority) new SimpleGrantedAuthority(PREFIJO + ((String) rol).toUpperCase()))
                .toList();
    }
}
