package ar.utn.complejo.recursos.config;

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
 * Segunda capa de seguridad (defensa en profundidad): el servicio vuelve a validar el JWT aunque el Gateway ya lo haya hecho.
 * Autorización fina por rol y operación.
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
                    auth.requestMatchers("/internal/**").hasRole("SERVICIO");
                    auth.requestMatchers(HttpMethod.GET, "/api/v1/instalaciones/**", "/api/v1/servicios/**",
                            "/api/v1/disponibilidad").hasAnyRole("ADMIN", "CLIENTE");
                    auth.requestMatchers("/api/v1/**").hasRole("ADMIN");
                    auth.anyRequest().denyAll();
                })
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(converter)));
        return http.build();
    }
}
