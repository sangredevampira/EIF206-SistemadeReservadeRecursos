package Sistema.Red;

import Sistema.DTO.Funcionario;
import Sistema.DTO.LoginResultadoDTO;
import Sistema.DTO.Rol;

// Datos de sesión de UNA conexión. Hay un ContextoSesion por cliente conectado.
public class ContextoSesion {

    // volatile: el hilo del cliente lo escribe y el GestorClientes lo lee desde otros hilos
    private volatile LoginResultadoDTO login;   // null = nadie ha iniciado sesión

    public boolean estaAutenticado() { return login != null; }

    public void iniciarSesion(LoginResultadoDTO resultado) { this.login = resultado; }

    public void cerrarSesion() { this.login = null; }

    public String getIdUsuario() {
        LoginResultadoDTO actual = login;
        return actual == null ? null : actual.getIdUsuario();
    }

    public Rol getRol() {
        LoginResultadoDTO actual = login;
        return actual == null ? null : actual.getRol();
    }

    public Funcionario getFuncionario() {
        LoginResultadoDTO actual = login;
        return actual == null ? null : actual.getFuncionario();
    }

    // Para operaciones solo de administrador
    public void exigirAdministrador() {
        if (getRol() != Rol.ADMINISTRADOR)
            throw new IllegalArgumentException("No tiene permiso para esta operación.");
    }
}