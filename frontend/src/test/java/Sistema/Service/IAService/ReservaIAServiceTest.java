package Sistema.Service.IAService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Prueba solo la parte de ExtractorIAReserva que es logica pura (interpretar
 * y validar texto/JSON). Los metodos extraer()/enviarConReintentos() llaman
 * a la API real de Gemini por internet y quedan fuera de este test unitario
 * a proposito: probarlos requeriria una API key real y conexion, lo cual
 * los volveria lentos y poco confiables para correr con "mvn test".
 */

public class ReservaIAServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void limpiarJson_conMarcasDeCodigoJson_lasQuita() {
        String texto = "```json\n{\"actividad\":\"reunion\"}\n```";
        String resultado = ExtractorIAReserva.limpiarJson(texto);
        assertEquals("{\"actividad\":\"reunion\"}", resultado);
    }

    @Test
    void limpiarJsonConMarcasDeCodigoGenericasLasQuita() {
        String texto = "```\n{\"actividad\":\"reunion\"}\n```";
        String resultado = ExtractorIAReserva.limpiarJson(texto);
        assertEquals("{\"actividad\":\"reunion\"}", resultado);
    }

    @Test
    void limpiarJsonSinMarcasDeCodigoLoDejaIgual() {
        String texto = "  {\"actividad\":\"reunion\"}  ";
        String resultado = ExtractorIAReserva.limpiarJson(texto);
        assertEquals("{\"actividad\":\"reunion\"}", resultado);
    }

    @Test
    void normalizarHora_conHoraDeUnDigitoAgregaCeroInicial() { assertEquals("08:05", ExtractorIAReserva.normalizarHora("8:5")); }

    @Test
    void normalizarHoraConHoraDeDosDigitosLaDejaIgual() { assertEquals("14:30", ExtractorIAReserva.normalizarHora("14:30")); }

    @Test
    void normalizarHoraSinMinutosAsumeCero() { assertEquals("09:00", ExtractorIAReserva.normalizarHora("9")); }

    @Test
    void extraerTexto_conRespuestaValida_devuelveElTextoDelModelo() throws Exception {
        String respuestaGemini = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          { "text": "{\\"actividad\\":\\"reunion\\"}" }
                        ]
                      }
                    }
                  ]
                }
                """;

        String resultado = ExtractorIAReserva.extraerTexto(respuestaGemini);

        assertEquals("{\"actividad\":\"reunion\"}", resultado);
    }

    @Test
    void extraerTextoConCandidatesVaciosLanzaExcepcion() {
        String respuestaGemini = "{ \"candidates\": [] }";

        Exception ex = assertThrows(Exception.class,
                () -> ExtractorIAReserva.extraerTexto(respuestaGemini));
        assertTrue(ex.getMessage().contains("Gemini no devolvió contenido"));
    }

    @Test
    void extraerTextoSinCandidatesLanzaExcepcion() {
        String respuestaGemini = "{ \"otraCosa\": true }";

        assertThrows(Exception.class,
                () -> ExtractorIAReserva.extraerTexto(respuestaGemini));
    }

    @Test
    void construirReservaConTodosLosCampo() throws Exception {
        JsonNode datos = mapper.readTree("""
                {
                  "actividad": "Reunión de ventas",
                  "fecha": "2026-10-05",
                  "horaInicio": "08:00",
                  "horaFin": "10:00",
                  "categorias": ["Salas", "Proyectores"]
                }
                """);

        ReservaExtraida resultado = ExtractorIAReserva.construirReservaExtraida(datos);

        assertEquals("Reunión de ventas", resultado.getActividad());
        assertEquals(LocalDate.of(2026, 10, 5), resultado.getFecha());
        assertEquals(LocalTime.of(8, 0), resultado.getHoraInicio());
        assertEquals(LocalTime.of(10, 0), resultado.getHoraFin());
        assertEquals(2, resultado.getCategoriasSugeridas().size());
        assertTrue(resultado.getCategoriasSugeridas().contains("Salas"));
    }

    @Test
    void construirReservaSinFechaNiHoras() throws Exception {
        JsonNode datos = mapper.readTree("""
                {
                  "actividad": "Algo sin fecha clara",
                  "categorias": []
                }
                """);

        ReservaExtraida resultado = ExtractorIAReserva.construirReservaExtraida(datos);

        assertEquals("Algo sin fecha clara", resultado.getActividad());
        assertNull(resultado.getFecha());
        assertNull(resultado.getHoraInicio());
        assertNull(resultado.getHoraFin());
        assertTrue(resultado.getCategoriasSugeridas().isEmpty());
    }

    @Test
    void construirReservaSinCategoriasDevuelveVacio() throws Exception {
        JsonNode datos = mapper.readTree("{ \"actividad\": \"Reunión\" }");

        ReservaExtraida resultado = ExtractorIAReserva.construirReservaExtraida(datos);

        assertNotNull(resultado.getCategoriasSugeridas());
        assertTrue(resultado.getCategoriasSugeridas().isEmpty());
    }

    @Test
    void construirCuerpoPeticionEstructuraGemini() throws Exception {
        String prompt = "Hoy es 2026-09-16. Extrae los datos de: \"reunión mañana a las 8\"";

        String cuerpo = ExtractorIAReserva.construirCuerpoPeticion(prompt);
        JsonNode raiz = mapper.readTree(cuerpo);

        String textoEnviado = raiz.path("contents").path(0)
                .path("parts").path(0)
                .path("text").asText();

        assertEquals(prompt, textoEnviado);
    }
}
