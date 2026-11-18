package ar.utn.complejo.recursos.config;

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
        // Servidor relativo: el Swagger UI publicado por el Gateway prueba contra el propio Gateway, no contra el servicio interno.
        return new OpenAPI()
                .servers(java.util.List.of(new Server().url("/")))
                .info(new Info().title("recursos-service").version("1.0.0")
                        .description("Catálogo de instalaciones, mantenimientos, servicios complementarios, disponibilidad y reservas"))
                .components(new Components().addSecuritySchemes("bearer",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearer"));
    }
}
