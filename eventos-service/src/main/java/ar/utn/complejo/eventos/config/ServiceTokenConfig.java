package ar.utn.complejo.eventos.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

import java.io.IOException;

/**
 * Token de la cuenta técnica SERVICIO (client credentials contra Keycloak) para llamar a /internal/** de Recursos.
 * Spring Security cachea el token y lo renueva antes de que venza.
 */
@Configuration
@ConditionalOnProperty(name = "recursos.auth.enabled", havingValue = "true", matchIfMissing = true)
public class ServiceTokenConfig {

    private static final String REGISTRO = "recursos";

    @Bean
    public OAuth2AuthorizedClientManager serviceAuthorizedClientManager(ClientRegistrationRepository registros) {
        return new AuthorizedClientServiceOAuth2AuthorizedClientManager(registros, new InMemoryOAuth2AuthorizedClientService(registros));
    }

    @Bean
    public ClientHttpRequestInterceptor tokenServicioInterceptor(OAuth2AuthorizedClientManager manager) {
        return (request, body, ejecucion) -> {
            OAuth2AuthorizedClient cliente;
            try {
                cliente = manager.authorize(OAuth2AuthorizeRequest.withClientRegistrationId(REGISTRO).principal("eventos-service").build());
            } catch (RuntimeException e) {
                // Keycloak caído o credenciales inválidas: se trata como falla técnica de la llamada (IOException -> ResourceAccessException).
                throw new IOException("No se pudo obtener el token de la cuenta de servicio: " + e.getMessage(), e);
            }
            if (cliente == null) {
                throw new IOException("No se pudo obtener el token de la cuenta de servicio");
            }
            request.getHeaders().setBearerAuth(cliente.getAccessToken().getTokenValue());
            request.getHeaders().remove(HttpHeaders.COOKIE);
            return ejecucion.execute(request, body);
        };
    }
}
