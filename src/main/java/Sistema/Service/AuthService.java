package Sistema.Service;

import Sistema.Logica.UsuarioLogica;
import Sistema.util.SessionManager;

public class AuthService {

    private final UsuarioLogica usuarioLogica = new UsuarioLogica();

    public UsuarioLogica.ResultadoLogin login(String id, String clave) {
        UsuarioLogica.ResultadoLogin resultado = usuarioLogica.iniciarSesion(id, clave);

        if (resultado.isExitoso()) {
            SessionManager.getInstancia().iniciarSesion(
                    resultado.getUsuario().getId(),
                    resultado.getUsuario().getRol().name(),
                    resultado.getFuncionarioAsociado()
            );
        }

        return resultado;
    }
}
