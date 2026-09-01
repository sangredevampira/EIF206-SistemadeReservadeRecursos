package Sistema.Logica;

import Sistema.DTO.Funcionario;
import Sistema.DTO.Rol;
import Sistema.DTO.Usuario;
import Sistema.Datos.UsuarioDatos;

public class UsuarioLogica {

    private final UsuarioDatos usuarioDatos = new UsuarioDatos();

    public ResultadoLogin iniciarSesion(String id, String clave) {

        if (id == null || id.isBlank()) {
            return ResultadoLogin.fallo("El ID no puede estar vacio.");
        }
        if (clave == null || clave.isBlank()) {
            return ResultadoLogin.fallo("La clave no puede estar vacia.");
        }

        Usuario usuario = usuarioDatos.buscarPorId(id);

        if (usuario == null) {
            return ResultadoLogin.fallo("El usuario ingresado no existe.");
        }

        if (!usuario.getClave().equals(clave)) {
            return ResultadoLogin.fallo("La clave ingresada es incorrecta.");
        }

        return ResultadoLogin.exito(usuario);
    }

    public static class ResultadoLogin {
        private final boolean exitoso;
        private final Usuario usuario;
        private final Funcionario funcionarioAsociado;
        private final String mensajeError;

        private ResultadoLogin(boolean exitoso, Usuario usuario, Funcionario funcionarioAsociado, String mensajeError) {
            this.exitoso = exitoso;
            this.usuario = usuario;
            this.funcionarioAsociado = funcionarioAsociado;
            this.mensajeError = mensajeError;
        }

        public static ResultadoLogin exito(Usuario usuario) {
            Funcionario funcionarioAsociado = null;
            if (usuario.getRol() == Rol.FUNCIONARIO) {
                // FuncionarioDatos aun no existe (Fase E). Placeholder con el ID real;
                // cuando exista, reemplazar por: funcionarioDatos.buscarPorId(usuario.getId())
                funcionarioAsociado = new Funcionario(usuario.getId(), null, null);
            }
            return new ResultadoLogin(true, usuario, funcionarioAsociado, null);
        }

        public static ResultadoLogin fallo(String mensajeError) {
            return new ResultadoLogin(false, null, null, mensajeError);
        }

        public boolean isExitoso() { return exitoso; }
        public Usuario getUsuario() { return usuario; }
        public Funcionario getFuncionarioAsociado() { return funcionarioAsociado; }
        public String getMensajeError() { return mensajeError; }
    }
}