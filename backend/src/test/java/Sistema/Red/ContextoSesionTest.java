package Sistema.Red;

import Sistema.DTO.LoginResultadoDTO;
import Sistema.DTO.Rol;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ContextoSesionTest {

    private final ContextoSesion contexto = new ContextoSesion();

    @Test
    void alInicioNadieEstaAutenticado() {
        assertFalse(contexto.estaAutenticado());
        assertNull(contexto.getIdUsuario());
        assertNull(contexto.getRol());
    }

    @Test
    void iniciarYCerrarSesionCambiaElEstado() {
        contexto.iniciarSesion(new LoginResultadoDTO("u1", Rol.FUNCIONARIO, null));
        assertTrue(contexto.estaAutenticado());
        assertEquals("u1", contexto.getIdUsuario());
        assertEquals(Rol.FUNCIONARIO, contexto.getRol());

        contexto.cerrarSesion();
        assertFalse(contexto.estaAutenticado());
    }

    @Test
    void exigirAdministradorRechazaAUnFuncionario() {
        contexto.iniciarSesion(new LoginResultadoDTO("u1", Rol.FUNCIONARIO, null));
        IllegalArgumentException error =
                assertThrows(IllegalArgumentException.class, contexto::exigirAdministrador);
        assertEquals("No tiene permiso para esta operación.", error.getMessage());
    }

    @Test
    void exigirAdministradorRechazaSiNoHaySesion() {
        assertThrows(IllegalArgumentException.class, contexto::exigirAdministrador);
    }

    @Test
    void exigirAdministradorPermiteAlAdministrador() {
        contexto.iniciarSesion(new LoginResultadoDTO("admin", Rol.ADMINISTRADOR, null));
        assertDoesNotThrow(contexto::exigirAdministrador);
    }
}