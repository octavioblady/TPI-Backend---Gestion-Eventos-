package ar.utn.complejo.eventos;

import ar.utn.complejo.eventos.clima.ClimaNoDisponibleException;
import ar.utn.complejo.eventos.clima.Pronostico;
import ar.utn.complejo.eventos.recursos.RecursosNoDisponibleException;
import ar.utn.complejo.eventos.recursos.RecursosRechazoException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Proceso central, estados T-01 a T-06, reglas 5 y 6, seguridad y manejo de fallas de las dependencias. */
@AutoConfigureMockMvc
class EventoApiTest extends BaseEventosTest {

    @Autowired
    MockMvc mvc;

    private static RequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")).jwt(j -> j.subject("admin-1"));
    }

    private static RequestPostProcessor cliente(String sub) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_CLIENTE")).jwt(j -> j.subject(sub));
    }

    private static String body(String instalaciones, String fecha, String desde, String hasta, int asistentes, String servicios) {
        return """
                {"instalacionIds":%s,"inicio":"%sT%s:00","fin":"%sT%s:00","asistentes":%d%s}"""
                .formatted(instalaciones, fecha, desde, fecha, hasta, asistentes, servicios == null ? "" : ",\"servicios\":" + servicios);
    }

    private long solicitar(RequestPostProcessor quien, String json) throws Exception {
        String r = mvc.perform(post("/api/v1/eventos").with(quien).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return Long.parseLong(r.replaceAll(".*?\"id\":(\\d+).*", "$1"));
    }

    private long eventoConfirmado(String cli, String fecha, String instalaciones) throws Exception {
        long id = solicitar(cliente(cli), body(instalaciones, fecha, "18:00", "22:00", 30, null));
        mvc.perform(patch("/api/v1/eventos/" + id + "/senia").with(admin())).andExpect(jsonPath("$.estado", is("CONFIRMADO")));
        return id;
    }

    // ------------------------------------------------------------- T-01 SOLICITADO

    @Test
    void solicitarCotizaYDejaElEventoSolicitado() throws Exception {
        mvc.perform(post("/api/v1/eventos").with(cliente("cli-1")).contentType(MediaType.APPLICATION_JSON)
                        .content(body("[1,3]", "2026-12-10", "18:00", "22:00", 30, "[{\"servicioId\":7,\"cantidad\":20}]")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado", is("SOLICITADO")))
                .andExpect(jsonPath("$.clienteId", is("cli-1")))
                .andExpect(jsonPath("$.precioTotal", is(84000.0)))     // 2 instalaciones x 4 h x 10.000 + 20 sillas x 200
                .andExpect(jsonPath("$.seniaMonto", is(42000.0)))      // 50 %
                .andExpect(jsonPath("$.seniaAbonada", is(false)))
                .andExpect(jsonPath("$.historial", hasSize(1)));
        verify(recursos, never()).reservar(any()); // todavía no se reserva nada
    }

    @Test
    void validacionesDeEntrada() throws Exception {
        mvc.perform(post("/api/v1/eventos").with(cliente("c")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instalacionIds\":[],\"asistentes\":0}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo", is("VALIDACION")));
        mvc.perform(post("/api/v1/eventos").with(cliente("c")).contentType(MediaType.APPLICATION_JSON)
                        .content(body("[1]", "2026-12-10", "22:00", "18:00", 10, null)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo", is("PERIODO_INVALIDO")));
        mvc.perform(post("/api/v1/eventos").with(cliente("c")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instalacionIds\":[1],\"inicio\":\"2026-12-10T22:00:00\",\"fin\":\"2026-12-11T02:00:00\",\"asistentes\":5}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo", is("PERIODO_INVALIDO")));
    }

    @Test
    void instalacionInexistenteSePropagaComo404() throws Exception {
        doThrow(new RecursosRechazoException(404, "INSTALACION_NO_ENCONTRADA", "No existen las instalaciones [99]")).when(recursos).cotizar(any());
        mvc.perform(post("/api/v1/eventos").with(cliente("c")).contentType(MediaType.APPLICATION_JSON)
                        .content(body("[99]", "2026-12-10", "18:00", "22:00", 10, null)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo", is("INSTALACION_NO_ENCONTRADA")));
    }

    @Test
    void elAdminPuedeSolicitarEnNombreDeUnCliente_elClienteNoPuedeSolicitarParaOtro() throws Exception {
        String conCliente = "{\"clienteId\":\"cli-9\"," + body("[1]", "2026-12-10", "18:00", "22:00", 10, null).substring(1);
        mvc.perform(post("/api/v1/eventos").with(admin()).contentType(MediaType.APPLICATION_JSON).content(conCliente))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.clienteId", is("cli-9")));
        mvc.perform(post("/api/v1/eventos").with(cliente("cli-1")).contentType(MediaType.APPLICATION_JSON).content(conCliente))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo", is("EVENTO_AJENO")));
    }

    // ------------------------------------------------------------- T-02 CONFIRMADO / T-03 RECHAZADO

    @Test
    void conSeniaYCondicionesValidasElEventoSeConfirma_E01() throws Exception {
        long id = solicitar(cliente("cli-1"), body("[1]", "2026-12-10", "18:00", "22:00", 30, "[{\"servicioId\":7,\"cantidad\":20}]"));
        mvc.perform(patch("/api/v1/eventos/" + id + "/senia").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado", is("CONFIRMADO")))
                .andExpect(jsonPath("$.seniaAbonada", is(true)))
                .andExpect(jsonPath("$.historial", hasSize(2)));
        verify(recursos).reservar(argThat(r -> r.eventoId() == id && r.asistentes() == 30 && r.servicios().size() == 1));
    }

    @Test
    void sinSeniaElEventoPasaDeSolicitadoARechazado_yNoSeReservaNada() throws Exception {
        long id = solicitar(cliente("cli-1"), body("[1]", "2026-12-10", "18:00", "22:00", 30, null));
        mvc.perform(patch("/api/v1/eventos/" + id + "/senia").with(admin()).contentType(MediaType.APPLICATION_JSON).content("{\"abonada\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado", is("RECHAZADO")))
                .andExpect(jsonPath("$.codigoRechazo", is("SENIA_NO_ABONADA")))
                .andExpect(jsonPath("$.seniaAbonada", is(false)));
        verify(recursos, never()).reservar(any());
    }

    @Test
    void siRecursosRechazaPorReglaDeNegocioElEventoQuedaRechazadoConElMotivo_E04_E06_E08() throws Exception {
        doThrow(new RecursosRechazoException(409, "MANTENIMIENTO", "Mantenimiento: SUM mediano")).when(recursos).reservar(any());
        long id = solicitar(cliente("cli-1"), body("[1]", "2026-12-10", "18:00", "22:00", 30, null));
        mvc.perform(patch("/api/v1/eventos/" + id + "/senia").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado", is("RECHAZADO")))
                .andExpect(jsonPath("$.codigoRechazo", is("MANTENIMIENTO")))
                .andExpect(jsonPath("$.motivoRechazo", containsString("Mantenimiento")));
        verify(recursos, never()).liberar(anyLong());
    }

    @Test
    void recursosCaido_503_ElEventoQuedaRechazadoYSeIntentaLiberar_E25() throws Exception {
        doThrow(new RecursosNoDisponibleException("sin conexión", false, null)).when(recursos).reservar(any());
        long id = solicitar(cliente("cli-1"), body("[1]", "2026-12-10", "18:00", "22:00", 30, null));
        mvc.perform(patch("/api/v1/eventos/" + id + "/senia").with(admin()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo", is("RECURSOS_NO_DISPONIBLE")));
        mvc.perform(get("/api/v1/eventos/" + id).with(admin()))
                .andExpect(jsonPath("$.estado", is("RECHAZADO")))
                .andExpect(jsonPath("$.codigoRechazo", is("RECURSOS_NO_DISPONIBLE")));
        verify(recursos).liberar(id);
    }

    @Test
    void recursosDemoraMasQueElTimeout_504_E26() throws Exception {
        doThrow(new RecursosNoDisponibleException("timeout", true, null)).when(recursos).reservar(any());
        long id = solicitar(cliente("cli-1"), body("[1]", "2026-12-10", "18:00", "22:00", 30, null));
        mvc.perform(patch("/api/v1/eventos/" + id + "/senia").with(admin()))
                .andExpect(status().isGatewayTimeout()).andExpect(jsonPath("$.codigo", is("RECURSOS_TIMEOUT")));
    }

    @Test
    void noSePuedeRegistrarLaSeniaDosVeces() throws Exception {
        long id = eventoConfirmado("cli-1", "2026-12-10", "[1]");
        mvc.perform(patch("/api/v1/eventos/" + id + "/senia").with(admin()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo", is("TRANSICION_INVALIDA")));
    }

    // ------------------------------------------------------------- Regla 6: clima

    @Test
    void pronosticoAdversoEnElPatioConfirmaConAdvertenciaRegistrada_E15() throws Exception {
        when(pronostico.pronosticoPara(any())).thenReturn(new Pronostico(70, 20, false));
        long id = solicitar(cliente("cli-1"), body("[2]", "2026-12-10", "18:00", "22:00", 30, null));
        mvc.perform(patch("/api/v1/eventos/" + id + "/senia").with(admin()))
                .andExpect(jsonPath("$.estado", is("CONFIRMADO")))
                .andExpect(jsonPath("$.advertencias", hasSize(1)))
                .andExpect(jsonPath("$.advertencias[0].probabilidadLluvia", is(70)))
                .andExpect(jsonPath("$.advertencias[0].detalle", containsString("lluvia")));
    }

    @Test
    void pronosticoFavorableNoGeneraAdvertencia() throws Exception {
        when(pronostico.pronosticoPara(any())).thenReturn(new Pronostico(10, 15, false));
        long id = solicitar(cliente("cli-1"), body("[2]", "2026-12-10", "18:00", "22:00", 30, null));
        mvc.perform(patch("/api/v1/eventos/" + id + "/senia").with(admin()))
                .andExpect(jsonPath("$.estado", is("CONFIRMADO"))).andExpect(jsonPath("$.advertencias", hasSize(0)));
    }

    @Test
    void siLaApiMeteorologicaFallaElEventoSeConfirmaSinAdvertencia_E16_E28() throws Exception {
        when(pronostico.pronosticoPara(any())).thenThrow(new ClimaNoDisponibleException("caída", null));
        long id = solicitar(cliente("cli-1"), body("[2]", "2026-12-10", "18:00", "22:00", 30, null));
        mvc.perform(patch("/api/v1/eventos/" + id + "/senia").with(admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado", is("CONFIRMADO")))
                .andExpect(jsonPath("$.advertencias", hasSize(0)));
    }

    @Test
    void eventoCubiertoNoConsultaElClima() throws Exception {
        eventoConfirmado("cli-1", "2026-12-10", "[1]");
        verifyNoInteractions(pronostico);
    }

    @Test
    void masDe16DiasNoSeConsultaAlConfirmar_peroSePuedeReconsultarMasAdelante() throws Exception {
        when(pronostico.pronosticoPara(any())).thenReturn(new Pronostico(80, 0, false));
        long id = solicitar(cliente("cli-1"), body("[2]", "2026-12-30", "18:00", "22:00", 30, null));
        mvc.perform(patch("/api/v1/eventos/" + id + "/senia").with(admin()))
                .andExpect(jsonPath("$.estado", is("CONFIRMADO"))).andExpect(jsonPath("$.advertencias", hasSize(0)));
        verifyNoInteractions(pronostico);
        mvc.perform(post("/api/v1/eventos/" + id + "/pronostico").with(cliente("cli-1")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado", is("FUERA_DE_HORIZONTE")));
    }

    @Test
    void elClientePuedeReconsultarElPronosticoYSeRegistraLaAdvertencia() throws Exception {
        when(pronostico.pronosticoPara(any())).thenReturn(new Pronostico(0, 0, true));
        long id = eventoConfirmado("cli-1", "2026-12-10", "[2]");
        mvc.perform(post("/api/v1/eventos/" + id + "/pronostico").with(cliente("cli-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado", is("ADVERTENCIA")))
                .andExpect(jsonPath("$.advertencia.granizo", is(true)));
        // una al confirmar (el mock ya devolvía adverso) y otra por la reconsulta
        mvc.perform(get("/api/v1/eventos/" + id).with(cliente("cli-1"))).andExpect(jsonPath("$.advertencias", hasSize(2)))
                .andExpect(jsonPath("$.estado", is("CONFIRMADO"))); // solo advierte: no cambia el estado
        mvc.perform(post("/api/v1/eventos/" + id + "/pronostico").with(cliente("otro"))).andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------- T-04 CANCELADO (regla 5)

    @Test
    void cancelarConMasDe48HorasLiberaYLaSeniaEsReembolsable_E11() throws Exception {
        long id = eventoConfirmado("cli-1", "2026-12-10", "[1]");
        mvc.perform(patch("/api/v1/eventos/" + id + "/cancelacion").with(cliente("cli-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado", is("CANCELADO")))
                .andExpect(jsonPath("$.seniaReembolsable", is(true)));
        verify(recursos).liberar(id);
    }

    @Test
    void cancelarConMenosDe48HorasLaSeniaNoSeDevuelve_E12() throws Exception {
        long id = eventoConfirmado("cli-1", "2026-12-02", "[1]"); // mañana: la anticipación la valida Recursos (simulado OK)
        mvc.perform(patch("/api/v1/eventos/" + id + "/cancelacion").with(cliente("cli-1")))
                .andExpect(jsonPath("$.estado", is("CANCELADO"))).andExpect(jsonPath("$.seniaReembolsable", is(false)));
    }

    @Test
    void cancelarConExactamente48HorasEsReembolsable() throws Exception {
        long id = eventoConfirmado("cli-1", "2026-12-03", "[1]"); // el evento empieza 3/12 18:00 = 56 h
        // borde: evento a las 10:00 del 3/12 = exactamente 48 h desde AHORA
        long id2 = solicitar(cliente("cli-1"), body("[3]", "2026-12-03", "10:00", "12:00", 5, null));
        mvc.perform(patch("/api/v1/eventos/" + id2 + "/senia").with(admin()));
        mvc.perform(patch("/api/v1/eventos/" + id2 + "/cancelacion").with(cliente("cli-1")))
                .andExpect(jsonPath("$.seniaReembolsable", is(true)));
    }

    @Test
    void siRecursosNoRespondeAlCancelarElEventoSigueConfirmado() throws Exception {
        long id = eventoConfirmado("cli-1", "2026-12-10", "[1]");
        doThrow(new RecursosNoDisponibleException("caído", false, null)).when(recursos).liberar(id);
        mvc.perform(patch("/api/v1/eventos/" + id + "/cancelacion").with(cliente("cli-1"))).andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/v1/eventos/" + id).with(admin())).andExpect(jsonPath("$.estado", is("CONFIRMADO")));
    }

    @Test
    void unClienteNoPuedeCancelarElEventoDeOtro_E19() throws Exception {
        long id = eventoConfirmado("cli-1", "2026-12-10", "[1]");
        mvc.perform(patch("/api/v1/eventos/" + id + "/cancelacion").with(cliente("cli-2")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo", is("EVENTO_AJENO")));
        verify(recursos, never()).liberar(anyLong());
    }

    @Test
    void soloSePuedeCancelarDesdeConfirmado() throws Exception {
        long solicitado = solicitar(cliente("cli-1"), body("[1]", "2026-12-10", "18:00", "22:00", 30, null));
        mvc.perform(patch("/api/v1/eventos/" + solicitado + "/cancelacion").with(cliente("cli-1"))).andExpect(status().isConflict());

        long enCurso = eventoConfirmado("cli-1", "2026-12-11", "[1]");
        mvc.perform(patch("/api/v1/eventos/" + enCurso + "/inicio").with(admin())).andExpect(status().isOk());
        mvc.perform(patch("/api/v1/eventos/" + enCurso + "/cancelacion").with(cliente("cli-1")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo", is("TRANSICION_INVALIDA")));
    }

    // ------------------------------------------------------------- T-05 / T-06

    @Test
    void cicloDeVidaInicioYFinalizacion_E20() throws Exception {
        long id = eventoConfirmado("cli-1", "2026-12-10", "[1]");
        mvc.perform(patch("/api/v1/eventos/" + id + "/inicio").with(admin())).andExpect(jsonPath("$.estado", is("EN_CURSO")));
        mvc.perform(patch("/api/v1/eventos/" + id + "/finalizacion").with(admin()))
                .andExpect(jsonPath("$.estado", is("FINALIZADO"))).andExpect(jsonPath("$.historial", hasSize(4)));
    }

    @Test
    void noSePuedeIniciarUnEventoCanceladoORechazado_E21() throws Exception {
        long cancelado = eventoConfirmado("cli-1", "2026-12-10", "[1]");
        mvc.perform(patch("/api/v1/eventos/" + cancelado + "/cancelacion").with(cliente("cli-1")));
        mvc.perform(patch("/api/v1/eventos/" + cancelado + "/inicio").with(admin())).andExpect(status().isConflict());

        long rechazado = solicitar(cliente("cli-1"), body("[1]", "2026-12-10", "18:00", "22:00", 30, null));
        mvc.perform(patch("/api/v1/eventos/" + rechazado + "/senia").with(admin()).contentType(MediaType.APPLICATION_JSON).content("{\"abonada\":false}"));
        mvc.perform(patch("/api/v1/eventos/" + rechazado + "/inicio").with(admin()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo", is("TRANSICION_INVALIDA")));
    }

    // ------------------------------------------------------------- Seguridad y consultas

    @Test
    void sinTokenResponde401_E18() throws Exception {
        mvc.perform(get("/api/v1/eventos")).andExpect(status().isUnauthorized());
    }

    @Test
    void elClienteNoPuedeRegistrarSeniaNiIniciarNiFinalizar_E17() throws Exception {
        long id = solicitar(cliente("cli-1"), body("[1]", "2026-12-10", "18:00", "22:00", 30, null));
        mvc.perform(patch("/api/v1/eventos/" + id + "/senia").with(cliente("cli-1"))).andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/eventos/" + id + "/inicio").with(cliente("cli-1"))).andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/eventos/" + id + "/finalizacion").with(cliente("cli-1"))).andExpect(status().isForbidden());
    }

    @Test
    void elClienteSoloVeSusEventos_elAdminVeTodos() throws Exception {
        long mio = solicitar(cliente("cli-1"), body("[1]", "2026-12-10", "10:00", "12:00", 5, null));
        solicitar(cliente("cli-2"), body("[1]", "2026-12-10", "14:00", "16:00", 5, null));

        mvc.perform(get("/api/v1/eventos").with(cliente("cli-1")))
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].id", is((int) mio)));
        mvc.perform(get("/api/v1/eventos").with(admin())).andExpect(jsonPath("$", hasSize(2)));
        mvc.perform(get("/api/v1/eventos/" + mio).with(cliente("cli-2")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/eventos/999999").with(admin())).andExpect(status().isNotFound());
    }
}
