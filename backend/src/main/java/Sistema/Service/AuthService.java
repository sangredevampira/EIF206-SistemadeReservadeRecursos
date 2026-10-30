package Sistema.Service;

import Sistema.DTO.LoginResultadoDTO;
import Sistema.DTO.ResultadoOperacionDTO;
import Sistema.Logica.UsuarioLogica;

// Capa Service: solo pasa las solicitudes a la Logica, sin validaciones.
// implements IAuthService queda para el paso 6 (logout lo resuelve el servidor).

public class AuthService {

    private final UsuarioLogica usuarioLogica;

    public AuthService() {
        this(new UsuarioLogica());
    }

    // para las pruebas: permite usar una UsuarioLogica falsa
    public AuthService(UsuarioLogica usuarioLogica) {
        this.usuarioLogica = usuarioLogica;
    }

    // Un login fallido llega como IllegalArgumentException con el mensaje.
    // La sesion del Frontend (SessionManager) ya no se inicia aqui:
    // el Backend no conoce la interfaz. Lo hace LoginController.
    public LoginResultadoDTO login(String id, String clave) {
        return usuarioLogica.iniciarSesion(id, clave);
    }

    public ResultadoOperacionDTO cambiarClave(String id, String claveActual, String claveNueva, String claveNuevaConfirmacion) {
        return usuarioLogica.cambiarClave(id, claveActual, claveNueva, claveNuevaConfirmacion);
    }
}
