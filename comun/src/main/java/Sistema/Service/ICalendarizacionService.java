package Sistema.Service;

import Sistema.DTO.CalendarizacionDTO;

import java.time.LocalDate;

// contrato de la consulta de calendarizacion. Lo implementan el CalendarizacionService del Backend y,
// en el Frontend, el proxy que envia CAL_CONSULTAR.
public interface ICalendarizacionService {

    CalendarizacionDTO consultar(String idCategoria, LocalDate fecha);
}
