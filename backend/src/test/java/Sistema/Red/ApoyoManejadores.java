package Sistema.Red;

import Sistema.DTO.Funcionario;
import Sistema.DTO.LoginResultadoDTO;
import Sistema.DTO.Rol;
import com.fasterxml.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Apoyo comun de las pruebas de ManejadorFuncionarios, ManejadorCategorias y
 * ManejadorRecursos. Igual que ManejadorAuthTest, las pruebas pasan por el
 * DespachadorPeticiones real: asi se prueba tambien la conversion JSON <-> DTO
 * y como se responde un ResultadoOperacionDTO. Sin red ni MySQL.
 */
final class ApoyoManejadores {

    static final String SIN_PERMISO = "No tiene permiso para esta operación.";
    static final String DATOS_INVALIDOS = "Los datos de la solicitud no son válidos.";
    static final String SIN_SESION = "Debe iniciar sesión.";

    private ApoyoManejadores() {}

    static ContextoSesion administrador() {
        ContextoSesion contexto = new ContextoSesion();
        contexto.iniciarSesion(new LoginResultadoDTO("admin", Rol.ADMINISTRADOR, null));
        return contexto;
    }

    static ContextoSesion funcionario() {
        ContextoSesion contexto = new ContextoSesion();
        contexto.iniciarSesion(new LoginResultadoDTO("44444", Rol.FUNCIONARIO,
                new Funcionario("44444", "Ana Perez", "8888-9999")));
        return contexto;
    }

    static ContextoSesion sinSesion() {
        return new ContextoSesion();
    }

    // datos puede ser un DTO, un Map o null
    static MensajeRed enviar(DespachadorPeticiones despachador, String operacion, Object datos,
                             ContextoSesion contexto) {
        JsonNode nodo = (datos == null) ? null : JsonRed.aNodo(datos);
        MensajeRed solicitud = MensajeRed.solicitud(operacion, nodo);
        solicitud.setIdSolicitud("t1");
        return despachador.despachar(solicitud, contexto);
    }

    static void assertExito(MensajeRed respuesta) {
        assertEquals(Boolean.TRUE, respuesta.getExito(), () -> "error inesperado: " + respuesta.getMensaje());
        assertEquals("t1", respuesta.getIdSolicitud());
    }

    static void assertError(String mensaje, MensajeRed respuesta) {
        assertEquals(Boolean.FALSE, respuesta.getExito());
        assertEquals(mensaje, respuesta.getMensaje());
        assertEquals("t1", respuesta.getIdSolicitud());
    }

    // un exito con ResultadoOperacionDTO viaja con datos en null (lo hace el despachador)
    static void assertExitoSinDatos(MensajeRed respuesta) {
        assertExito(respuesta);
        assertTrue(respuesta.getDatos() == null || respuesta.getDatos().isNull());
    }
}
