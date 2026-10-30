package Sistema.Service;

import Sistema.DTO.LoginResultadoDTO;
import Sistema.DTO.ResultadoOperacionDTO;
import Sistema.DTO.Rol;
import Sistema.Logica.UsuarioLogica;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas de AuthService. El Service no valida (seccion 9): solo pasa las
 * solicitudes a la Logica. Por eso se prueba solo la delegacion, con una
 * UsuarioLogica falsa. Las reglas se prueban en UsuarioLogicaTest y el SQL
 * en UsuarioDatosIT.
 */
class AuthServiceTest {

    /** NOTA:
     * Fake de UsuarioLogica: responde lo que cada prueba configura y guarda
     * los parametros que recibio, para confirmar que AuthService delega bien.
     */
    static class UsuarioLogicaFake extends UsuarioLogica {

        // las respuestas que queremos que devuelva la logica
        LoginResultadoDTO loginARetornar;
        ResultadoOperacionDTO cambioARetornar;
        RuntimeException excepcionARetornar;

        // los parametros que recibio la logica
        String idRecibido;
        String claveRecibida;
        String[] parametrosCambioRecibidos;

        UsuarioLogicaFake() {
            super(null);   // no usa UsuarioDatos: todos sus metodos estan sobrescritos
        }

        @Override
        public LoginResultadoDTO iniciarSesion(String id, String clave) {
            idRecibido = id;
            claveRecibida = clave;
            if (excepcionARetornar != null) throw excepcionARetornar;
            return loginARetornar;
        }

        @Override
        public ResultadoOperacionDTO cambiarClave(String id, String claveActual, String claveNueva, String claveNuevaConfirmacion) {
            parametrosCambioRecibidos = new String[]{id, claveActual, claveNueva, claveNuevaConfirmacion};
            return cambioARetornar;
        }
    }

    // pruebas

    @Test
    void loginDelegaEnLaLogicaYDevuelveSuResultado() {
        UsuarioLogicaFake fake = new UsuarioLogicaFake();
        fake.loginARetornar = new LoginResultadoDTO("admin", Rol.ADMINISTRADOR, null);
        AuthService service = new AuthService(fake);

        LoginResultadoDTO resultado = service.login("admin", "admin");

        assertSame(fake.loginARetornar, resultado);
        assertEquals("admin", fake.idRecibido);
        assertEquals("admin", fake.claveRecibida);
    }

    @Test
    void loginPropagaLaExcepcionDeLaLogicaSinAtraparla() {
        UsuarioLogicaFake fake = new UsuarioLogicaFake();
        fake.excepcionARetornar = new IllegalArgumentException("ID o clave incorrectos.");
        AuthService service = new AuthService(fake);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.login("admin", "otra"));
        assertEquals("ID o clave incorrectos.", ex.getMessage());
    }

    @Test
    void cambiarClaveDelegaLosCuatroParametros() {
        UsuarioLogicaFake fake = new UsuarioLogicaFake();
        fake.cambioARetornar = ResultadoOperacionDTO.exito();
        AuthService service = new AuthService(fake);

        ResultadoOperacionDTO resultado = service.cambiarClave("123", "1234", "nueva", "nueva");

        assertSame(fake.cambioARetornar, resultado);
        assertArrayEquals(new String[]{"123", "1234", "nueva", "nueva"}, fake.parametrosCambioRecibidos);
    }

    @Test
    void cambiarClaveDevuelveElFalloDeLaLogicaSinCambiarlo() {
        UsuarioLogicaFake fake = new UsuarioLogicaFake();
        fake.cambioARetornar = ResultadoOperacionDTO.fallo("Las dos claves nuevas no coinciden.");
        AuthService service = new AuthService(fake);

        ResultadoOperacionDTO resultado = service.cambiarClave("123", "1234", "nueva", "otra");

        assertFalse(resultado.isExito());
        assertEquals("Las dos claves nuevas no coinciden.", resultado.getMensaje());
    }
}
