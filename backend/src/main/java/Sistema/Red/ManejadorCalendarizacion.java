package Sistema.Red;

import Sistema.Service.CalendarizacionService;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

// Operación CAL_CONSULTAR. Solo traduce mensaje <-> CalendarizacionService; las validaciones son de la Lógica.
public class ManejadorCalendarizacion {

    private final CalendarizacionService service;

    public ManejadorCalendarizacion(CalendarizacionService service) {
        this.service = service;
    }

    public void registrarEn(DespachadorPeticiones despachador) {
        despachador.registrar(Operaciones.CAL_CONSULTAR, this::consultar);
    }

    // cualquier sesión iniciada (lo exige el despachador). Un dato ausente llega como null a la Lógica.
    private Object consultar(JsonNode datos, ContextoSesion contexto) {
        return service.consultar(
                DatosSolicitud.textoOpcional(datos, "idCategoria"),
                fechaOpcional(datos, "fecha"));
    }

    // fecha "AAAA-MM-DD": ausente o null -> null; ilegible -> datos no válidos (también la usa ManejadorActividades)
    static LocalDate fechaOpcional(JsonNode datos, String campo) {
        String texto = DatosSolicitud.textoOpcional(datos, campo);
        if (texto == null)
            return null;
        try {
            return LocalDate.parse(texto);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(DatosSolicitud.MENSAJE_INVALIDOS);
        }
    }
}
