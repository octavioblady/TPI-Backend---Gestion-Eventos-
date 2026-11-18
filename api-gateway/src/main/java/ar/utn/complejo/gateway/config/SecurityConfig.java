package ar.utn.complejo.gateway.config;

import ar.utn.complejo.common.error.ApiError;
import ar.utn.complejo.common.seguridad.KeycloakRealmRoleConverter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Security Filter del Gateway: es la primera capa de seguridad (autorización gruesa por rol, método y ruta).
 * 401 si el token falta, está vencido o es inválido; 403 si el rol no alcanza. Los microservicios vuelven a validar el JWT.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private static final String ADMIN = "ADMIN";
    private static final String CLIENTE = "CLIENTE";

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http, ObjectMapper mapper,
                                                         @Value("${app.docs.public:false}") boolean docsPublic) {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleConverter());

        http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(ex -> {
                    ex.pathMatchers("/actuator/health/**").permitAll();
                    if (docsPublic) {
                        ex.pathMatchers("/docs/**", "/swagger-ui.html", "/swagger-ui/**", "/webjars/**", "/v3/api-docs/**").permitAll();
                    }
                    // /internal/** no tiene ruta: se deja pasar la seguridad para que responda 404 sin importar el token.
                    ex.pathMatchers("/internal/**").permitAll();

                    ex.pathMatchers(HttpMethod.GET, "/api/v1/instalaciones/**", "/api/v1/servicios/**").hasAnyRole(ADMIN, CLIENTE);
                    ex.pathMatchers("/api/v1/instalaciones/**", "/api/v1/servicios/**").hasRole(ADMIN);
                    ex.pathMatchers("/api/v1/mantenimientos/**").hasRole(ADMIN);
                    ex.pathMatchers("/api/v1/disponibilidad").hasAnyRole(ADMIN, CLIENTE);
                    ex.pathMatchers(HttpMethod.PATCH, "/api/v1/eventos/*/senia", "/api/v1/eventos/*/inicio",
                            "/api/v1/eventos/*/finalizacion").hasRole(ADMIN);
                    ex.pathMatchers("/api/v1/eventos/**").hasAnyRole(ADMIN, CLIENTE);
                    ex.anyExchange().authenticated();
                })
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(new ReactiveJwtAuthenticationConverterAdapter(converter)))
                        .authenticationEntryPoint(entryPoint(mapper)))
                .exceptionHandling(eh -> eh.authenticationEntryPoint(entryPoint(mapper)).accessDeniedHandler(accessDenied(mapper)));
        return http.build();
    }

    private static ServerAuthenticationEntryPoint entryPoint(ObjectMapper mapper) {
        return (exchange, e) -> escribir(mapper, exchange, HttpStatus.UNAUTHORIZED, "NO_AUTENTICADO",
                "Falta el token o es inválido o está vencido");
    }

    private static ServerAccessDeniedHandler accessDenied(ObjectMapper mapper) {
        return (exchange, e) -> escribir(mapper, exchange, HttpStatus.FORBIDDEN, "SIN_PERMISOS",
                "Tu rol no alcanza para esta operación");
    }

    private static Mono<Void> escribir(ObjectMapper mapper, ServerWebExchange exchange, HttpStatus status, String codigo, String mensaje) {
        ApiError body = new ApiError(OffsetDateTime.now(), status.value(), status.getReasonPhrase(), codigo, mensaje,
                exchange.getRequest().getPath().value(), List.of());
        var response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            DataBuffer buffer = response.bufferFactory().wrap(mapper.writeValueAsBytes(body));
            return response.writeWith(Mono.just(buffer));
        } catch (Exception ex) {
            return response.setComplete();
        }
    }
}
