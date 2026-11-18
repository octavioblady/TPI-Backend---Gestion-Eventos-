package ar.utn.complejo.eventos.config;

import ar.utn.complejo.eventos.clima.ClimaProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/** Clientes HTTP con timeouts explícitos: ninguna dependencia externa puede colgar al servicio. */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient recursosRestClient(RestClient.Builder builder,
                                         @Value("${recursos.url}") String url,
                                         @Value("${recursos.timeout.conexion:1s}") Duration conexion,
                                         @Value("${recursos.timeout.lectura:2s}") Duration lectura,
                                         ObjectProvider<ClientHttpRequestInterceptor> interceptorToken) {
        RestClient.Builder b = builder.clone().baseUrl(url).requestFactory(fabrica(conexion, lectura));
        interceptorToken.ifAvailable(b::requestInterceptor);
        return b.build();
    }

    @Bean
    public RestClient climaRestClient(RestClient.Builder builder, ClimaProperties props) {
        return builder.clone().baseUrl(props.url()).requestFactory(fabrica(props.timeout(), props.timeout())).build();
    }

    private static JdkClientHttpRequestFactory fabrica(Duration conexion, Duration lectura) {
        HttpClient cliente = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(conexion).build();
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(cliente);
        fabrica.setReadTimeout(lectura);
        return fabrica;
    }
}
