package Sistema.DTO;

// datos de un login exitoso. Nunca lleva la clave ni el hash: por eso no
// guarda el Usuario completo, solo su id y su rol.
// funcionario es null para el administrador.
// Un login fallido no usa esta clase: exito=false y el mensaje viajan en el
// mensaje de red, y el proxy lo convierte en IllegalArgumentException.

public class LoginResultadoDTO {
    private String idUsuario;
    private Rol rol;
    private Funcionario funcionario;

    public LoginResultadoDTO() {}

    public LoginResultadoDTO(String idUsuario, Rol rol, Funcionario funcionario) {
        this.idUsuario = idUsuario;
        this.rol = rol;
        this.funcionario = funcionario;
    }

    public String getIdUsuario() { return idUsuario; }
    public void setIdUsuario(String idUsuario) { this.idUsuario = idUsuario; }
    public Rol getRol() { return rol; }
    public void setRol(Rol rol) { this.rol = rol; }
    public Funcionario getFuncionario() { return funcionario; }
    public void setFuncionario(Funcionario funcionario) { this.funcionario = funcionario; }
}
