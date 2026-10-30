package Sistema.DTO;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ResultadoOperacionDTOTest {

    // mismo ObjectMapper por defecto que usa ClienteRed
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void exitoDejaExitoTrueYMensajeNull() {
        ResultadoOperacionDTO resultado = ResultadoOperacionDTO.exito();

        assertTrue(resultado.isExito());
        assertNull(resultado.getMensaje());
    }

    @Test
    void falloDejaExitoFalseYElMensaje() {
        ResultadoOperacionDTO resultado = ResultadoOperacionDTO.fallo("La descripción ya existe");

        assertFalse(resultado.isExito());
        assertEquals("La descripción ya existe", resultado.getMensaje());
    }

    @Test
    void idaYVueltaPorJsonConservaLosValores() throws Exception {
        ResultadoOperacionDTO original = ResultadoOperacionDTO.fallo("La descripción ya existe");

        ResultadoOperacionDTO leido = json.readValue(
                json.writeValueAsString(original), ResultadoOperacionDTO.class);

        assertFalse(leido.isExito());
        assertEquals("La descripción ya existe", leido.getMensaje());
    }
}
