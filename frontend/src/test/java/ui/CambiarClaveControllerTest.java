package ui;

import Sistema.DTO.LoginResultadoDTO;
import Sistema.DTO.ResultadoOperacionDTO;
import Sistema.Service.IAuthService;
import Sistema.util.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Prueba la logica del boton Confirmar (CambiarClaveController.cambiarClave) y
 * la eleccion del usuario, con un IAuthService falso: sin red y sin JavaFX.
 * Cerrar el dialogo se revisa en la prueba manual.
 */
class CambiarClaveControllerTest {

    @BeforeEach
    @AfterEach
    void limpiarSesion() {
        SessionManager.getInstancia().cerrarSesion();
    }

    @Test
    void cambioExitosoDevuelveNullYPasaLosCuatroDatos() {
        AuthFalso auth = new AuthFalso();
        auth.resultado = ResultadoOperacionDTO.exito();

        assertNull(new CambiarClaveController(auth).cambiarClave("123", "1234", "nueva1", "nueva1"));

        assertEquals("123", auth.idRecibido);
        assertEquals("1234", auth.actualRecibida);
        assertEquals("nueva1", auth.nuevaRecibida);
        assertEquals("nueva1", auth.confirmacionRecibida);
    }

    @Test
    void rechazoDelServidorMuestraSuMensaje() {
        AuthFalso auth = new AuthFalso();
        auth.resultado = ResultadoOperacionDTO.fallo("Las dos claves nuevas no coinciden.");

        assertEquals("Las dos claves nuevas no coinciden.",
                new CambiarClaveController(auth).cambiarClave("123", "1234", "a", "b"));
    }

    @Test
    void problemaDeRedMuestraElMensajeDeConexion() {
        AuthFalso auth = new AuthFalso();
        auth.error = new IllegalStateException("No fue posible comunicarse con el servidor.");

        assertEquals(CambiarClaveController.MENSAJE_SIN_CONEXION,
                new CambiarClaveController(auth).cambiarClave("123", "1234", "nueva1", "nueva1"));
    }

    @Test
    void errorInesperadoMuestraElMensajeGenerico() {
        AuthFalso auth = new AuthFalso();
        auth.error = new RuntimeException("inesperado");

        assertEquals(CambiarClaveController.MENSAJE_ERROR_INESPERADO,
                new CambiarClaveController(auth).cambiarClave("123", "1234", "nueva1", "nueva1"));
    }

    @Test
    void desdeElLoginUsaElIdRecibido() {
        SessionManager.getInstancia().iniciarSesion("admin", "ADMINISTRADOR", null);
        CambiarClaveController controller = new CambiarClaveController(new AuthFalso());

        controller.setIdUsuarioObjetivo("ADMIN");

        assertEquals("ADMIN", controller.obtenerIdObjetivo());
    }

    @Test
    void desdeLaVentanaPrincipalUsaElIdDeLaSesion() {
        SessionManager.getInstancia().iniciarSesion("44444", "FUNCIONARIO", null);

        assertEquals("44444", new CambiarClaveController(new AuthFalso()).obtenerIdObjetivo());
    }

    @Test
    void elConstructorDePruebasRechazaUnServicioNulo() {
        assertThrows(NullPointerException.class, () -> new CambiarClaveController(null));
    }

    private static final class AuthFalso implements IAuthService {

        private ResultadoOperacionDTO resultado;
        private RuntimeException error;
        private String idRecibido;
        private String actualRecibida;
        private String nuevaRecibida;
        private String confirmacionRecibida;

        @Override
        public LoginResultadoDTO login(String id, String clave) {
            throw new UnsupportedOperationException("No se usa en estas pruebas.");
        }

        @Override
        public ResultadoOperacionDTO cambiarClave(String id, String actual, String nueva, String confirmacion) {
            idRecibido = id;
            actualRecibida = actual;
            nuevaRecibida = nueva;
            confirmacionRecibida = confirmacion;
            if (error != null) {
                throw error;
            }
            return resultado;
        }

        @Override
        public void logout() {
            throw new UnsupportedOperationException("No se usa en estas pruebas.");
        }
    }
}
