package ui;

import Sistema.DTO.Funcionario;
import Sistema.DTO.LoginResultadoDTO;
import Sistema.DTO.ResultadoOperacionDTO;
import Sistema.DTO.Rol;
import Sistema.Service.IAuthService;
import Sistema.util.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Prueba la logica del boton Ingresar (LoginController.iniciarSesion) con un
 * IAuthService falso: no usa red ni abre ventanas, asi que no necesita JavaFX.
 * Abrir la ventana principal se revisa en la prueba manual.
 */
class LoginControllerTest {

    @BeforeEach
    @AfterEach
    void limpiarSesion() {
        SessionManager.getInstancia().cerrarSesion();
    }

    @Test
    void loginDeAdministradorIniciaSesionSinFuncionario() {
        AuthFalso auth = new AuthFalso();
        auth.resultado = new LoginResultadoDTO("admin", Rol.ADMINISTRADOR, null);

        assertNull(new LoginController(auth).iniciarSesion("admin", "admin"));

        SessionManager sesion = SessionManager.getInstancia();
        assertEquals("admin", sesion.getIdUsuario());
        assertEquals("ADMINISTRADOR", sesion.getRol());
        assertNull(sesion.getFuncionarioAsociado());
    }

    @Test
    void loginDeFuncionarioGuardaSuFuncionario() {
        Funcionario funcionario = new Funcionario("44444", "Ana", "8888-8888");
        AuthFalso auth = new AuthFalso();
        auth.resultado = new LoginResultadoDTO("44444", Rol.FUNCIONARIO, funcionario);

        assertNull(new LoginController(auth).iniciarSesion("44444", "44444"));

        SessionManager sesion = SessionManager.getInstancia();
        assertEquals("44444", sesion.getIdUsuario());
        assertEquals("FUNCIONARIO", sesion.getRol());
        assertSame(funcionario, sesion.getFuncionarioAsociado());
    }

    @Test
    void guardaElIdQueDevuelveElServidorNoElEscrito() {
        AuthFalso auth = new AuthFalso();
        auth.resultado = new LoginResultadoDTO("admin", Rol.ADMINISTRADOR, null);

        assertNull(new LoginController(auth).iniciarSesion("ADMIN", "admin"));

        assertEquals("admin", SessionManager.getInstancia().getIdUsuario());
    }

    @Test
    void pasaElIdYLaClaveSinModificarlos() {
        AuthFalso auth = new AuthFalso();
        auth.resultado = new LoginResultadoDTO("123", Rol.FUNCIONARIO, new Funcionario("123", "Luis", "1"));

        new LoginController(auth).iniciarSesion(" 123 ", "clave con espacios ");

        assertEquals(" 123 ", auth.idRecibido);
        assertEquals("clave con espacios ", auth.claveRecibida);
    }

    @Test
    void reglaDeNegocioMuestraElMensajeDelServidorYNoIniciaSesion() {
        AuthFalso auth = new AuthFalso();
        auth.error = new IllegalArgumentException("ID o clave incorrectos.");

        assertEquals("ID o clave incorrectos.", new LoginController(auth).iniciarSesion("admin", "mala"));

        assertNull(SessionManager.getInstancia().getIdUsuario());
    }

    @Test
    void problemaDeRedMuestraElMensajeDeConexionYNoIniciaSesion() {
        AuthFalso auth = new AuthFalso();
        auth.error = new IllegalStateException("No fue posible comunicarse con el servidor.");

        assertEquals(LoginController.MENSAJE_SIN_CONEXION,
                new LoginController(auth).iniciarSesion("admin", "admin"));

        assertNull(SessionManager.getInstancia().getIdUsuario());
    }

    @Test
    void errorInesperadoMuestraElMensajeGenerico() {
        AuthFalso auth = new AuthFalso();
        // un rol nulo provoca NullPointerException al iniciar la sesion local
        auth.resultado = new LoginResultadoDTO("admin", null, null);

        assertEquals(LoginController.MENSAJE_ERROR_INESPERADO,
                new LoginController(auth).iniciarSesion("admin", "admin"));

        assertNull(SessionManager.getInstancia().getIdUsuario());
    }

    @Test
    void elConstructorDePruebasRechazaUnServicioNulo() {
        assertThrows(NullPointerException.class, () -> new LoginController(null));
    }

    private static final class AuthFalso implements IAuthService {

        private LoginResultadoDTO resultado;
        private RuntimeException error;
        private String idRecibido;
        private String claveRecibida;

        @Override
        public LoginResultadoDTO login(String id, String clave) {
            idRecibido = id;
            claveRecibida = clave;
            if (error != null) {
                throw error;
            }
            return resultado;
        }

        @Override
        public ResultadoOperacionDTO cambiarClave(String id, String actual, String nueva, String confirmacion) {
            throw new UnsupportedOperationException("No se usa en estas pruebas.");
        }

        @Override
        public void logout() {
            throw new UnsupportedOperationException("No se usa en estas pruebas.");
        }
    }
}
