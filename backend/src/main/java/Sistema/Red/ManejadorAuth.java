package Sistema.Red;

import Sistema.DTO.LoginResultadoDTO;
import Sistema.Service.AuthService;
import com.fasterxml.jackson.databind.JsonNode;

// Operaciones de sesión: AUTH_LOGIN, AUTH_CAMBIAR_CLAVE y AUTH_LOGOUT.
// Es el único manejador que toca la sesión de la conexión (ContextoSesion).
public class ManejadorAuth {

    private final AuthService authService;

    public ManejadorAuth(AuthService authService) {
        this.authService = authService;
    }

    // Registra sus tres operaciones en el despachador
    public void registrarEn(DespachadorPeticiones despachador) {
        despachador.registrar(Operaciones.AUTH_LOGIN, this::login);
        despachador.registrar(Operaciones.AUTH_CAMBIAR_CLAVE, this::cambiarClave);
        despachador.registrar(Operaciones.AUTH_LOGOUT, this::logout);
    }

    private Object login(JsonNode datos, ContextoSesion contexto) {
        String id = texto(datos, "idUsuario");
        String clave = texto(datos, "clave");
        // si el login falla, authService lanza IllegalArgumentException y la sesión no cambia
        LoginResultadoDTO resultado = authService.login(id, clave);
        contexto.iniciarSesion(resultado);
        return resultado;
    }

    // Se permite sin sesión (lo decide el despachador); la clave actual se valida en la Lógica
    private Object cambiarClave(JsonNode datos, ContextoSesion contexto) {
        return authService.cambiarClave(
                texto(datos, "idUsuario"),
                texto(datos, "claveActual"),
                texto(datos, "claveNueva"),
                texto(datos, "confirmacion"));
    }

    // El socket queda abierto: solo se anula la sesión y la conexión deja de recibir alertas
    private Object logout(JsonNode datos, ContextoSesion contexto) {
        contexto.cerrarSesion();
        return null;
    }

    // Lee un campo de texto de "datos"; si falta, la solicitud no es válida
    private String texto(JsonNode datos, String campo) {
        JsonNode valor = (datos == null) ? null : datos.get(campo);
        if (valor == null || valor.isNull()) {
            throw new IllegalArgumentException("Los datos de la solicitud no son válidos.");
        }
        return valor.asText();
    }
}