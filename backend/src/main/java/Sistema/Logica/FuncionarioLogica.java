package Sistema.Logica;

import Sistema.DTO.Funcionario;
import Sistema.DTO.ResultadoOperacionDTO;
import Sistema.DTO.Rol;
import Sistema.Datos.ConexionBD;
import Sistema.Datos.FuenteConexion;
import Sistema.Datos.FuncionarioDatos;
import Sistema.Datos.UsuarioDatos;
import Sistema.Datos.UsuarioRegistro;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Reglas de negocio de funcionarios (paso 5c, con MySQL).
 *
 * Usuario y funcionario comparten el ID (decision 1): registrar crea los dos
 * en UNA transaccion (clave inicial = ID, rol FUNCIONARIO) y eliminar es una
 * baja logica de los dos (activo = 0), tambien en una transaccion.
 *
 * registrar, modificar y eliminar devuelven siempre un ResultadoOperacionDTO:
 * exito, o fallo con un mensaje legible. Un error de la base se responde como
 * fallo(MENSAJE_ERROR_BD), sin detalles de SQL (contrato, seccion 2.2).
 *
 * Candados:
 *  - registrar NO usa candado: el ID lo escribe el administrador (no hay
 *    generador ni SELECT ... FOR UPDATE) y la PK de usuario es la ultima
 *    barrera. Si dos registran el mismo ID a la vez, el segundo INSERT espera
 *    al primero y recibe el error 1062, que se traduce a un mensaje claro.
 *  - eliminar usa ReservaLogica.CANDADO_RESERVAS (contrato, regla 5.1): "no
 *    tiene reservas futuras -> desactivar" son dos pasos y ninguna reserva
 *    nueva debe entrar entre los dos. Primero el candado y despues la
 *    conexion, igual que RecursoLogica.eliminarRecurso.
 */
public class FuncionarioLogica {

    // codigos de error de MySQL que se traducen a mensajes para el usuario
    static final int ERROR_DUPLICADO = 1062;          // PK de usuario o de funcionario
    static final int SIN_CODIGO_CONOCIDO = 0;

    // largos de las columnas (bd/01_schema.sql)
    static final int LARGO_MAXIMO_ID = 20;            // VARCHAR(20)
    static final int LARGO_MAXIMO_NOMBRE = 100;       // VARCHAR(100)
    static final int LARGO_MAXIMO_TELEFONO = 20;      // VARCHAR(20)

    static final String MENSAJE_NULO = "Debe indicar el funcionario.";
    static final String MENSAJE_ID_VACIO = "El ID no puede estar vacio.";
    static final String MENSAJE_ID_LARGO = "El ID no puede tener mas de " + LARGO_MAXIMO_ID + " caracteres.";
    static final String MENSAJE_NOMBRE_VACIO = "El nombre no puede estar vacio.";
    static final String MENSAJE_NOMBRE_LARGO = "El nombre no puede tener mas de " + LARGO_MAXIMO_NOMBRE + " caracteres.";
    static final String MENSAJE_TELEFONO_VACIO = "El telefono no puede estar vacio.";
    static final String MENSAJE_TELEFONO_LARGO = "El telefono no puede tener mas de " + LARGO_MAXIMO_TELEFONO + " caracteres.";
    static final String MENSAJE_ID_USUARIO = "Ya existe un usuario con ese ID (por ejemplo, el administrador).";
    static final String MENSAJE_ID_FUNCIONARIO = "Ya existe un funcionario con ese ID.";
    static final String MENSAJE_ID_DADO_DE_BAJA =
            "Ya existe un funcionario con ese ID, dado de baja. No se puede reutilizar el ID.";
    static final String MENSAJE_ID_REPETIDO =
            "Ya existe un usuario o funcionario con ese ID (puede estar dado de baja).";
    static final String MENSAJE_NO_EXISTE = "El funcionario no existe o fue dado de baja.";
    static final String MENSAJE_RESERVAS_FUTURAS =
            "No se puede eliminar el funcionario porque tiene reservas futuras activas.";

    private final FuncionarioDatos funcionarioDatos;
    private final UsuarioDatos usuarioDatos;

    // Dependencia a ReservaLogica (no a ReservaDatos): "tiene reservas futuras"
    // es una regla de Kristel (fecha + estado). Se llama con la misma conexion.
    private final ReservaLogica reservaLogica;

    private final FuenteConexion fuenteConexion;

    // sin E/S: los constructores de Datos y de ReservaLogica no abren nada
    public FuncionarioLogica() {
        this(new FuncionarioDatos(), new UsuarioDatos(), new ReservaLogica(), ConexionBD::obtenerConexion);
    }

    // para pruebas: FuncionarioLogicaTest pasa datos, reservas y conexiones falsas
    public FuncionarioLogica(FuncionarioDatos funcionarioDatos, UsuarioDatos usuarioDatos,
                             ReservaLogica reservaLogica, FuenteConexion fuenteConexion) {
        this.funcionarioDatos = funcionarioDatos;
        this.usuarioDatos = usuarioDatos;
        this.reservaLogica = reservaLogica;
        this.fuenteConexion = fuenteConexion;
    }

    /**
     * Crea el usuario (clave = ID, rol FUNCIONARIO, activo) y el funcionario
     * en una sola transaccion: si algo falla no queda nada a medias.
     * El ID no puede existir como usuario ni como funcionario, aunque este
     * dado de baja (decision 3).
     */
    public ResultadoOperacionDTO registrar(Funcionario funcionario) {
        if (funcionario == null) {
            return ResultadoOperacionDTO.fallo(MENSAJE_NULO);
        }
        String error = validarId(funcionario.getId());
        if (error == null) {
            error = validarNombreYTelefono(funcionario);
        }
        if (error != null) {
            return ResultadoOperacionDTO.fallo(error);
        }
        Funcionario nuevo = recortado(funcionario);
        String id = nuevo.getId();

        return enTransaccion(conexion -> {
            // todo funcionario tiene su usuario con el mismo ID (FK) y la baja
            // deja las dos filas inactivas: basta con mirar usuario
            UsuarioRegistro existente = usuarioDatos.buscarPorId(conexion, id);
            if (existente != null) {
                return ResultadoOperacionDTO.fallo(mensajeIdOcupado(existente));
            }
            // el hash (unos 0,23 s) va despues de la revision: un SELECT normal
            // no bloquea filas, asi que mientras se calcula no se retiene nada
            String claveHash = HashClave.generar(id);
            usuarioDatos.insertar(conexion, id, claveHash, Rol.FUNCIONARIO);
            funcionarioDatos.insertar(conexion, nuevo);
            return ResultadoOperacionDTO.exito();
        }, ERROR_DUPLICADO, MENSAJE_ID_REPETIDO);
    }

    /**
     * Solo cambia nombre y telefono, y solo si esta activo. El ID nunca cambia:
     * el UPDATE lo usa solo en el WHERE. Es una sola sentencia (atomica), asi
     * que no necesita transaccion ni candado.
     */
    public ResultadoOperacionDTO modificar(Funcionario funcionario) {
        if (funcionario == null) {
            return ResultadoOperacionDTO.fallo(MENSAJE_NULO);
        }
        if (funcionario.getId() == null || funcionario.getId().isBlank()) {
            return ResultadoOperacionDTO.fallo(MENSAJE_NO_EXISTE);
        }
        String error = validarNombreYTelefono(funcionario);
        if (error != null) {
            return ResultadoOperacionDTO.fallo(error);
        }

        try (Connection conexion = fuenteConexion.abrir()) {
            if (!funcionarioDatos.actualizar(conexion, recortado(funcionario))) {
                return ResultadoOperacionDTO.fallo(MENSAJE_NO_EXISTE);   // no existe o esta inactivo
            }
            return ResultadoOperacionDTO.exito();
        } catch (SQLException e) {
            return ResultadoOperacionDTO.fallo(UsuarioDatos.MENSAJE_ERROR_BD);
        }
    }

    /**
     * Baja logica de funcionario y usuario (activo = 0), prohibida si tiene
     * reservas futuras activas. Un funcionario inactivo ya no puede iniciar
     * sesion (lo revisa UsuarioLogica con usuario.activo).
     *
     * Dentro de CANDADO_RESERVAS y de una transaccion: la revision de reservas
     * usa la MISMA conexion, y el commit se hace antes de soltar el candado,
     * asi que una reserva que entre despues ya ve al funcionario inactivo.
     */
    public ResultadoOperacionDTO eliminar(String id) {
        if (id == null || id.isBlank()) {
            return ResultadoOperacionDTO.fallo(MENSAJE_NO_EXISTE);
        }

        synchronized (ReservaLogica.CANDADO_RESERVAS) {          // 1. primero el candado
            return enTransaccion(conexion -> {                   // 2. despues la conexion
                if (funcionarioDatos.buscarActivoPorId(conexion, id) == null) {
                    return ResultadoOperacionDTO.fallo(MENSAJE_NO_EXISTE);
                }
                if (reservaLogica.tieneReservasFuturas(conexion, id)) {
                    return ResultadoOperacionDTO.fallo(MENSAJE_RESERVAS_FUTURAS);
                }
                if (!funcionarioDatos.desactivar(conexion, id)) {
                    return ResultadoOperacionDTO.fallo(MENSAJE_NO_EXISTE);
                }
                // si el usuario no se encontrara, el rollback deshace tambien lo anterior
                if (!usuarioDatos.desactivarFuncionario(conexion, id)) {
                    return ResultadoOperacionDTO.fallo(MENSAJE_NO_EXISTE);
                }
                return ResultadoOperacionDTO.exito();
            }, SIN_CODIGO_CONOCIDO, null);
        }
    }

    // Solo activos. "Contiene", sin distinguir mayusculas (lo hace la collation);
    // los dos filtros se combinan con AND y un texto vacio no filtra.
    // Si la base falla, sale la IllegalStateException(MENSAJE_ERROR_BD) de Datos.
    public List<Funcionario> buscar(String idParcial, String nombreParcial) {
        return funcionarioDatos.buscar(limpiarFiltro(idParcial), limpiarFiltro(nombreParcial));
    }

    // mantiene el nombre del Proyecto 1 (lo usa el controller), pero solo devuelve activos
    public List<Funcionario> listarTodos() {
        return funcionarioDatos.listarActivos();
    }

    // --- apoyo ---

    // null si el ID es valido; si no, el mensaje
    private static String validarId(String id) {
        if (id == null || id.isBlank()) {
            return MENSAJE_ID_VACIO;
        }
        if (id.trim().length() > LARGO_MAXIMO_ID) {
            return MENSAJE_ID_LARGO;
        }
        return null;
    }

    // null si nombre y telefono son validos; si no, el mensaje
    private static String validarNombreYTelefono(Funcionario funcionario) {
        String nombre = funcionario.getNombre();
        if (nombre == null || nombre.isBlank()) {
            return MENSAJE_NOMBRE_VACIO;
        }
        if (nombre.trim().length() > LARGO_MAXIMO_NOMBRE) {
            return MENSAJE_NOMBRE_LARGO;
        }
        String telefono = funcionario.getTelefono();
        if (telefono == null || telefono.isBlank()) {
            return MENSAJE_TELEFONO_VACIO;
        }
        if (telefono.trim().length() > LARGO_MAXIMO_TELEFONO) {
            return MENSAJE_TELEFONO_LARGO;
        }
        return null;
    }

    // Copia con los tres campos recortados, para no modificar el DTO del controller.
    // El ID tambien se recorta: la collation es NO PAD y el login hace trim(), asi
    // que un ID guardado con espacios al final nunca podria iniciar sesion.
    private static Funcionario recortado(Funcionario funcionario) {
        return new Funcionario(
                funcionario.getId().trim(),
                funcionario.getNombre().trim(),
                funcionario.getTelefono().trim());
    }

    private static String mensajeIdOcupado(UsuarioRegistro existente) {
        if (existente.rol() != Rol.FUNCIONARIO) {
            return MENSAJE_ID_USUARIO;
        }
        return existente.activo() ? MENSAJE_ID_FUNCIONARIO : MENSAJE_ID_DADO_DE_BAJA;
    }

    private static String limpiarFiltro(String texto) {
        return (texto != null && !texto.isBlank()) ? texto.trim() : null;
    }

    // los pasos de una escritura, con la conexion de la transaccion
    @FunctionalInterface
    private interface PasosEnTransaccion {
        ResultadoOperacionDTO ejecutar(Connection conexion) throws SQLException;
    }

    /**
     * Conexion -> autocommit apagado -> pasos -> commit si salio bien,
     * rollback si es un fallo de regla o si algo lanzo excepcion.
     * Una SQLException con codigoConocido se responde con mensajeConocido;
     * cualquier otra (incluida no poder abrir la conexion) con MENSAJE_ERROR_BD.
     *
     * Copia del de CategoriaLogica, pero SIN candado adentro: eliminar lo
     * envuelve en CANDADO_RESERVAS y registrar no usa candado. (Deuda tecnica
     * aceptada: el mismo esquema esta en CategoriaLogica y RecursoLogica.)
     */
    private ResultadoOperacionDTO enTransaccion(PasosEnTransaccion pasos, int codigoConocido, String mensajeConocido) {
        try (Connection conexion = fuenteConexion.abrir()) {
            conexion.setAutoCommit(false);
            try {
                ResultadoOperacionDTO resultado = pasos.ejecutar(conexion);
                if (resultado.isExito()) {
                    conexion.commit();
                } else {
                    conexion.rollback();
                }
                return resultado;
            } catch (SQLException | RuntimeException e) {
                deshacer(conexion, e);
                throw e;
            }
        } catch (SQLException e) {
            if (codigoConocido != SIN_CODIGO_CONOCIDO && e.getErrorCode() == codigoConocido) {
                return ResultadoOperacionDTO.fallo(mensajeConocido);
            }
            return ResultadoOperacionDTO.fallo(UsuarioDatos.MENSAJE_ERROR_BD);
        }
    }

    // si el rollback tambien falla, ese error se adjunta al original y no lo tapa
    private static void deshacer(Connection conexion, Exception original) {
        try {
            conexion.rollback();
        } catch (SQLException errorRollback) {
            original.addSuppressed(errorRollback);
        }
    }
}
