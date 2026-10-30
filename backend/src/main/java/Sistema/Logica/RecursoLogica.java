package Sistema.Logica;

import Sistema.DTO.RecursoDTO;
import Sistema.Datos.ConexionBD;
import Sistema.Datos.FuenteConexion;
import Sistema.Datos.RecursoDatos;
import Sistema.Datos.UsuarioDatos;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Random;

public class RecursoLogica {

    // codigos de error de MySQL que se traducen a mensajes para el usuario
    static final int ERROR_ID_REPETIDO = 1062;
    static final int ERROR_CATEGORIA_INEXISTENTE = 1452;
    static final int LARGO_MAXIMO_DESCRIPCION = 100;     // recurso.descripcion es VARCHAR(100)

    private final RecursoDatos recursoDatos;
    private final FuenteConexion fuenteConexion;
    private ReservaLogica reservaLogica;

    // sin E/S: los fakes de RecursoServiceTest y DisponibilidadServiceTest usan este constructor
    public RecursoLogica() { this(new RecursoDatos(), null, ConexionBD::obtenerConexion); }

    // constructor para pruebas: RecursoLogicaTest pasa datos, reservas y conexiones falsas.
    // Si reservaLogica es null se crea la real cuando se necesite.
    public RecursoLogica(RecursoDatos recursoDatos, ReservaLogica reservaLogica, FuenteConexion fuenteConexion) {
        this.recursoDatos = recursoDatos;
        this.reservaLogica = reservaLogica;
        this.fuenteConexion = fuenteConexion;
    }

    private ReservaLogica getReservaLogica() {
        if (reservaLogica == null) reservaLogica = new ReservaLogica();
        return reservaLogica;
    }

    public int registrarRecurso(RecursoDTO nuevoRecurso) {
        if(nuevoRecurso == null)
            throw new IllegalArgumentException("El recurso que se intenta registrar no existe");

        validarDatosGenerales(nuevoRecurso);
        validarId(nuevoRecurso.getId());

        // la existencia de la categoria la revisa la FK (error 1452), sin SELECT previo
        try (Connection conexion = fuenteConexion.abrir()) {
            recursoDatos.insertar(conexion, conDescripcionRecortada(nuevoRecurso));
            return 1;
        } catch (SQLException e) {
            throw traducirErrorDeEscritura(e, nuevoRecurso.getId());
        }
    }

    // un recurso inactivo se trata como no encontrado
    public RecursoDTO buscarRecursoID(String id) {
        RecursoDTO recurso = recursoDatos.buscarActivoPorId(id);
        if(recurso == null)
            throw new IllegalArgumentException("El recurso solicitado no fue encontrado");
        return recurso;
    }

    // sobrecarga que usa ReservaLogica: solo devuelve recursos activos
    public List<RecursoDTO> filtrarRecursos(String idCategoria) {
        String categoriaLimpia = (idCategoria != null && !idCategoria.isBlank())
                ? idCategoria.trim()
                : null;

        List<RecursoDTO> filtrados = recursoDatos.filtrarPorCategoria(categoriaLimpia);

        if (filtrados == null)
            throw new IllegalArgumentException("No se pudieron filtrar los recursos por categoría");

        return filtrados;
    }

    public List<RecursoDTO> filtrarRecursos(String idCategoria, String descripcion) {
        String categoriaLimpia = (idCategoria != null && !idCategoria.isBlank())
                ? idCategoria.trim()
                : null;

        // sin toLowerCase: la collation de MySQL ya no distingue mayusculas
        String descripcionLimpia = (descripcion != null && !descripcion.isBlank())
                ? descripcion.trim()
                : null;

        List<RecursoDTO> filtrados = recursoDatos.filtrar(categoriaLimpia, descripcionLimpia);

        if (filtrados == null)
            throw new IllegalArgumentException("No se pudieron filtrar los recursos correctamente");

        return filtrados;
    }

    public List<RecursoDTO> listarRecursos() {
        List<RecursoDTO> listaRecursos = recursoDatos.listarActivos();
        if(listaRecursos == null)
            throw new IllegalArgumentException("Los recursos no se pudieron listar correctamente");
        return listaRecursos;
    }

    // El ID nunca cambia: el UPDATE lo usa solo en el WHERE. Por eso ya no hace
    // falta la revision de "otro recurso con ese ID" del Proyecto 1, que
    // comparaba el ID consigo mismo y nunca se cumplia.
    public boolean modificarRecurso(RecursoDTO recurso){
        if (recurso == null)
            throw new IllegalArgumentException("El recurso que se intenta modificar no existe");

        validarDatosGenerales(recurso);
        validarId(recurso.getId());

        boolean modificado;
        try (Connection conexion = fuenteConexion.abrir()) {
            modificado = recursoDatos.actualizar(conexion, conDescripcionRecortada(recurso));
        } catch (SQLException e) {
            throw traducirErrorDeEscritura(e, recurso.getId());
        }

        // 0 filas: no existe o esta dado de baja
        if (!modificado)
            throw new IllegalArgumentException("El recurso que se intenta modificar no existe o fue dado de baja");
        return true;
    }

    /**
     * Baja logica en una transaccion, dentro del candado de reservas.
     *
     * Orden: primero el candado y despues la conexion.
     *  1. "No tiene reservas futuras -> desactivar" son dos pasos. Con el mismo
     *     candado que registrarReserva, ninguna reserva nueva puede entrar entre
     *     los dos. (Que registrarReserva tome CANDADO_RESERVAS es trabajo de
     *     Kristel en su parte de reservas.) El commit se hace antes de soltar
     *     el candado, asi que quien lo tome despues ya ve activo = 0.
     *  2. Si se abriera la transaccion antes de esperar el candado, un hilo
     *     podria tener bloqueos de MySQL mientras espera el candado y otro el
     *     candado mientras espera esas filas: un bloqueo mutuo que MySQL no
     *     detecta porque no ve el candado de Java.
     *  3. Los hilos que esperan el candado no tienen conexiones abiertas sin usar.
     */
    public boolean eliminarRecurso(String id){
        if (id == null || id.isBlank())
            throw new IllegalArgumentException("El recurso que se intenta eliminar no existe");

        synchronized (ReservaLogica.CANDADO_RESERVAS) {
            try (Connection conexion = fuenteConexion.abrir()) {
                conexion.setAutoCommit(false);
                try {
                    // 1. validacion propia de Recurso
                    if (recursoDatos.buscarActivoPorId(conexion, id) == null)
                        throw new IllegalArgumentException("El recurso que se intenta eliminar no existe");

                    // 2. coordinacion con reserva, con la misma conexion
                    if (getReservaLogica().tieneReservasFuturasActivas(conexion, id))
                        throw new IllegalArgumentException("No se puede eliminar el recurso porque tiene reservas futuras activas");

                    // 3. si no hay ningun problema se desactiva
                    recursoDatos.desactivar(conexion, id);
                    conexion.commit();
                    return true;
                } catch (SQLException | RuntimeException e) {
                    deshacer(conexion, e);
                    throw e;
                }
            } catch (SQLException e) {
                throw new IllegalStateException(UsuarioDatos.MENSAJE_ERROR_BD, e);
            }
        }
    }

    // metodo que crea unicamente el ID; cuenta tambien los IDs de recursos inactivos.
    // Si otro administrador registra el mismo ID antes, la PK lo rechaza (1062).
    public String generarNuevoId() {
        Random random = new Random();
        int id;
        do {
            id = random.nextInt(900000) + 100000;
        } while (recursoDatos.existeId(String.valueOf(id)));

        return String.valueOf(id);
    }

    // metodos de comprobacion y validacion
    private void validarDatosGenerales(RecursoDTO recurso) {
        if (recurso.getCategoria() == null || recurso.getCategoria().getId() == null
                || recurso.getCategoria().getId().isBlank())
            throw new IllegalArgumentException("Debe seleccionar una categoría");
        if (recurso.getDescripcion() == null || recurso.getDescripcion().isBlank())
            throw new IllegalArgumentException("La descripción no puede estar vacía");
        if (recurso.getDescripcion().trim().length() > LARGO_MAXIMO_DESCRIPCION)
            throw new IllegalArgumentException("La descripción no puede tener más de "
                    + LARGO_MAXIMO_DESCRIPCION + " caracteres");
    }

    // formato del Proyecto 1: numero de 6 digitos (el que produce generarNuevoId)
    private void validarId(String id) {
        if (id == null || id.isBlank())
            throw new IllegalArgumentException("El recurso no tiene un ID asignado");
        if (!id.matches("[0-9]{6}"))
            throw new IllegalArgumentException("El ID del recurso debe tener exactamente 6 dígitos");
    }

    // copia para no modificar el DTO que llega del controller
    private static RecursoDTO conDescripcionRecortada(RecursoDTO recurso) {
        return new RecursoDTO(recurso.getId(), recurso.getDescripcion().trim(), recurso.getCategoria());
    }

    private static RuntimeException traducirErrorDeEscritura(SQLException e, String idRecurso) {
        if (e.getErrorCode() == ERROR_ID_REPETIDO)
            return new IllegalArgumentException("Ya existe un recurso con el ID " + idRecurso
                    + " (puede estar dado de baja)");
        if (e.getErrorCode() == ERROR_CATEGORIA_INEXISTENTE)
            return new IllegalArgumentException("La categoría seleccionada no existe");
        return new IllegalStateException(UsuarioDatos.MENSAJE_ERROR_BD, e);
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
