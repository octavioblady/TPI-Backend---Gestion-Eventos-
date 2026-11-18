package ar.utn.complejo.eventos.config;

import ar.utn.complejo.common.tiempo.ReglasTiempo;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClockConfig {

    @Bean
    public Clock reloj() {
        return ReglasTiempo.relojDelComplejo();
    }
}
