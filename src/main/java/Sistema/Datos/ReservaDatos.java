package Sistema.Datos;

import java.util.List;
import java.util.ArrayList;
import java.time.LocalDate;

import Sistema.DTO.ReservaDTO;
import Sistema.DTO.RecursoDTO;
import Sistema.DTO.EstadoReserva;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class ReservaDatos extends Datos {

    public ReservaDatos() {}
    public ReservaDatos(String rutaArchivo)
    {
        super(rutaArchivo, ReservaDTO.class);
        mapper.registerModule(new JavaTimeModule()); // necesario por LocalDate/LocalTime
    }
}
