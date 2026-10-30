package Sistema.Logica;

import Sistema.DTO.Funcionario;
import Sistema.DTO.Rol;
import Sistema.DTO.Usuario;
import Sistema.Datos.FuncionarioDatos;
import Sistema.Datos.UsuarioJsonDatos;

import java.util.List;

// Capa Logica: reglas de negocio de Funcionarios. Coordina FuncionarioDatos
// y UsuarioJsonDatos porque el enunciado exige que registrar un funcionario
// cree automaticamente su usuario asociado (clave = id, rol = FUNCIONARIO).
// TEMPORAL: sigue con los JSON hasta el paso 5 (el login ya usa MySQL).

public class FuncionarioLogica {

    private final FuncionarioDatos funcionarioDatos = new FuncionarioDatos();
    private final UsuarioJsonDatos usuarioDatos = new UsuarioJsonDatos();

    // Dependencia directa a ReservaLogica (no a ReservaDatos): a diferencia
    // del caso de CategoriaLogica->RecursoDatos, aca sí conviene pasar por
    // la Logica de Kris porque "es una reserva futura" es una regla de
    // negocio suya (fecha + estado), no una simple lectura de archivo.
    private final ReservaLogica reservaLogica = new ReservaLogica();

    /**
     * Proceso exacto del plan (2.5):
     * 1. ID no exista. 2. Nombre y telefono validos. 3-4. Crea funcionario
     * y su usuario asociado. 5-6. Clave = id, rol = FUNCIONARIO.
     */
    public ResultadoOperacion registrar(Funcionario funcionario) {

        if (funcionario.getId() == null || funcionario.getId().isBlank()) {
            return ResultadoOperacion.fallo("El ID no puede estar vacio.");
        }
        if (funcionarioDatos.buscarPorId(funcionario.getId()) != null) {
            return ResultadoOperacion.fallo("Ya existe un funcionario con ese ID.");
        }
        if (usuarioDatos.buscarPorId(funcionario.getId()) != null) {
            return ResultadoOperacion.fallo("Ya existe un usuario con ese ID (por ejemplo, el administrador).");
        }
        if (funcionario.getNombre() == null || funcionario.getNombre().isBlank()) {
            return ResultadoOperacion.fallo("El nombre no puede estar vacio.");
        }
        if (funcionario.getTelefono() == null || funcionario.getTelefono().isBlank()) {
            return ResultadoOperacion.fallo("El telefono no puede estar vacio.");
        }

        funcionarioDatos.guardar(funcionario);

        Usuario usuarioAsociado = new Usuario(funcionario.getId(), funcionario.getId(), Rol.FUNCIONARIO);
        usuarioDatos.guardar(usuarioAsociado);

        return ResultadoOperacion.exito();
    }

    public ResultadoOperacion modificar(Funcionario funcionario) {

        if (funcionarioDatos.buscarPorId(funcionario.getId()) == null) {
            return ResultadoOperacion.fallo("El funcionario no existe.");
        }
        if (funcionario.getNombre() == null || funcionario.getNombre().isBlank()) {
            return ResultadoOperacion.fallo("El nombre no puede estar vacio.");
        }
        if (funcionario.getTelefono() == null || funcionario.getTelefono().isBlank()) {
            return ResultadoOperacion.fallo("El telefono no puede estar vacio.");
        }

        funcionarioDatos.modificar(funcionario);
        return ResultadoOperacion.exito();
    }

    /**
     * Regla del plan: no se puede eliminar un funcionario con reservas
     * futuras. Pendiente real: el modulo de Reservas (de Kris) todavia
     * no existe, asi que por ahora no se puede validar esta parte.
     * Cuando exista ReservaLogica/ReservaDatos, agregar aca la consulta
     * antes de permitir el borrado.
     *
     * Ademas de borrar el Funcionario, se borra su Usuario asociado:
     * la relacion es de composicion ("un funcionario tiene un usuario"),
     * asi que si el funcionario desaparece, su cuenta de acceso no debe
     * seguir existiendo (si no, el ID borrado podria seguir haciendo
     * login indefinidamente).
     */
    public ResultadoOperacion eliminar(String id) {

        if (funcionarioDatos.buscarPorId(id) == null) {
            return ResultadoOperacion.fallo("El funcionario no existe.");
        }

        if (reservaLogica.tieneReservasFuturas(id)) {
            return ResultadoOperacion.fallo(
                    "No se puede eliminar el funcionario porque tiene reservas futuras registradas.");
        }

        funcionarioDatos.eliminar(id);
        usuarioDatos.eliminar(id);
        return ResultadoOperacion.exito();
    }

    // La busqueda no puede "fallar" en el sentido de negocio (una lista
    // vacia es un resultado valido), por eso no usa ResultadoOperacion.
    public List<Funcionario> buscar(String idParcial, String nombreParcial) {
        return funcionarioDatos.buscar(idParcial, nombreParcial);
    }

    public List<Funcionario> listarTodos() {
        return funcionarioDatos.listarTodos();
    }

    public static class ResultadoOperacion {
        private final boolean exitoso;
        private final String mensajeError;

        private ResultadoOperacion(boolean exitoso, String mensajeError) {
            this.exitoso = exitoso;
            this.mensajeError = mensajeError;
        }

        public static ResultadoOperacion exito() {
            return new ResultadoOperacion(true, null);
        }

        public static ResultadoOperacion fallo(String mensajeError) {
            return new ResultadoOperacion(false, mensajeError);
        }

        public boolean isExitoso() { return exitoso; }
        public String getMensajeError() { return mensajeError; }
    }
}
