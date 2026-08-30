package Sistema.Logica;

import Sistema.DTO.Usuario;
import Sistema.Datos.UsuarioDatos;

//decide si un intento de login es valido, campos no vacios,  si el usuario existe, clave coincide etc..
//pq estas validaciones aca y no en UsuarioDatos? pq eso son reglas de negocio, no un detalle de acceso a archivo

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
        private final String mensajeError;

        private ResultadoLogin(boolean exitoso, Usuario usuario, String mensajeError) {
            this.exitoso = exitoso;
            this.usuario = usuario;
            this.mensajeError = mensajeError;
        }

        public static ResultadoLogin exito(Usuario usuario) {
            return new ResultadoLogin(true, usuario, null);
        }

        public static ResultadoLogin fallo(String mensajeError) {
            return new ResultadoLogin(false, null, mensajeError);
        }

        public boolean isExitoso() { return exitoso; }
        public Usuario getUsuario() { return usuario; }
        public String getMensajeError() { return mensajeError; }
    }
}
