package Sistema.Logica;

import Sistema.DTO.Funcionario;
import Sistema.DTO.LoginResultadoDTO;
import Sistema.DTO.ResultadoOperacionDTO;
import Sistema.DTO.Rol;
import Sistema.Datos.UsuarioDatos;
import Sistema.Datos.UsuarioRegistro;

/**
 * Reglas del login y del cambio de clave (claves con HashClave).
 *
 * Mensajes: "no existe" y "clave incorrecta" dan el MISMO mensaje, para no
 * revelar que IDs existen. Si el usuario no existe igual se calcula un hash
 * (contra HASH_FICTICIO), para que la respuesta tarde lo mismo. "Inactivo"
 * tiene mensaje propio, pero solo despues de verificar la clave: solo lo ve
 * el dueno de la cuenta.
 */
public class UsuarioLogica {

    static final String MENSAJE_CREDENCIALES = "ID o clave incorrectos.";
    static final String MENSAJE_CREDENCIALES_CAMBIO = "ID o clave actual incorrectos.";
    static final String MENSAJE_INACTIVO = "Su usuario está inactivo. Contacte al administrador.";

    // mismas iteraciones que un hash real, para que verificarlo tarde lo mismo
    private static final String HASH_FICTICIO = HashClave.generar("clave-ficticia-para-igualar-el-tiempo");

    private final UsuarioDatos usuarioDatos;

    public UsuarioLogica() {
        this(new UsuarioDatos());
    }

    // para las pruebas: permite usar un UsuarioDatos falso sin MySQL
    public UsuarioLogica(UsuarioDatos usuarioDatos) {
        this.usuarioDatos = usuarioDatos;
    }

    /**
     * Devuelve los datos de la sesion o lanza IllegalArgumentException con un
     * mensaje legible. Nunca devuelve la clave ni el hash. El ID devuelto es
     * el que esta guardado en la base, no el que escribio el usuario.
     */
    public LoginResultadoDTO iniciarSesion(String id, String clave) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Debe ingresar el ID.");
        }
        if (clave == null || clave.isBlank()) {
            throw new IllegalArgumentException("Debe ingresar la clave.");
        }

        UsuarioRegistro usuario = usuarioDatos.buscarPorId(id.trim());
        if (!claveCorrecta(usuario, clave)) {
            throw new IllegalArgumentException(MENSAJE_CREDENCIALES);
        }
        if (!usuario.activo()) {
            throw new IllegalArgumentException(MENSAJE_INACTIVO);
        }

        Funcionario funcionario = null;   // el administrador no tiene funcionario
        if (usuario.rol() == Rol.FUNCIONARIO) {
            funcionario = usuarioDatos.buscarFuncionarioAsociado(usuario.id());
            if (funcionario == null) {
                throw new IllegalArgumentException(
                        "El usuario no tiene datos de funcionario. Contacte al administrador.");
            }
        }
        return new LoginResultadoDTO(usuario.id(), usuario.rol(), funcionario);
    }

    /**
     * Reglas: la clave actual debe coincidir, la nueva no puede estar vacia y
     * las dos confirmaciones deben ser iguales. Se permite sin sesion (desde
     * la pantalla de login) porque se valida la clave actual.
     */
    public ResultadoOperacionDTO cambiarClave(String id, String claveActual, String claveNueva, String claveNuevaConfirmacion) {
        // primero lo que no necesita la base
        if (id == null || id.isBlank()) {
            return ResultadoOperacionDTO.fallo("Debe indicar el usuario.");
        }
        if (claveActual == null || claveActual.isBlank()) {
            return ResultadoOperacionDTO.fallo("Debe ingresar la clave actual.");
        }
        if (claveNueva == null || claveNueva.isBlank()) {
            return ResultadoOperacionDTO.fallo("La clave nueva no puede estar vacía.");
        }
        if (!claveNueva.equals(claveNuevaConfirmacion)) {
            return ResultadoOperacionDTO.fallo("Las dos claves nuevas no coinciden.");
        }

        UsuarioRegistro usuario = usuarioDatos.buscarPorId(id.trim());
        if (!claveCorrecta(usuario, claveActual)) {
            return ResultadoOperacionDTO.fallo(MENSAJE_CREDENCIALES_CAMBIO);
        }
        if (!usuario.activo()) {
            return ResultadoOperacionDTO.fallo(MENSAJE_INACTIVO);
        }

        if (!usuarioDatos.actualizarClaveHash(usuario.id(), HashClave.generar(claveNueva))) {
            return ResultadoOperacionDTO.fallo("No se pudo actualizar la clave. Intente de nuevo.");
        }
        return ResultadoOperacionDTO.exito();
    }

    // si el usuario no existe se verifica contra HASH_FICTICIO: tarda lo mismo
    // que una clave incorrecta y asi el tiempo no revela que IDs existen
    private static boolean claveCorrecta(UsuarioRegistro usuario, String clave) {
        if (usuario == null) {
            HashClave.verificar(clave, HASH_FICTICIO);
            return false;
        }
        return HashClave.verificar(clave, usuario.claveHash());
    }
}
