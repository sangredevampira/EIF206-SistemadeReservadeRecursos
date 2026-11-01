package Sistema.Red;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonRedTest {

    public record Cita(LocalDate fecha, LocalTime hora) {}

    public record Numero(int valor) {}

    @Test
    void solicitudHaceIdaYVuelta() {
        JsonNode datos = JsonRed.aNodo(Map.of("idReserva", "R-1"));
        MensajeRed original = MensajeRed.solicitud(Operaciones.RES_BUSCAR_ID, datos);
        original.setIdSolicitud("abc");

        MensajeRed leido = JsonRed.leer(JsonRed.aLinea(original));

        assertEquals(TipoMensaje.SOLICITUD, leido.getTipo());
        assertEquals("abc", leido.getIdSolicitud());
        assertEquals("RES_BUSCAR_ID", leido.getOperacion());
        assertEquals("R-1", leido.getDatos().get("idReserva").asText());
    }

    @Test
    void respuestaErrorLlevaExitoFalse() {
        MensajeRed leido = JsonRed.leer(JsonRed.aLinea(
                MensajeRed.respuestaError("id-1", Operaciones.RES_CREAR, "No hay disponibilidad")));

        assertEquals(TipoMensaje.RESPUESTA, leido.getTipo());
        assertEquals(Boolean.FALSE, leido.getExito());
        assertEquals("No hay disponibilidad", leido.getMensaje());
        assertEquals("id-1", leido.getIdSolicitud());
        assertNull(leido.getDatos());
    }

    @Test
    void mensajeConSaltoDeLineaSigueEnUnaLinea() {
        String linea = JsonRed.aLinea(
                MensajeRed.respuestaError("id-1", Operaciones.RES_CREAR, "Línea 1\nLínea 2"));

        assertFalse(linea.contains("\n"));
        assertFalse(linea.contains("\r"));
        assertEquals("Línea 1\nLínea 2", JsonRed.leer(linea).getMensaje());
    }

    @Test
    void fechaYHoraSeEscribenEnFormatoIso() {
        JsonNode datos = JsonRed.aNodo(new Cita(LocalDate.of(2026, 11, 3), LocalTime.of(6, 0)));

        assertEquals("2026-11-03", datos.get("fecha").asText());
        assertEquals("06:00:00", datos.get("hora").asText());
    }

    @Test
    void eventoNoLlevaIdSolicitudNiExito() {
        String linea = JsonRed.aLinea(MensajeRed.evento(Operaciones.RESERVA_CREADA, JsonRed.aNodo(Map.of())));

        assertFalse(linea.contains("idSolicitud"));
        assertFalse(linea.contains("exito"));
        assertTrue(linea.contains("\"tipo\":\"EVENTO\""));
    }

    @Test
    void desdeConDatosInvalidosLanzaIllegalArgumentException() {
        JsonNode invalidos = JsonRed.aNodo(Map.of("valor", "no es número"));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> JsonRed.desde(invalidos, Numero.class));

        assertEquals("Los datos de la solicitud no son válidos", error.getMessage());
    }
}
