package Sistema.util;

public class SessionManager {
    private static SessionManager instancia;
    private String idUsuario;
    private String rol;

    private SessionManager() {
    }

    public static SessionManager getInstancia() {
        if (instancia == null) {
            instancia = new SessionManager();
        }
        return instancia;
    }

    public void iniciarSesion(String idUsuario, String rol) {
        this.idUsuario = idUsuario;
        this.rol = rol;
    }

    public void cerrarSesion() {
        this.idUsuario = null;
        this.rol = null;
    }

    public String getIdUsuario() {
        return idUsuario;
    }

    public String getRol() {
        return rol;
    }

    public boolean esAdministrador() {
        return "ADMINISTRADOR".equals(rol);
    }
}
