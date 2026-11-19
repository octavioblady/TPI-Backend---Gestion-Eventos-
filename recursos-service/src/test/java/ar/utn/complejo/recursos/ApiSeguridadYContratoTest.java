package ar.utn.complejo.recursos;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Seguridad por rol (401/403), contrato de errores estándar y flujos de mantenimiento y disponibilidad vía HTTP. */
@AutoConfigureMockMvc
class ApiSeguridadYContratoTest extends BaseIntegrationTest {

    @Autowired
    MockMvc mvc;

    private static org.springframework.test.web.servlet.request.RequestPostProcessor como(String rol) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + rol)).jwt(j -> j.subject("usuario-" + rol));
    }

    private static final String INSTALACION_JSON = """
            {"nombre":"Quincho","capacidad":30,"aireLibre":true,
             "horarios":[{"dia":"SATURDAY","apertura":"10:00","cierre":"20:00"}]}""";

    @Test
    void sinTokenResponde401() throws Exception {
        mvc.perform(get("/api/v1/instalaciones")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/disponibilidad").param("fecha", "2026-12-10").param("horaInicio", "10:00").param("horaFin", "12:00"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void clienteListaInstalaciones() throws Exception {
        mvc.perform(get("/api/v1/instalaciones").with(como("CLIENTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    void clienteNoPuedeCrearInstalaciones403() throws Exception {
        mvc.perform(post("/api/v1/instalaciones").with(como("CLIENTE")).contentType(MediaType.APPLICATION_JSON).content(INSTALACION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCreaInstalacionConTarifaPorDefecto() throws Exception {
        mvc.perform(post("/api/v1/instalaciones").with(como("ADMIN")).contentType(MediaType.APPLICATION_JSON).content(INSTALACION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tarifaHora", is(10000)))
                .andExpect(jsonPath("$.activa", is(true)));
    }

    @Test
    void validacionDevuelveErrorEstandar400() throws Exception {
        mvc.perform(post("/api/v1/instalaciones").with(como("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"\",\"capacidad\":-1,\"horarios\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo", is("VALIDACION")))
                .andExpect(jsonPath("$.detalles", not(empty())))
                .andExpect(jsonPath("$.path", is("/api/v1/instalaciones")));
    }

    @Test
    void bajaLogicaOcultaLaInstalacionDelListado() throws Exception {
        mvc.perform(delete("/api/v1/instalaciones/" + cancha.getId()).with(como("ADMIN"))).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/instalaciones").with(como("CLIENTE"))).andExpect(jsonPath("$", hasSize(2)));
        mvc.perform(get("/api/v1/instalaciones?soloActivas=true").with(como("CLIENTE")))
                .andExpect(jsonPath("$", hasSize(2))).andExpect(jsonPath("$[*].activa", everyItem(is(true))));
        mvc.perform(get("/api/v1/instalaciones?soloActivas=false").with(como("CLIENTE")))
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].activa", is(false)));
    }

    @Test
    void endpointsInternosSoloAceptanRolServicio() throws Exception {
        String body = """
                {"eventoId":1,"instalacionIds":[%d],"inicio":"2026-12-10T18:00:00","fin":"2026-12-10T20:00:00","asistentes":10}"""
                .formatted(sum.getId());
        mvc.perform(post("/internal/reservas").with(como("CLIENTE")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/internal/reservas").with(como("ADMIN")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/internal/reservas").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/internal/reservas").with(como("SERVICIO")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.instalaciones", hasSize(1)));
    }

    @Test
    void conflictoDeReservaDevuelve409ConCodigoFuncional() throws Exception {
        String body = """
                {"eventoId":%d,"instalacionIds":[%d],"inicio":"2026-12-10T18:00:00","fin":"2026-12-10T20:00:00","asistentes":10}""";
        mvc.perform(post("/internal/reservas").with(como("SERVICIO")).contentType(MediaType.APPLICATION_JSON)
                .content(body.formatted(1, sum.getId()))).andExpect(status().isCreated());
        mvc.perform(post("/internal/reservas").with(como("SERVICIO")).contentType(MediaType.APPLICATION_JSON)
                        .content(body.formatted(2, sum.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo", is("CONFLICTO_DISPONIBILIDAD")));
    }

    @Test
    void disponibilidadParaMananaNoTieneEspacios_regla1() throws Exception {
        mvc.perform(get("/api/v1/disponibilidad").with(como("CLIENTE"))
                        .param("fecha", "2026-12-02").param("horaInicio", "10:00").param("horaFin", "12:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].disponible", everyItem(is(false))))
                .andExpect(jsonPath("$[0].codigoMotivo", is("ANTICIPACION_INSUFICIENTE")));
    }

    @Test
    void disponibilidadFiltraPorCapacidadYHorario() throws Exception {
        mvc.perform(get("/api/v1/disponibilidad").with(como("CLIENTE"))
                        .param("fecha", "2026-12-10").param("horaInicio", "10:00").param("horaFin", "12:00").param("capacidad", "80"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre=='Cancha multiuso')].disponible", contains(true)))
                .andExpect(jsonPath("$[?(@.nombre=='SUM mediano')].codigoMotivo", contains("CAPACIDAD_EXCEDIDA")));
        mvc.perform(get("/api/v1/disponibilidad").with(como("CLIENTE"))
                        .param("fecha", "2026-12-10").param("horaInicio", "21:00").param("horaFin", "23:00"))
                .andExpect(jsonPath("$[?(@.nombre=='Cancha multiuso')].codigoMotivo", contains("FUERA_DE_HORARIO")));
    }

    @Test
    void parametroConFormatoInvalidoDevuelve400() throws Exception {
        mvc.perform(get("/api/v1/disponibilidad").with(como("CLIENTE"))
                        .param("fecha", "no-es-fecha").param("horaInicio", "10:00").param("horaFin", "12:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo", is("PARAMETRO_INVALIDO")));
    }

    @Test
    void mantenimientoBloqueaYAlFinalizarSeLiberaLaInstalacion_E06_E07() throws Exception {
        String body = """
                {"instalacionId":%d,"desde":"2026-12-09T00:00:00","hasta":"2026-12-12T00:00:00","motivo":"Pintura"}""".formatted(sum.getId());
        String respuesta = mvc.perform(post("/api/v1/mantenimientos").with(como("ADMIN")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado", is("ACTIVO")))
                .andReturn().getResponse().getContentAsString();
        String id = respuesta.replaceAll(".*\"id\":(\\d+).*", "$1");

        mvc.perform(get("/api/v1/disponibilidad").with(como("CLIENTE"))
                        .param("fecha", "2026-12-10").param("horaInicio", "10:00").param("horaFin", "12:00"))
                .andExpect(jsonPath("$[?(@.nombre=='SUM mediano')].motivo", contains("Mantenimiento")));

        mvc.perform(patch("/api/v1/mantenimientos/" + id + "/finalizacion").with(como("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado", is("FINALIZADO")));
        mvc.perform(patch("/api/v1/mantenimientos/" + id + "/finalizacion").with(como("ADMIN")))
                .andExpect(status().isConflict());

        mvc.perform(get("/api/v1/disponibilidad").with(como("CLIENTE"))
                        .param("fecha", "2026-12-10").param("horaInicio", "10:00").param("horaFin", "12:00"))
                .andExpect(jsonPath("$[?(@.nombre=='SUM mediano')].disponible", contains(true)));
    }

    @Test
    void noSePuedeBloquearUnPeriodoConReservasActivas() throws Exception {
        mvc.perform(post("/internal/reservas").with(como("SERVICIO")).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"eventoId":1,"instalacionIds":[%d],"inicio":"2026-12-10T18:00:00","fin":"2026-12-10T20:00:00","asistentes":10}"""
                        .formatted(sum.getId()))).andExpect(status().isCreated());
        mvc.perform(post("/api/v1/mantenimientos").with(como("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"instalacionId":%d,"desde":"2026-12-10T00:00:00","hasta":"2026-12-11T00:00:00"}""".formatted(sum.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo", is("MANTENIMIENTO_CON_RESERVAS")));
    }

    @Test
    void servicioNoPuedeBajarElStockPorDebajoDeLoReservado() throws Exception {
        mvc.perform(post("/internal/reservas").with(como("SERVICIO")).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"eventoId":1,"instalacionIds":[%d],"inicio":"2026-12-10T18:00:00","fin":"2026-12-10T20:00:00","asistentes":10,
                         "servicios":[{"servicioId":%d,"cantidad":30}]}""".formatted(sum.getId(), sillas.getId()))).andExpect(status().isCreated());
        mvc.perform(put("/api/v1/servicios/" + sillas.getId()).with(como("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Sillas\",\"precioUnitario\":250,\"stockTotal\":10}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo", is("STOCK_MENOR_A_RESERVADO")));
        mvc.perform(get("/api/v1/servicios/" + sillas.getId() + "/stock").with(como("CLIENTE")).param("fecha", "2026-12-10"))
                .andExpect(jsonPath("$.disponible", is(20)));
    }

    @Test
    void swaggerYOpenApiSeServenSinToken() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/instalaciones']").exists())
                .andExpect(jsonPath("$.paths['/internal/reservas']").exists());
    }

    @Test
    void cotizacionDevuelveTarifasYPreciosSinReservar() throws Exception {
        mvc.perform(post("/internal/reservas/cotizacion").with(como("SERVICIO")).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"instalacionIds":[%d,%d],"servicios":[{"servicioId":%d,"cantidad":5}]}"""
                                .formatted(cancha.getId(), salon.getId(), sillas.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instalaciones", hasSize(2)))
                .andExpect(jsonPath("$.instalaciones[?(@.nombre=='Salón principal y patio')].aireLibre", contains(true)))
                .andExpect(jsonPath("$.servicios[0].precioUnitario", is(200.0)));
        org.junit.jupiter.api.Assertions.assertTrue(reservasEspacio.findAll().isEmpty());
        mvc.perform(post("/internal/reservas/cotizacion").with(como("CLIENTE")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instalacionIds\":[1]}")).andExpect(status().isForbidden());
        mvc.perform(post("/internal/reservas/cotizacion").with(como("SERVICIO")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instalacionIds\":[99999]}")).andExpect(status().isNotFound());
    }
}
