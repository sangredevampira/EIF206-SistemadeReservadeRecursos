package Sistema.Service;

import Sistema.DTO.LoginResultadoDTO;
import Sistema.DTO.ResultadoOperacionDTO;

// contrato de autenticacion. Lo implementan el AuthService del Backend y,
// en el Frontend, el proxy que envia AUTH_LOGIN, AUTH_CAMBIAR_CLAVE y AUTH_LOGOUT.

public interface IAuthService {

    LoginResultadoDTO login(String id, String clave);

    ResultadoOperacionDTO cambiarClave(String id, String actual, String nueva, String confirmacion);

    void logout();
}
