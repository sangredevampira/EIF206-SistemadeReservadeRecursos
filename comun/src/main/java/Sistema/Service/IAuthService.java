package Sistema.Service;

import Sistema.DTO.LoginResultadoDTO;
import Sistema.DTO.ResultadoOperacionDTO;

// contrato de autenticacion. Lo implementa, en el Frontend, el proxy que envia
// AUTH_LOGIN, AUTH_CAMBIAR_CLAVE y AUTH_LOGOUT. El AuthService del Backend NO la
// implementa: no tiene logout(), porque en el servidor el logout lo hace
// ManejadorAuth anulando la sesion de la conexion (ContextoSesion).

public interface IAuthService {

    LoginResultadoDTO login(String id, String clave);

    ResultadoOperacionDTO cambiarClave(String id, String actual, String nueva, String confirmacion);

    void logout();
}
