package Sistema.DTO;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class LoginResultadoDTOTest {

    // mismo ObjectMapper por defecto que usa ClienteRed
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void idaYVueltaPorJsonConFuncionarioConservaLosValores() throws Exception {
        LoginResultadoDTO original = new LoginResultadoDTO("111", Rol.FUNCIONARIO,
                new Funcionario("111", "Ana Mora", "8888-8888"));

        LoginResultadoDTO leido = json.readValue(
                json.writeValueAsString(original), LoginResultadoDTO.class);

        assertEquals("111", leido.getIdUsuario());
        assertEquals(Rol.FUNCIONARIO, leido.getRol());
        assertNotNull(leido.getFuncionario());
        assertEquals("111", leido.getFuncionario().getId());
        assertEquals("Ana Mora", leido.getFuncionario().getNombre());
        assertEquals("8888-8888", leido.getFuncionario().getTelefono());
    }

    @Test
    void idaYVueltaPorJsonDelAdministradorConFuncionarioNull() throws Exception {
        LoginResultadoDTO original = new LoginResultadoDTO("admin", Rol.ADMINISTRADOR, null);

        LoginResultadoDTO leido = json.readValue(
                json.writeValueAsString(original), LoginResultadoDTO.class);

        assertEquals("admin", leido.getIdUsuario());
        assertEquals(Rol.ADMINISTRADOR, leido.getRol());
        assertNull(leido.getFuncionario());
    }

    @Test
    void elJsonNoLlevaClaveNiClaveHash() throws Exception {
        LoginResultadoDTO resultado = new LoginResultadoDTO("111", Rol.FUNCIONARIO,
                new Funcionario("111", "Ana Mora", "8888-8888"));

        String texto = json.writeValueAsString(resultado);

        assertFalse(texto.contains("\"clave\""));
        assertFalse(texto.contains("\"claveHash\""));
    }
}
