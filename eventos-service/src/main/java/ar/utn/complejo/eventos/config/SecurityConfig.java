package ar.utn.complejo.eventos.config;

import ar.utn.complejo.common.seguridad.KeycloakRealmRoleConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Segunda capa de seguridad (defensa en profundidad): vuelve a validar el JWT y aplica la autorización fina por rol.
 * La regla "un CLIENTE solo ve sus eventos" se aplica en el servicio, comparando el 'sub' del token con cliente_id.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   @Value("${app.docs.public:false}") boolean docsPublic) throws Exception {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleConverter());

        http.csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/actuator/health/**").permitAll();
                    if (docsPublic) {
                        auth.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll();
                    }
                    auth.requestMatchers(HttpMethod.PATCH, "/api/v1/eventos/*/senia", "/api/v1/eventos/*/inicio",
                            "/api/v1/eventos/*/finalizacion").hasRole("ADMIN");
                    auth.requestMatchers("/api/v1/eventos/**").hasAnyRole("ADMIN", "CLIENTE");
                    auth.anyRequest().denyAll();
                })
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(converter)));
        return http.build();
    }
}
