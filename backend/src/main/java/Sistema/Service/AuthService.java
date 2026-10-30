package Sistema.Service;

import Sistema.Logica.UsuarioLogica;

public class AuthService {

    private final UsuarioLogica usuarioLogica = new UsuarioLogica();

    public UsuarioLogica.ResultadoLogin login(String id, String clave) {
        // La sesion del Frontend (SessionManager) ya no se inicia aqui:
        // el Backend no conoce la interfaz. Ahora lo hace LoginController.
        return usuarioLogica.iniciarSesion(id, clave);
    }

    public UsuarioLogica.ResultadoCambioClave cambiarClave(String id, String claveActual, String claveNueva, String claveNuevaConfirmacion) {
        return usuarioLogica.cambiarClave(id, claveActual, claveNueva, claveNuevaConfirmacion);
    }
}