package Sistema.Service;

import Sistema.DTO.Usuario;
import Sistema.Logica.UsuarioLogica;
import Sistema.util.SessionManager;

//puente entre controller y logica. reenvia el login y si tuvo exito, avisa a sessionManager.
//no valida nada pq esta capa nunca valida, solo reenvia la info

public class AuthService {

    private final UsuarioLogica usuarioLogica = new UsuarioLogica();

    public UsuarioLogica.ResultadoLogin login(String id, String clave) {
        UsuarioLogica.ResultadoLogin resultado = usuarioLogica.iniciarSesion(id, clave);

        if (resultado.isExitoso()) {
            Usuario usuario = resultado.getUsuario();
            SessionManager.getInstancia().iniciarSesion(usuario.getId(), usuario.getRol().name());
        }

        return resultado;
    }
}
