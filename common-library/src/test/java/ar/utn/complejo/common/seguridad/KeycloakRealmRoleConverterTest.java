package ar.utn.complejo.common.seguridad;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeycloakRealmRoleConverterTest {

    private final KeycloakRealmRoleConverter converter = new KeycloakRealmRoleConverter();

    private Jwt jwt(Map<String, Object> claims) {
        return new Jwt("token", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "none"), claims);
    }

    @Test
    void convierteRolesDeRealmAAutoridades() {
        Jwt jwt = jwt(Map.of("sub", "u1", "realm_access", Map.of("roles", List.of("ADMIN", "cliente", "offline_access"))));
        List<String> autoridades = converter.convert(jwt).stream().map(GrantedAuthority::getAuthority).toList();
        assertEquals(List.of("ROLE_ADMIN", "ROLE_CLIENTE", "ROLE_OFFLINE_ACCESS"), autoridades);
    }

    @Test
    void sinClaimDeRolesDevuelveVacio() {
        assertTrue(converter.convert(jwt(Map.of("sub", "u1"))).isEmpty());
    }

    @Test
    void claimMalFormadoDevuelveVacio() {
        assertTrue(converter.convert(jwt(Map.of("sub", "u1", "realm_access", "x"))).isEmpty());
        assertTrue(converter.convert(jwt(Map.of("sub", "u1", "realm_access", Map.of("roles", "ADMIN")))).isEmpty());
    }
}
