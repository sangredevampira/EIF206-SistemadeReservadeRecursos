package Sistema.Datos;

import java.util.List;
import java.util.ArrayList;
import java.time.LocalDate;

import Sistema.DTO.ReservaDTO;
import Sistema.DTO.RecursoDTO;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class ReservaDatos extends Datos<ReservaDTO> {

    public ReservaDatos() {}
    public ReservaDatos(String rutaArchivo)
    {
        super(rutaArchivo, ReservaDTO.class);
        mapper.registerModule(new JavaTimeModule());    // necesario por LocalDate/LocalTime
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
}
