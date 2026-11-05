package Sistema.util;

import Sistema.DTO.Funcionario;

public class SessionManager {
    private static SessionManager instancia;
    private String idUsuario;
    private String rol;
    private Funcionario funcionarioAsociado;

    private SessionManager() {
    }

    public static SessionManager getInstancia() {
        if (instancia == null) {
            instancia = new SessionManager();
        }
        return instancia;
    }

    public void iniciarSesion(String idUsuario, String rol, Funcionario funcionarioAsociado) {
        this.idUsuario = idUsuario;
        this.rol = rol;
        this.funcionarioAsociado = funcionarioAsociado;
    }

    public void cerrarSesion() {
        this.idUsuario = null;
        this.rol = null;
        this.funcionarioAsociado = null;
    }

    public String getIdUsuario() {
        return idUsuario;
    }

    public String getRol() {
        return rol;
    }

    public Funcionario getFuncionarioAsociado() {
        return funcionarioAsociado;
    }

    public boolean esAdministrador() {
        return "ADMINISTRADOR".equals(rol);
    }
}