package ar.utn.complejo.eventos.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        // Servidor relativo: el Swagger UI publicado por el Gateway prueba contra el propio Gateway.
        return new OpenAPI()
                .servers(java.util.List.of(new Server().url("/")))
                .info(new Info().title("eventos-service").version("1.0.0")
                        .description("Proceso central: solicitud, validación, precio, seña, confirmación y ciclo de vida de eventos"))
                .components(new Components().addSecuritySchemes("bearer",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearer"));
    }
}
