package Sistema.Logica;

import Sistema.DTO.EstadoReserva;
import Sistema.DTO.RecursoDTO;
import Sistema.DTO.ReservaDTO;
import Sistema.Datos.ConexionBD;
import Sistema.Datos.FuenteConexion;
import Sistema.Datos.ReservaDatos;
import Sistema.Datos.UsuarioDatos;
import Sistema.util.HorarioSistema;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public class ReservaLogica {

    // codigo de error de MySQL: la clave foranea no existe (aqui, el funcionario de la reserva)
    static final int ERROR_CLAVE_FORANEA = 1452;

    private final ReservaDatos reservaDatos;
    private final FuenteConexion fuenteConexion;
    private RecursoLogica recursoLogica;

    // lo usan también RecursoLogica y FuncionarioLogica para las bajas
    static final Object CANDADO_RESERVAS = new Object();

    public ReservaLogica() { this(new ReservaDatos(), null, ConexionBD::obtenerConexion); }

    // constructor solo para pruebas: permite inyectar datos, recursos y conexiones falsas.
    // Si recursoLogica es null se crea la real cuando se necesite.
    public ReservaLogica(ReservaDatos reservaDatos, RecursoLogica recursoLogica, FuenteConexion fuenteConexion) {
        this.reservaDatos = reservaDatos;
        this.recursoLogica = recursoLogica;
        this.fuenteConexion = fuenteConexion;
    }

    private RecursoLogica getRecursoLogica() {
        if (recursoLogica == null) recursoLogica = new RecursoLogica();
        return recursoLogica;
    }

    /**
     * Registra una reserva y devuelve la reserva creada (con su ID y los recursos asignados).
     * Si alguna categoria no tiene un recurso libre lanza IllegalArgumentException con esas categorias.
     *
     * "Buscar recursos libres -> generar el ID -> guardar" son pasos que no pueden mezclarse con los de otra
     * reserva (dos clientes podrian quedarse con el mismo recurso), asi que van dentro del candado y de una
     * transaccion: se guarda todo junto o nada.
     *
     * Orden: primero el candado y despues la conexion (igual que RecursoLogica.eliminarRecurso), para que
     * los hilos que esperan el candado no tengan conexiones abiertas sin usar. El commit se hace antes de
     * soltar el candado, asi que quien lo tome despues ya ve la reserva guardada.
     */
    public ReservaDTO registrarReserva(String idFuncionario, String nombreFuncionario, String nombreActividad,
                                       LocalDate fecha, LocalTime horaInicio, LocalTime horaFin,
                                       List<String> idsCategoriasSolicitadas) {

        validarDatosGenerales(nombreActividad, fecha, horaInicio, horaFin, idsCategoriasSolicitadas);

        synchronized (CANDADO_RESERVAS) {
            try (Connection conexion = fuenteConexion.abrir()) {
                conexion.setAutoCommit(false);
                try {
                    ReservaDTO nueva = armarReserva(conexion, idFuncionario, nombreFuncionario, nombreActividad,
                            fecha, horaInicio, horaFin, idsCategoriasSolicitadas);
                    reservaDatos.agregarReserva(conexion, nueva);
                    conexion.commit();
                    return nueva;
                } catch (SQLException | RuntimeException e) {
                    deshacer(conexion, e);
                    throw e;
                }
            } catch (SQLException e) {
                throw traducirError(e);
            }
        }
    }

    // Dentro del candado y de la transaccion: asigna un recurso por categoria, genera el ID y arma la reserva.
    private ReservaDTO armarReserva(Connection conexion, String idFuncionario, String nombreFuncionario,
                                    String nombreActividad, LocalDate fecha, LocalTime horaInicio,
                                    LocalTime horaFin, List<String> idsCategorias) throws SQLException {
        List<RecursoDTO> recursosAsignados = new ArrayList<>();
        List<String> categoriasSinRecursos = new ArrayList<>();

        // LinkedHashSet: si una categoria viene repetida no se le asigna el mismo recurso dos veces
        for (String idCategoria : new LinkedHashSet<>(idsCategorias)) {
            List<RecursoDTO> recursosDeLaCategoria = getRecursoLogica().filtrarRecursos(idCategoria);
            RecursoDTO disponible = buscarPrimerRecursoDisponible(conexion, recursosDeLaCategoria,
                    fecha, horaInicio, horaFin);

            if (disponible == null)
                categoriasSinRecursos.add(nombreDeCategoria(idCategoria, recursosDeLaCategoria));
            else
                recursosAsignados.add(disponible);
        }

        if (!categoriasSinRecursos.isEmpty())
            throw new IllegalArgumentException("No hay recursos disponibles en las categorías: "
                    + String.join(", ", categoriasSinRecursos));

        return new ReservaDTO(
                reservaDatos.generarNuevoId(conexion),
                idFuncionario,
                nombreFuncionario,
                nombreActividad,
                fecha,
                horaInicio,
                horaFin,
                recursosAsignados,
                EstadoReserva.ACTIVA
        );
    }

    private RecursoDTO buscarPrimerRecursoDisponible(Connection conexion, List<RecursoDTO> recursos,
                                                     LocalDate fecha, LocalTime horaInicio, LocalTime horaFin)
            throws SQLException {
        for (RecursoDTO recurso : recursos)
            if (recursoDisponible(conexion, recurso, fecha, horaInicio, horaFin))
                return recurso;
        return null;
    }

    // para el mensaje: la descripcion de la categoria sale de sus recursos; si no tiene ninguno, se usa el ID
    private String nombreDeCategoria(String idCategoria, List<RecursoDTO> recursosDeLaCategoria) {
        if (!recursosDeLaCategoria.isEmpty()
                && recursosDeLaCategoria.get(0).getCategoria() != null
                && recursosDeLaCategoria.get(0).getCategoria().getDescripcion() != null)
            return recursosDeLaCategoria.get(0).getCategoria().getDescripcion();
        return idCategoria;
    }

    public boolean cancelarReserva(String idReserva){
        ReservaDTO reserva = reservaDatos.buscarReservaID(idReserva);

        if(reserva == null)
            throw new IllegalArgumentException("La reserva que se intenta cancelar no existe");
        if(reserva.getEstado() == EstadoReserva.CANCELADA)
            throw new IllegalArgumentException("Esta reserva ya se encuentra cancelada");
        if(reserva.getFecha().isBefore(LocalDate.now()))
            throw new IllegalArgumentException("No se puede cancelar una reserva que ya pasó");

        reserva.setEstado(EstadoReserva.CANCELADA);
        return reservaDatos.modificarReserva(reserva);
    }

    public ReservaDTO buscarReservaID(String id){
        ReservaDTO reserva = reservaDatos.buscarReservaID(id);
        if(reserva == null)
            throw new IllegalArgumentException("La reserva solicitada no fue encontrada");
        return reserva;
    }

    // ver las reservas de un funcionario
    public List<ReservaDTO> buscarPorFuncionario(String idFuncionario){
        List<ReservaDTO> reservasFuncionario = reservaDatos.filtrarReservaFuncionario(idFuncionario);
        if(reservasFuncionario.isEmpty())
            throw new IllegalArgumentException("El usuario no tiene reservas registradas");

        return reservasFuncionario;
    }

    // metodos para coordinarcon con el trabajo de sofi
    public List<ReservaDTO> buscarPorPeriodo(LocalDate fechaDesde, LocalDate fechaHasta){
        if (fechaDesde == null || fechaHasta == null)
            throw new IllegalArgumentException("Debe indicar ambas fechas del período");

        if (fechaDesde.isAfter(fechaHasta))
            throw new IllegalArgumentException("La fecha 'desde' no puede ser posterior a la fecha 'hasta'");

        return reservaDatos.filtrarReservasRangoFechas(fechaDesde, fechaHasta);
    }

    // metodo para coordinar la eliminacion de un recurso con RecursoLogica
    public boolean tieneReservasFuturasActivas(String idRecurso){
        return reservaDatos.existenReservasFuturasActivasDeRecurso(idRecurso);
    }

    // version para transacciones: usa la conexion recibida y no la cierra
    public boolean tieneReservasFuturasActivas(Connection conexion, String idRecurso) throws SQLException {
        return reservaDatos.existenReservasFuturasActivasDeRecurso(conexion, idRecurso);
    }

    // metodos de comprobacion
    private void validarDatosGenerales(String nombreActividad, LocalDate fecha,
                                       LocalTime horaInicio, LocalTime horaFin,
                                       List<String> idsCategoriasSolicitadas) {
        if (nombreActividad == null || nombreActividad.isBlank())
            throw new IllegalArgumentException("Debe describir la actividad a realizar.");
        if (fecha == null)
            throw new IllegalArgumentException("Debe indicar la fecha de la reserva.");
        if (fecha.isBefore(LocalDate.now()))
            throw new IllegalArgumentException("No se pueden crear reservas en fechas pasadas.");
        if (horaInicio == null || horaFin == null)
            throw new IllegalArgumentException("Debe indicar la hora de inicio y finalización.");
        if (!horaInicio.isBefore(horaFin))
            throw new IllegalArgumentException("La hora de inicio debe ser anterior a la hora de finalización.");
        if (horaInicio.isBefore(HorarioSistema.INICIO_SERVICIO)
                || horaFin.isAfter(HorarioSistema.FIN_SERVICIO))
            throw new IllegalArgumentException("La reserva debe estar dentro del horario de 06:00 a 22:00.");
        if (horaInicio.getMinute() % HorarioSistema.MINUTOS_POR_BLOQUE != 0
                || horaFin.getMinute() % HorarioSistema.MINUTOS_POR_BLOQUE != 0
                || horaInicio.getSecond() != 0
                || horaFin.getSecond() != 0)
            throw new IllegalArgumentException("Las horas deben utilizar intervalos de 30 minutos.");
        if (idsCategoriasSolicitadas == null || idsCategoriasSolicitadas.isEmpty())
            throw new IllegalArgumentException("Debe seleccionar al menos una categoría de recurso.");
    }

    public boolean reservaExistente(String id){
        for (ReservaDTO r : reservaDatos.listarReservas())
            if (r.getIdReserva().equals(id))
                return true;
        return false;
    }

    // usa la conexion de la transaccion: lee las reservas de ese recurso y busca un traslape
    private boolean recursoDisponible(Connection conexion, RecursoDTO recurso, LocalDate fecha,
                                      LocalTime horaInicio, LocalTime horaFin) throws SQLException {
        List<ReservaDTO> reservasRecurso = reservaDatos.filtrarReservaPorRecurso(conexion, recurso.getId());

        for(ReservaDTO r : reservasRecurso){
            boolean estaActiva = r.getEstado() == EstadoReserva.ACTIVA;
            boolean mismaFecha = r.getFecha().equals(fecha);

            boolean seTraslapan = horaInicio.isBefore(r.getHoraFin()) && horaFin.isAfter(r.getHoraInicio());

            if(estaActiva && mismaFecha && seTraslapan)
                return false;
        }
        return true;
    }

    public boolean tieneReservasFuturas(String idFuncionario) {
        return reservaDatos.existenReservasFuturasDeFuncionario(idFuncionario);
    }

    // version para transacciones: usa la conexion recibida y no la cierra
    public boolean tieneReservasFuturas(Connection conexion, String idFuncionario) throws SQLException {
        return reservaDatos.existenReservasFuturasDeFuncionario(conexion, idFuncionario);
    }

    // --- apoyo de la transaccion ---

    private static RuntimeException traducirError(SQLException e) {
        if (e.getErrorCode() == ERROR_CLAVE_FORANEA)
            return new IllegalArgumentException("El funcionario de la reserva no está registrado en la base de datos.");
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