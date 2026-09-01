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

    /**
     * Valida y aplica un cambio de clave. Reglas del plan detallado (2.4):
     * la clave actual debe coincidir con la real, la nueva no puede estar
     * vacia, y las dos confirmaciones de la nueva clave deben ser iguales.
     */
    public ResultadoCambioClave cambiarClave(String id, String claveActual, String claveNueva, String claveNuevaConfirmacion) {

        Usuario usuario = usuarioDatos.buscarPorId(id);

        if (usuario == null) {
            return ResultadoCambioClave.fallo("El usuario no existe.");
        }

        if (claveActual == null || claveActual.isBlank()) {
            return ResultadoCambioClave.fallo("Debe ingresar la clave actual.");
        }

        if (!usuario.getClave().equals(claveActual)) {
            return ResultadoCambioClave.fallo("La clave actual es incorrecta.");
        }

        if (claveNueva == null || claveNueva.isBlank()) {
            return ResultadoCambioClave.fallo("La clave nueva no puede estar vacia.");
        }

        if (!claveNueva.equals(claveNuevaConfirmacion)) {
            return ResultadoCambioClave.fallo("Las dos claves nuevas no coinciden.");
        }

        usuarioDatos.actualizarClave(id, claveNueva);
        return ResultadoCambioClave.exito();
    }

    public static class ResultadoCambioClave {
        private final boolean exitoso;
        private final String mensajeError;

        private ResultadoCambioClave(boolean exitoso, String mensajeError) {
            this.exitoso = exitoso;
            this.mensajeError = mensajeError;
        }

        public static ResultadoCambioClave exito() {
            return new ResultadoCambioClave(true, null);
        }

        public static ResultadoCambioClave fallo(String mensajeError) {
            return new ResultadoCambioClave(false, mensajeError);
        }

        public boolean isExitoso() { return exitoso; }
        public String getMensajeError() { return mensajeError; }
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