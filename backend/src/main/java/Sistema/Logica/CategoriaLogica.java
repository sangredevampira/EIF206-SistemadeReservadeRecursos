package Sistema.Logica;

import Sistema.DTO.CategoriaDTO;
import Sistema.DTO.ResultadoOperacionDTO;
import Sistema.Datos.CategoriaDatos;
import Sistema.Datos.ConexionBD;
import Sistema.Datos.FuenteConexion;
import Sistema.Datos.RecursoDatos;
import Sistema.Datos.UsuarioDatos;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Reglas de negocio de categorias (paso 5b, con MySQL).
 *
 * registrar, modificar y eliminar devuelven siempre un ResultadoOperacionDTO:
 * exito, o fallo con un mensaje legible. Un error de la base se responde como
 * fallo(MENSAJE_ERROR_BD), sin detalles de SQL (contrato, seccion 2.2).
 *
 * Las tres escrituras siguen el mismo esquema:
 *   validar sin base -> candado -> abrir conexion -> autocommit apagado ->
 *   pasos con la MISMA conexion -> commit (o rollback si algo falla).
 */
public class CategoriaLogica {

    // codigos de error de MySQL que se traducen a mensajes para el usuario
    static final int ERROR_DUPLICADO = 1062;          // UNIQUE de descripcion (o PK)
    static final int ERROR_TIENE_HIJOS = 1451;        // FK RESTRICT de recurso -> categoria
    static final int LARGO_MAXIMO_DESCRIPCION = 100;  // categoria.descripcion es VARCHAR(100)

    static final String MENSAJE_NULA = "Debe indicar la categoria.";
    static final String MENSAJE_VACIA = "La descripcion no puede estar vacia.";
    static final String MENSAJE_LARGA = "La descripcion no puede tener mas de "
            + LARGO_MAXIMO_DESCRIPCION + " caracteres.";
    static final String MENSAJE_REPETIDA = "Ya existe una categoria con esa descripcion.";
    static final String MENSAJE_REPETIDA_OTRA = "Ya existe otra categoria con esa descripcion.";
    static final String MENSAJE_NO_EXISTE = "La categoria no existe.";
    static final String MENSAJE_TIENE_RECURSOS =
            "No se puede eliminar la categoria porque tiene recursos asociados"
                    + " (tambien cuentan los recursos dados de baja).";

    /**
     * Candado de las escrituras de categorias (de paquete: CategoriaLogicaTest
     * revisa con Thread.holdsLock que se use).
     *
     * Por que, si GeneradorIdsBD ya usa SELECT ... FOR UPDATE:
     *  1. Con la tabla vacia el FOR UPDATE solo toma bloqueos de "hueco", que
     *     no se excluyen entre si: dos hilos pasarian juntos y sus INSERT
     *     chocarian (MySQL mata uno con el error 1213). El candado los pone en fila antes.
     *  2. "La descripcion no existe -> insertar" son dos pasos; con el candado
     *     ningun otro hilo de este proceso entra entre los dos.
     *  3. Los hilos que esperan no tienen conexiones ni bloqueos de MySQL.
     * Orden: primero el candado y despues la conexion (igual que
     * RecursoLogica.eliminarRecurso), para no tener bloqueos de MySQL
     * mientras se espera el candado: seria un bloqueo mutuo que MySQL no ve.
     *
     * Riesgo aceptado: el candado solo vale dentro de un proceso. Mientras cada
     * Frontend use el Backend como biblioteca (dependencia temporal), dos
     * Frontend que registren a la vez sobre la tabla vacia pueden recibir el
     * 1213; el usuario ve MENSAJE_ERROR_BD y reintenta. Con un solo servidor
     * (paso 6) desaparece. El UNIQUE y la PK siguen protegiendo los datos.
     */
    static final Object CANDADO_CATEGORIAS = new Object();

    private final CategoriaDatos categoriaDatos;

    // Dependencia directa a RecursoDatos (no a RecursoLogica): solo se
    // necesita LEER si la categoria tiene recursos, con la misma conexion.
    private final RecursoDatos recursoDatos;

    private final FuenteConexion fuenteConexion;

    // sin E/S: los constructores de Datos no abren nada
    public CategoriaLogica() {
        this(new CategoriaDatos(), new RecursoDatos(), ConexionBD::obtenerConexion);
    }

    // para pruebas: CategoriaLogicaTest pasa datos y conexiones falsas
    public CategoriaLogica(CategoriaDatos categoriaDatos, RecursoDatos recursoDatos, FuenteConexion fuenteConexion) {
        this.categoriaDatos = categoriaDatos;
        this.recursoDatos = recursoDatos;
        this.fuenteConexion = fuenteConexion;
    }

    public ResultadoOperacionDTO registrar(CategoriaDTO categoria) {
        if (categoria == null) {
            return ResultadoOperacionDTO.fallo(MENSAJE_NULA);
        }
        String error = validarDescripcion(categoria.getDescripcion());
        if (error != null) {
            return ResultadoOperacionDTO.fallo(error);
        }
        String descripcion = categoria.getDescripcion().trim();

        String[] idNuevo = new String[1];
        ResultadoOperacionDTO resultado = enTransaccion(conexion -> {
            // primero el FOR UPDATE: asi la lectura siguiente ve lo ultimo confirmado
            String id = categoriaDatos.siguienteId(conexion);
            if (categoriaDatos.existeDescripcion(conexion, descripcion, null)) {
                return ResultadoOperacionDTO.fallo(MENSAJE_REPETIDA);
            }
            categoriaDatos.insertar(conexion, new CategoriaDTO(id, descripcion));
            idNuevo[0] = id;
            return ResultadoOperacionDTO.exito();
        }, ERROR_DUPLICADO, MENSAJE_REPETIDA);

        // como en el Proyecto 1, el DTO recibido queda con su ID, solo si se confirmo
        if (resultado.isExito()) {
            categoria.setId(idNuevo[0]);
        }
        return resultado;
    }

    public ResultadoOperacionDTO modificar(CategoriaDTO categoria) {
        if (categoria == null) {
            return ResultadoOperacionDTO.fallo(MENSAJE_NULA);
        }
        String id = categoria.getId();
        if (id == null || id.isBlank()) {
            return ResultadoOperacionDTO.fallo(MENSAJE_NO_EXISTE);
        }
        String error = validarDescripcion(categoria.getDescripcion());
        if (error != null) {
            return ResultadoOperacionDTO.fallo(error);
        }
        String descripcion = categoria.getDescripcion().trim();

        return enTransaccion(conexion -> {
            if (categoriaDatos.buscarPorId(conexion, id) == null) {
                return ResultadoOperacionDTO.fallo(MENSAJE_NO_EXISTE);
            }
            // la propia categoria no cuenta: puede cambiar solo sus mayusculas
            if (categoriaDatos.existeDescripcion(conexion, descripcion, id)) {
                return ResultadoOperacionDTO.fallo(MENSAJE_REPETIDA_OTRA);
            }
            if (!categoriaDatos.actualizar(conexion, new CategoriaDTO(id, descripcion))) {
                return ResultadoOperacionDTO.fallo(MENSAJE_NO_EXISTE);
            }
            return ResultadoOperacionDTO.exito();
        }, ERROR_DUPLICADO, MENSAJE_REPETIDA_OTRA);
    }

    /**
     * Regla del enunciado (funcionalidad 4) y del contrato (seccion 6, regla 8):
     * una categoria con recursos, aunque esten inactivos, no se elimina.
     * La revision y el DELETE usan la misma conexion. Si un recurso entra
     * entre los dos (RecursoLogica no toma este candado), la FK RESTRICT
     * rechaza el DELETE con 1451 y se responde el mismo mensaje.
     */
    public ResultadoOperacionDTO eliminar(String id) {
        if (id == null || id.isBlank()) {
            return ResultadoOperacionDTO.fallo(MENSAJE_NO_EXISTE);
        }

        return enTransaccion(conexion -> {
            if (categoriaDatos.buscarPorId(conexion, id) == null) {
                return ResultadoOperacionDTO.fallo(MENSAJE_NO_EXISTE);
            }
            if (recursoDatos.categoriaTieneRecursos(conexion, id)) {
                return ResultadoOperacionDTO.fallo(MENSAJE_TIENE_RECURSOS);
            }
            if (!categoriaDatos.eliminar(conexion, id)) {
                return ResultadoOperacionDTO.fallo(MENSAJE_NO_EXISTE);
            }
            return ResultadoOperacionDTO.exito();
        }, ERROR_TIENE_HIJOS, MENSAJE_TIENE_RECURSOS);
    }

    // "contiene", sin distinguir mayusculas (lo hace la collation). Texto vacio = todas.
    // Si la base falla, sale la IllegalStateException(MENSAJE_ERROR_BD) de Datos.
    public List<CategoriaDTO> buscar(String descripcionParcial) {
        String descripcion = (descripcionParcial != null && !descripcionParcial.isBlank())
                ? descripcionParcial.trim()
                : null;
        return categoriaDatos.buscar(descripcion);
    }

    public List<CategoriaDTO> listarTodos() {
        return categoriaDatos.listarTodos();
    }

    // --- apoyo ---

    // null si la descripcion es valida; si no, el mensaje
    private static String validarDescripcion(String descripcion) {
        if (descripcion == null || descripcion.isBlank()) {
            return MENSAJE_VACIA;
        }
        if (descripcion.trim().length() > LARGO_MAXIMO_DESCRIPCION) {
            return MENSAJE_LARGA;
        }
        return null;
    }

    // los pasos de una escritura, con la conexion de la transaccion
    @FunctionalInterface
    private interface PasosEnTransaccion {
        ResultadoOperacionDTO ejecutar(Connection conexion) throws SQLException;
    }

    /**
     * Candado -> conexion -> autocommit apagado -> pasos -> commit si salio
     * bien, rollback si es un fallo de regla o si algo lanzo excepcion.
     * Una SQLException con codigoConocido se responde con mensajeConocido;
     * cualquier otra (incluida no poder abrir la conexion) con MENSAJE_ERROR_BD.
     */
    private ResultadoOperacionDTO enTransaccion(PasosEnTransaccion pasos, int codigoConocido, String mensajeConocido) {
        synchronized (CANDADO_CATEGORIAS) {
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
                if (e.getErrorCode() == codigoConocido) {
                    return ResultadoOperacionDTO.fallo(mensajeConocido);
                }
                return ResultadoOperacionDTO.fallo(UsuarioDatos.MENSAJE_ERROR_BD);
            }
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
