package Sistema.Datos;

import java.util.List;
import java.util.ArrayList;
import java.time.LocalDate;

import Sistema.DTO.ReservaDTO;
import Sistema.DTO.RecursoDTO;
import Sistema.DTO.EstadoReserva;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class ReservaDatos extends Datos<ReservaDTO> {

    public ReservaDatos() {}
    public ReservaDatos(String rutaArchivo)
    {
        super(rutaArchivo, ReservaDTO.class);
        mapper.registerModule(new JavaTimeModule()); // necesario por LocalDate/LocalTime
    }

    public void agregarReserva(ReservaDTO NuevaReserva){
        List<ReservaDTO> reservas = deserializar();
        reservas.add(NuevaReserva);
        serializar(reservas);
    }

    public ReservaDTO buscarReservaID(String id){
        List<ReservaDTO> reservas = deserializar();
        ReservaDTO buscado = null;
        boolean encontrado = false;

        for(ReservaDTO r : reservas)    // para cada RecursoDTO en la lista, lo guardamos temporalmente en r
            if(!encontrado && r.getIdReserva().equals(id)){
                buscado = r;
                encontrado = true;
            }
        return buscado;
    }

    public List<ReservaDTO> listarReservas() { return deserializar(); }

    public List<ReservaDTO> filtrarReservaFuncionario(String idFuncionario){
        List<ReservaDTO> reservas = deserializar();
        List<ReservaDTO> reservasFiltradas = new ArrayList<>();
        for(ReservaDTO r : reservas)
            if(r.getIdFuncionario().equals(idFuncionario))
                reservasFiltradas.add(r);
        return reservasFiltradas;
    }

    public List<ReservaDTO> filtrarReservaFecha(LocalDate fecha){
        List<ReservaDTO> reservas = new ArrayList<>();
        for(ReservaDTO r : deserializar())
            if(r.getFechaReserva().equals(fecha))
                reservas.add(r);
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
