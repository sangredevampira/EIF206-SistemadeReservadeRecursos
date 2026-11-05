package Sistema.Datos;

import Sistema.DTO.CategoriaDTO;
import Sistema.DTO.EstadoReserva;
import Sistema.DTO.RecursoDTO;
import Sistema.DTO.ReservaDTO;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Acceso a las tablas reserva y reserva_recurso con JDBC. Mismo patron que RecursoDatos:
 *   - la version que RECIBE la conexion no la cierra y lanza SQLException
 *     (para las transacciones de ReservaLogica y las pruebas con rollback);
 *   - la que no la recibe abre y cierra la suya y convierte la SQLException en el mensaje generico.
 * El constructor no abre conexiones: los dobles de las pruebas crean ReservaDatos() sin MySQL.
 */
@SuppressWarnings({"SqlResolve", "SqlNoDataSourceInspection"})
public class ReservaDatos {

    // Una fila por (reserva, recurso). LEFT JOIN: una reserva sin recursos tambien aparece.
    // Los recursos se leen SIN filtrar por activo: una reserva vieja conserva su recurso aunque ya este de baja.
    private static final String SELECT_RESERVAS =
            "SELECT r.id_reserva, r.id_funcionario, f.nombre AS nombre_funcionario, r.nombre_actividad,"
                    + " r.fecha, r.hora_inicio, r.hora_fin, r.estado,"
                    + " re.id_recurso, re.descripcion, c.id_categoria, c.descripcion AS descripcion_categoria"
                    + " FROM reserva r"
                    + " JOIN funcionario f ON f.id_funcionario = r.id_funcionario"
                    + " LEFT JOIN reserva_recurso rr ON rr.id_reserva = r.id_reserva"
                    + " LEFT JOIN recurso re ON re.id_recurso = rr.id_recurso"
                    + " LEFT JOIN categoria c ON c.id_categoria = re.id_categoria";

    private static final String ORDEN = " ORDER BY r.fecha, r.hora_inicio, r.id_reserva, re.id_recurso";

    @FunctionalInterface
    private interface Operacion<T> {
        T ejecutar(Connection conexion) throws SQLException;
    }

    // abre una conexion, ejecuta la operacion y la cierra; el error de SQL se oculta tras el mensaje generico
    private <T> T conNuevaConexion(Operacion<T> operacion) {
        try (Connection conexion = ConexionBD.obtenerConexion()) {
            return operacion.ejecutar(conexion);
        } catch (SQLException e) {
            throw new IllegalStateException(UsuarioDatos.MENSAJE_ERROR_BD, e);
        }
    }

    // --- listar y buscar ---

    public List<ReservaDTO> listarReservas() {
        return conNuevaConexion(this::listarReservas);
    }

    public List<ReservaDTO> listarReservas(Connection conexion) throws SQLException {
        return consultar(conexion, "");
    }

    public ReservaDTO buscarReservaID(String id) {
        return conNuevaConexion(conexion -> buscarReservaID(conexion, id));
    }

    public ReservaDTO buscarReservaID(Connection conexion, String id) throws SQLException {
        List<ReservaDTO> encontradas = consultar(conexion, " WHERE r.id_reserva = ?", id);
        return encontradas.isEmpty() ? null : encontradas.get(0);
    }

    public List<ReservaDTO> filtrarReservaFuncionario(String idFuncionario) {
        return conNuevaConexion(conexion -> filtrarReservaFuncionario(conexion, idFuncionario));
    }

    public List<ReservaDTO> filtrarReservaFuncionario(Connection conexion, String idFuncionario)
            throws SQLException {
        return consultar(conexion, " WHERE r.id_funcionario = ?", idFuncionario);
    }

    // incluye las fechas "desde" y "hasta"; trae activas y canceladas
    public List<ReservaDTO> filtrarReservasRangoFechas(LocalDate desde, LocalDate hasta) {
        return conNuevaConexion(conexion -> filtrarReservasRangoFechas(conexion, desde, hasta));
    }

    public List<ReservaDTO> filtrarReservasRangoFechas(Connection conexion, LocalDate desde, LocalDate hasta)
            throws SQLException {
        return consultar(conexion, " WHERE r.fecha BETWEEN ? AND ?", desde, hasta);
    }

    // reservas que incluyen ese recurso (cada una con TODOS sus recursos)
    public List<ReservaDTO> filtrarReservaPorRecurso(String idRecurso) {
        return conNuevaConexion(conexion -> filtrarReservaPorRecurso(conexion, idRecurso));
    }

    public List<ReservaDTO> filtrarReservaPorRecurso(Connection conexion, String idRecurso)
            throws SQLException {
        return consultar(conexion,
                " WHERE r.id_reserva IN (SELECT id_reserva FROM reserva_recurso WHERE id_recurso = ?)",
                idRecurso);
    }

    // --- escribir ---

    // guarda la reserva y sus recursos; si la llama ReservaLogica, la transaccion es de ella
    public void agregarReserva(Connection conexion, ReservaDTO reserva) throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement(
                "INSERT INTO reserva (id_reserva, id_funcionario, nombre_actividad, fecha, hora_inicio,"
                        + " hora_fin, estado) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            sentencia.setString(1, reserva.getIdReserva());
            sentencia.setString(2, reserva.getIdFuncionario());
            sentencia.setString(3, reserva.getNombreActividad());
            sentencia.setObject(4, reserva.getFecha());
            sentencia.setObject(5, reserva.getHoraInicio());
            sentencia.setObject(6, reserva.getHoraFin());
            sentencia.setString(7, reserva.getEstado().name());
            sentencia.executeUpdate();
        }
        try (PreparedStatement sentencia = conexion.prepareStatement(
                "INSERT INTO reserva_recurso (id_reserva, id_recurso) VALUES (?, ?)")) {
            for (RecursoDTO recurso : reserva.getRecursosReservados()) {
                sentencia.setString(1, reserva.getIdReserva());
                sentencia.setString(2, recurso.getId());
                sentencia.addBatch();
            }
            sentencia.executeBatch();
        }
    }

    // version sin conexion: son dos INSERT, asi que van en su propia transaccion
    public void agregarReserva(ReservaDTO reserva) {
        conNuevaConexion(conexion -> {
            conexion.setAutoCommit(false);
            try {
                agregarReserva(conexion, reserva);
                conexion.commit();
            } catch (SQLException | RuntimeException e) {
                conexion.rollback();
                throw e;
            }
            return null;
        });
    }

    // Solo cambia el estado (cancelar): es lo unico que una reserva puede modificar.
    // true si cambio exactamente una fila.
    public boolean modificarReserva(Connection conexion, ReservaDTO reserva) throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement(
                "UPDATE reserva SET estado = ? WHERE id_reserva = ?")) {
            sentencia.setString(1, reserva.getEstado().name());
            sentencia.setString(2, reserva.getIdReserva());
            return sentencia.executeUpdate() == 1;
        }
    }

    public boolean modificarReserva(ReservaDTO reserva) {
        return conNuevaConexion(conexion -> modificarReserva(conexion, reserva));
    }

    // true si el funcionario existe y no esta dado de baja; usa la conexion recibida y no la cierra
    public boolean funcionarioActivo(Connection conexion, String idFuncionario) throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement(
                "SELECT EXISTS (SELECT 1 FROM funcionario WHERE id_funcionario = ? AND activo = 1)")) {
            sentencia.setString(1, idFuncionario);
            try (ResultSet resultado = sentencia.executeQuery()) {
                resultado.next();
                return resultado.getBoolean(1);
            }
        }
    }

    // siguiente "RES-000001"; debe llamarse dentro del candado y de una transaccion (lo exige GeneradorIdsBD)
    public String generarNuevoId(Connection conexion) throws SQLException {
        return GeneradorIdsBD.siguienteId(conexion, "reserva", "id_reserva", "RES");
    }

    // --- consultas de reservas futuras ---
    // "Futura" = fecha de hoy o posterior (no se mira la hora); "activa" = estado ACTIVA.

    public boolean existenReservasFuturasDeFuncionario(String idFuncionario) {
        try (Connection conexion = ConexionBD.obtenerConexion()) {
            return existenReservasFuturasDeFuncionario(conexion, idFuncionario);
        } catch (SQLException e) {
            throw new IllegalStateException(UsuarioDatos.MENSAJE_ERROR_BD, e);
        }
    }

    public boolean existenReservasFuturasDeFuncionario(Connection conexion, String idFuncionario)
            throws SQLException {
        return existe(conexion,
                "SELECT EXISTS (SELECT 1 FROM reserva"
                        + " WHERE id_funcionario = ? AND fecha >= ? AND estado = 'ACTIVA')",
                idFuncionario);
    }

    public boolean existenReservasFuturasActivasDeRecurso(String idRecurso) {
        try (Connection conexion = ConexionBD.obtenerConexion()) {
            return existenReservasFuturasActivasDeRecurso(conexion, idRecurso);
        } catch (SQLException e) {
            throw new IllegalStateException(UsuarioDatos.MENSAJE_ERROR_BD, e);
        }
    }

    public boolean existenReservasFuturasActivasDeRecurso(Connection conexion, String idRecurso)
            throws SQLException {
        return existe(conexion,
                "SELECT EXISTS (SELECT 1 FROM reserva r"
                        + " JOIN reserva_recurso rr ON rr.id_reserva = r.id_reserva"
                        + " WHERE rr.id_recurso = ? AND r.fecha >= ? AND r.estado = 'ACTIVA')",
                idRecurso);
    }

    // el primer parametro es el ID y el segundo la fecha de hoy
    private boolean existe(Connection conexion, String sql, String id) throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, id);
            sentencia.setObject(2, LocalDate.now());
            try (ResultSet resultado = sentencia.executeQuery()) {
                resultado.next();
                return resultado.getBoolean(1);
            }
        }
    }

    // --- apoyo ---

    // Ejecuta el SELECT base con el filtro dado y junta las filas de una misma reserva en un solo ReservaDTO.
    // Los valores siempre van como "?" (nunca dentro del texto del SQL).
    private List<ReservaDTO> consultar(Connection conexion, String filtro, Object... valores)
            throws SQLException {
        Map<String, ReservaDTO> reservas = new LinkedHashMap<>();
        try (PreparedStatement sentencia = conexion.prepareStatement(SELECT_RESERVAS + filtro + ORDEN)) {
            for (int i = 0; i < valores.length; i++) {
                sentencia.setObject(i + 1, valores[i]);
            }
            try (ResultSet resultado = sentencia.executeQuery()) {
                while (resultado.next()) {
                    String idReserva = resultado.getString("id_reserva");
                    ReservaDTO reserva = reservas.get(idReserva);
                    if (reserva == null) {
                        reserva = leerReserva(resultado);
                        reservas.put(idReserva, reserva);
                    }
                    String idRecurso = resultado.getString("id_recurso");
                    if (idRecurso != null) {
                        reserva.getRecursosReservados().add(new RecursoDTO(
                                idRecurso,
                                resultado.getString("descripcion"),
                                new CategoriaDTO(resultado.getString("id_categoria"),
                                        resultado.getString("descripcion_categoria"))));
                    }
                }
            }
        }
        return new ArrayList<>(reservas.values());
    }

    private static ReservaDTO leerReserva(ResultSet resultado) throws SQLException {
        ReservaDTO reserva = new ReservaDTO();
        reserva.setIdReserva(resultado.getString("id_reserva"));
        reserva.setIdFuncionario(resultado.getString("id_funcionario"));
        reserva.setNombreFuncionario(resultado.getString("nombre_funcionario"));
        reserva.setNombreActividad(resultado.getString("nombre_actividad"));
        reserva.setFecha(resultado.getObject("fecha", LocalDate.class));
        reserva.setHoraInicio(resultado.getObject("hora_inicio", LocalTime.class));
        reserva.setHoraFin(resultado.getObject("hora_fin", LocalTime.class));
        reserva.setEstado(EstadoReserva.valueOf(resultado.getString("estado")));
        reserva.setRecursosReservados(new ArrayList<>());
        return reserva;
    }
}