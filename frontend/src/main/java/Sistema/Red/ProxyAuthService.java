package Sistema.Service.Red;

import Sistema.DTO.LoginResultadoDTO;
import Sistema.DTO.ResultadoOperacionDTO;
import Sistema.Red.Operaciones;
import Sistema.Red.ProxyBase;
import Sistema.Service.IAuthService;

/**
 * Representa en el Frontend la autenticacion del Backend (AUTH_LOGIN,
 * AUTH_CAMBIAR_CLAVE y AUTH_LOGOUT; docs/00_contratos.md, seccion 3). No tiene
 * reglas de negocio: manda cada pedido por la red y devuelve el resultado.
 * En el servidor lo atiende ManejadorAuth (de Kristel), que guarda la sesion
 * de la conexion en ContextoSesion.
 */
public class ProxyAuthService extends ProxyBase implements IAuthService {

    // Un login fallido llega como IllegalArgumentException con el mensaje del
    // servidor ("ID o clave incorrectos.", etc.), igual que con el AuthService
    // del Backend: el proxy la deja pasar.
    @Override
    public LoginResultadoDTO login(String id, String clave) {
        LoginResultadoDTO resultado = ejecutar(Operaciones.AUTH_LOGIN,
                ApoyoProxies.datos("idUsuario", id, "clave", clave),
                LoginResultadoDTO.class);
        return ApoyoProxies.obligatorio(resultado, "los datos del inicio de sesión");
    }

    // Se permite sin sesion (desde la pantalla de login): el servidor valida la clave actual.
    @Override
    public ResultadoOperacionDTO cambiarClave(String id, String actual, String nueva, String confirmacion) {
        return ApoyoProxies.resultado(() -> ejecutar(Operaciones.AUTH_CAMBIAR_CLAVE,
                ApoyoProxies.datos(
                        "idUsuario", id,
                        "claveActual", actual,
                        "claveNueva", nueva,
                        "confirmacion", confirmacion)));
    }

    /**
     * Anula la sesion en el servidor; el socket sigue abierto y la conexion deja
     * de recibir alertas. Si falla, la excepcion se deja pasar (IllegalStateException
     * si no hay conexion, IllegalArgumentException si el servidor lo rechaza).
     *
     * IMPORTANTE para quien lo llame (MainController, de Sofia): debe limpiar la
     * sesion local (SessionManager) y volver al login AUNQUE logout() falle, por
     * ejemplo si el servidor esta apagado; si no, el usuario quedaria atrapado
     * dentro de la aplicacion.
     */
    @Override
    public void logout() {
        ejecutar(Operaciones.AUTH_LOGOUT, null);
    }
}
