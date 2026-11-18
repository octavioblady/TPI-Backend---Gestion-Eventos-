package ar.utn.complejo.recursos.config;

import ar.utn.complejo.common.tiempo.ReglasTiempo;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClockConfig {

    /** Reloj en la zona horaria del complejo. Se reemplaza en los tests para controlar la hora. */
    @Bean
    public Clock reloj() {
        return ReglasTiempo.relojDelComplejo();
    }
}
