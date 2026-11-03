package Sistema.Service.Red;

import Sistema.DTO.LoginResultadoDTO;
import Sistema.DTO.ResultadoOperacionDTO;
import Sistema.DTO.Rol;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static Sistema.Service.Red.ServidorFalso.JSON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProxyAuthServiceTest {

    private final ServidorFalso servidor = new ServidorFalso();

    @BeforeEach
    void antes() {
        servidor.cerrarCliente();
    }

    @AfterEach
    void despues() {
        servidor.cerrarCliente();
    }

    // --- login ---

    @Test
    void loginEnviaAuthLoginConIdUsuarioYClave() throws Exception {
        servidor.conExito(respuestaAdministrador(), () -> new ProxyAuthService().login("admin", "secreta"));

        JsonNode solicitud = servidor.solicitudRecibida();
        assertEquals("SOLICITUD", solicitud.path("tipo").asText());
        assertFalse(solicitud.path("idSolicitud").asText().isBlank());
        assertEquals("AUTH_LOGIN", servidor.operacionRecibida());

        JsonNode datos = servidor.datosRecibidos();
        assertEquals("admin", datos.path("idUsuario").asText());
        assertEquals("secreta", datos.path("clave").asText());
        assertEquals(2, datos.size());
    }

    @Test
    void loginConvierteAdministradorSinFuncionario() throws Exception {
        LoginResultadoDTO resultado = servidor.conExito(respuestaAdministrador(),
                () -> new ProxyAuthService().login("admin", "admin"));

        assertEquals("admin", resultado.getIdUsuario());
        assertEquals(Rol.ADMINISTRADOR, resultado.getRol());
        assertNull(resultado.getFuncionario());
    }

    @Test
    void loginConvierteFuncionarioConSusDatos() throws Exception {
        ObjectNode respuesta = JSON.createObjectNode()
                .put("idUsuario", "44444")
                .put("rol", "FUNCIONARIO");
        respuesta.putObject("funcionario")
                .put("id", "44444")
                .put("nombre", "Kristel")
                .put("telefono", "8888-8888");

        LoginResultadoDTO resultado = servidor.conExito(respuesta,
                () -> new ProxyAuthService().login("44444", "44444"));

        assertEquals(Rol.FUNCIONARIO, resultado.getRol());
        assertNotNull(resultado.getFuncionario());
        assertEquals("Kristel", resultado.getFuncionario().getNombre());
        assertEquals("8888-8888", resultado.getFuncionario().getTelefono());
    }

    @Test
    void loginFallidoLanzaIllegalArgumentConElMensajeDelServidor() throws Exception {
        IllegalArgumentException error = servidor.conFallo("ID o clave incorrectos.",
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ProxyAuthService().login("admin", "mala")));

        assertEquals("ID o clave incorrectos.", error.getMessage());
    }

    @Test
    void loginConCampoNullLoOmite() throws Exception {
        servidor.conFallo("Los datos de la solicitud no son válidos.",
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ProxyAuthService().login(null, "clave")));

        JsonNode datos = servidor.datosRecibidos();
        assertFalse(datos.has("idUsuario"));
        assertEquals("clave", datos.path("clave").asText());
    }

    @Test
    void loginConDatosNullEsRespuestaInvalida() throws Exception {
        IllegalStateException error = servidor.conExito(null,
                () -> assertThrows(IllegalStateException.class,
                        () -> new ProxyAuthService().login("admin", "admin")));

        assertTrue(error.getMessage().contains("inicio de sesión"));
    }

    @Test
    void loginSinServidorLanzaIllegalState() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new ProxyAuthService().login("admin", "admin"));

        assertEquals("No fue posible comunicarse con el servidor.", error.getMessage());
    }

    // --- cambiarClave ---

    @Test
    void cambiarClaveEnviaLosCuatroCamposDelContrato() throws Exception {
        servidor.conExito(null,
                () -> new ProxyAuthService().cambiarClave("123", "1234", "nueva", "nueva"));

        assertEquals("AUTH_CAMBIAR_CLAVE", servidor.operacionRecibida());
        JsonNode datos = servidor.datosRecibidos();
        assertEquals("123", datos.path("idUsuario").asText());
        assertEquals("1234", datos.path("claveActual").asText());
        assertEquals("nueva", datos.path("claveNueva").asText());
        assertEquals("nueva", datos.path("confirmacion").asText());
    }

    @Test
    void cambiarClaveExitosaDevuelveExitoSinMensaje() throws Exception {
        ResultadoOperacionDTO resultado = servidor.conExito(null,
                () -> new ProxyAuthService().cambiarClave("123", "1234", "nueva", "nueva"));

        assertTrue(resultado.isExito());
        assertNull(resultado.getMensaje());
    }

    @Test
    void cambiarClaveRechazadaDevuelveFalloConElMensaje() throws Exception {
        ResultadoOperacionDTO resultado = servidor.conFallo("ID o clave actual incorrectos.",
                () -> new ProxyAuthService().cambiarClave("123", "mala", "nueva", "nueva"));

        assertFalse(resultado.isExito());
        assertEquals("ID o clave actual incorrectos.", resultado.getMensaje());
    }

    @Test
    void cambiarClaveSinServidorDejaPasarIllegalState() {
        assertThrows(IllegalStateException.class,
                () -> new ProxyAuthService().cambiarClave("123", "1234", "nueva", "nueva"));
    }

    // --- logout ---

    @Test
    void logoutEnviaAuthLogoutSinDatos() throws Exception {
        servidor.conExito(null, () -> {
            new ProxyAuthService().logout();
            return null;
        });

        assertEquals("AUTH_LOGOUT", servidor.operacionRecibida());
        assertTrue(servidor.datosRecibidos().isNull());
    }

    @Test
    void logoutRechazadoDejaPasarLaExcepcion() throws Exception {
        IllegalArgumentException error = servidor.conFallo("Debe iniciar sesión.",
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ProxyAuthService().logout()));

        assertEquals("Debe iniciar sesión.", error.getMessage());
    }

    @Test
    void logoutSinServidorLanzaIllegalState() {
        assertThrows(IllegalStateException.class, () -> new ProxyAuthService().logout());
    }

    private static ObjectNode respuestaAdministrador() {
        ObjectNode respuesta = JSON.createObjectNode()
                .put("idUsuario", "admin")
                .put("rol", "ADMINISTRADOR");
        respuesta.putNull("funcionario");
        return respuesta;
    }
}
