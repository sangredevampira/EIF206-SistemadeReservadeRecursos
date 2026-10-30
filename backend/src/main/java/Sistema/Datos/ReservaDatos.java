package Sistema.Datos;

import java.util.List;
import java.util.ArrayList;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

import Sistema.DTO.ReservaDTO;
import Sistema.DTO.RecursoDTO;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.SerializationFeature;

public class ReservaDatos extends Datos<ReservaDTO> {

    public ReservaDatos() {}
    public ReservaDatos(String rutaArchivo)
    {
        super(rutaArchivo, ReservaDTO.class);
        mapper.registerModule(new JavaTimeModule());    // necesario por LocalDate/LocalTime
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public void agregarReserva(ReservaDTO NuevaReserva){
        List<ReservaDTO> reservas = deserializar();
        reservas.add(NuevaReserva);
        serializar(reservas);
    }

    public ReservaDTO buscarReservaID(String id){
        for(ReservaDTO r : deserializar())
            if(r.getIdReserva().equals(id))
                return r;
        return null;
    }

    public List<ReservaDTO> listarReservas() { return deserializar(); }

    public List<ReservaDTO> filtrarReservaFuncionario(String idFuncionario){
        List<ReservaDTO> reservasFiltradas = new ArrayList<>();
        for(ReservaDTO r : deserializar())
            if(r.getIdFuncionario().equals(idFuncionario))
                reservasFiltradas.add(r);
        return reservasFiltradas;
    }

    public List<ReservaDTO> filtrarReservaFecha(LocalDate fecha){
        List<ReservaDTO> reservas = new ArrayList<>();
        for(ReservaDTO r : deserializar())
            if(r.getFecha().equals(fecha))
                reservas.add(r);
        return reservas;
    }

    public List<ReservaDTO> filtrarReservasRangoFechas(LocalDate desde, LocalDate hasta){
        List<ReservaDTO> reservas = new ArrayList<>();
        for(ReservaDTO r : deserializar()){
            LocalDate fecha = r.getFecha();
            if(!fecha.isBefore(desde) && !fecha.isAfter(hasta))
                reservas.add(r);
        }
        return reservas;
    }

    public List<ReservaDTO> filtrarReservaPorRecurso(String idRecurso){ // solo se encarga de buscar las reservas que tengan un cierto recurso
        List<ReservaDTO> reservas = new ArrayList<>();
        for(ReservaDTO r : deserializar())
            for(RecursoDTO recurso : r.getRecursosReservados())
                if(recurso.getId().equals(idRecurso)){
                    reservas.add(r);
                    break;
                }
        return reservas;
    }

    public boolean modificarReserva(ReservaDTO reserva){
        List<ReservaDTO> reservas = deserializar();
        boolean modificado = false;

        for (int i = 0; i < reservas.size() && !modificado; i++)
            if (reservas.get(i).getIdReserva().equals(reserva.getIdReserva())) {
                reservas.set(i, reserva);
                modificado = true;
            }
        if (modificado) serializar(reservas);
        return modificado;
    }

    // --- consultas SQL (MySQL) ---
    // Dos versiones por metodo, como en UsuarioDatos: la que recibe la conexion
    // no la cierra y lanza SQLException; la otra abre y cierra la suya.
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
}
