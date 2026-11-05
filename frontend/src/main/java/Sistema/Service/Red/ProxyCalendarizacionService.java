package Sistema.Service.Red;

import Sistema.DTO.CalendarizacionDTO;
import Sistema.Red.Operaciones;
import Sistema.Red.ProxyBase;
import Sistema.Service.ICalendarizacionService;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * Representa en el Frontend al CalendarizacionService del Backend: envía CAL_CONSULTAR y devuelve la matriz
 * de disponibilidad ya calculada por el servidor.
 */
public class ProxyCalendarizacionService extends ProxyBase implements ICalendarizacionService {

    @Override
    public CalendarizacionDTO consultar(String idCategoria, LocalDate fecha) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("idCategoria", idCategoria);
        datos.put("fecha", fecha == null ? null : fecha.toString());
        CalendarizacionDTO resultado = ejecutar(Operaciones.CAL_CONSULTAR, datos, CalendarizacionDTO.class);
        if (resultado == null) {
            throw new IllegalStateException("El servidor no devolvió la calendarización.");
        }
        return resultado;
    }
}
