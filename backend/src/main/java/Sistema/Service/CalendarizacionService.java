package Sistema.Service;

import Sistema.DTO.CalendarizacionDTO;
import Sistema.DTO.RecursoDTO;
import Sistema.DTO.ReservaDTO;
import Sistema.Logica.CalendarizacionLogica;

import java.time.LocalDate;
import java.util.List;

public class CalendarizacionService implements ICalendarizacionService {

    private final CalendarizacionLogica calendarizacionLogica;

    public CalendarizacionService() {
        this(new CalendarizacionLogica());
    }

    // permite inyectar la lógica (lo usan las pruebas)
    public CalendarizacionService(CalendarizacionLogica logica) {
        this.calendarizacionLogica = logica;
    }

    @Override
    public CalendarizacionDTO consultar(String idCategoria, LocalDate fecha) {
        return calendarizacionLogica.consultar(idCategoria, fecha);
    }

    public List<String> generarHorasDelDia() {
        return calendarizacionLogica.generarHorasDelDia();
    }

    public String obtenerEstadoRecurso(RecursoDTO recurso, LocalDate fecha, String hora, List<ReservaDTO> reservas) {
        return calendarizacionLogica.obtenerEstadoRecurso(recurso, fecha, hora, reservas);
    }
}
