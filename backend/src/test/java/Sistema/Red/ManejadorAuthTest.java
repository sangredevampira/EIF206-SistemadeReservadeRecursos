package Sistema.Red;

import Sistema.DTO.LoginResultadoDTO;
import Sistema.DTO.ResultadoOperacionDTO;
import Sistema.DTO.Rol;
import Sistema.Service.AuthService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ManejadorAuthTest {

    private DespachadorPeticiones despachador;
    private ContextoSesion contexto;
    private ResultadoOperacionDTO resultadoCambio = ResultadoOperacionDTO.exito();
    private String[] ultimoCambio;   // lo que recibió cambiarClave del manejador

    @BeforeEach
    void preparar() {
        // AuthService falso: con null no toca MySQL porque se sobrescriben sus dos métodos
        AuthService falso = new AuthService(null) {
            @Override
            public LoginResultadoDTO login(String id, String clave) {
                if ("u1".equals(id) && "c1".equals(clave)) {
                    return new LoginResultadoDTO("u1", Rol.FUNCIONARIO, null);
                }
                throw new IllegalArgumentException("ID o clave incorrectos.");
            }

            @Override
            public ResultadoOperacionDTO cambiarClave(String id, String actual, String nueva, String confirmacion) {
                ultimoCambio = new String[]{id, actual, nueva, confirmacion};
                return resultadoCambio;
            }
        };
        despachador = new DespachadorPeticiones();
        new ManejadorAuth(falso).registrarEn(despachador);
        contexto = new ContextoSesion();
    }

    @Test
    void loginCorrectoGuardaLaSesionYDevuelveElResultado() throws Exception {
        MensajeRed respuesta = enviar(Operaciones.AUTH_LOGIN, credenciales("u1", "c1"));

        assertTrue(exito(respuesta));
        assertTrue(contexto.estaAutenticado());
        assertEquals("u1", contexto.getIdUsuario());
        assertEquals("u1", respuesta.getDatos().get("idUsuario").asText());
        assertEquals("FUNCIONARIO", respuesta.getDatos().get("rol").asText());
    }

    @Test
    void loginFallidoDaErrorConSuMensajeYNoIniciaSesion() throws Exception {
        MensajeRed respuesta = enviar(Operaciones.AUTH_LOGIN, credenciales("u1", "mala"));

        assertFalse(exito(respuesta));
        assertEquals("ID o clave incorrectos.", respuesta.getMensaje());
        assertFalse(contexto.estaAutenticado());
    }

    @Test
    void loginSinDatosOConUnCampoFaltanteDaError() throws Exception {
        MensajeRed sinDatos = enviar(Operaciones.AUTH_LOGIN, null);
        assertFalse(exito(sinDatos));
        assertEquals("Los datos de la solicitud no son válidos.", sinDatos.getMensaje());

        MensajeRed sinClave = enviar(Operaciones.AUTH_LOGIN, JsonRed.aNodo(Map.of("idUsuario", "u1")));
        assertFalse(exito(sinClave));
        assertEquals("Los datos de la solicitud no son válidos.", sinClave.getMensaje());
        assertFalse(contexto.estaAutenticado());
    }

    @Test
    void cambiarClaveFuncionaSinSesionYPasaLosCuatroCamposEnOrden() throws Exception {
        MensajeRed respuesta = enviar(Operaciones.AUTH_CAMBIAR_CLAVE, JsonRed.aNodo(Map.of(
                "idUsuario", "u1", "claveActual", "c1", "claveNueva", "n1", "confirmacion", "n2")));

        assertTrue(exito(respuesta));
        assertArrayEquals(new String[]{"u1", "c1", "n1", "n2"}, ultimoCambio);
    }

    @Test
    void cambiarClaveFallidoDevuelveElMensajeDeLaLogica() throws Exception {
        resultadoCambio = ResultadoOperacionDTO.fallo("La clave actual es incorrecta.");

        MensajeRed respuesta = enviar(Operaciones.AUTH_CAMBIAR_CLAVE, JsonRed.aNodo(Map.of(
                "idUsuario", "u1", "claveActual", "x", "claveNueva", "n1", "confirmacion", "n1")));

        assertFalse(exito(respuesta));
        assertEquals("La clave actual es incorrecta.", respuesta.getMensaje());
    }

    @Test
    void logoutCierraLaSesionYDespuesYaNoSeAtiende() throws Exception {
        enviar(Operaciones.AUTH_LOGIN, credenciales("u1", "c1"));
        assertTrue(contexto.estaAutenticado());

        MensajeRed respuesta = enviar(Operaciones.AUTH_LOGOUT, null);
        assertTrue(exito(respuesta));
        assertFalse(contexto.estaAutenticado());

        // sin sesión, ya no se acepta otro logout
        MensajeRed rechazo = enviar(Operaciones.AUTH_LOGOUT, null);
        assertFalse(exito(rechazo));
        assertEquals("Debe iniciar sesión.", rechazo.getMensaje());
    }

    // ---------- apoyo ----------

    private MensajeRed enviar(String operacion, JsonNode datos) {
        MensajeRed solicitud = MensajeRed.solicitud(operacion, datos);
        solicitud.setIdSolicitud("t1");
        return despachador.despachar(solicitud, contexto);
    }

    private JsonNode credenciales(String id, String clave) throws Exception {
        return JsonRed.aNodo(Map.of("idUsuario", id, "clave", clave));
    }

    private boolean exito(MensajeRed mensaje) {
        return Boolean.TRUE.equals(mensaje.getExito());
    }
}
