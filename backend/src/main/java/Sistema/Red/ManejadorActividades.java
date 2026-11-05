package Sistema.Red;

import Sistema.Service.ProgramacionActividadesService;
import com.fasterxml.jackson.databind.JsonNode;

// Operación ACT_CONSULTAR. Solo traduce mensaje <-> ProgramacionActividadesService; las validaciones son de la Lógica.
public class ManejadorActividades {

    private final ProgramacionActividadesService service;

    public ManejadorActividades(ProgramacionActividadesService service) {
        this.service = service;
    }

    public void registrarEn(DespachadorPeticiones despachador) {
        despachador.registrar(Operaciones.ACT_CONSULTAR, this::consultar);
    }

    // cualquier sesión iniciada (lo exige el despachador). Un dato ausente llega como null a la Lógica.
    private Object consultar(JsonNode datos, ContextoSesion contexto) {
        return service.consultar(
                ManejadorCalendarizacion.fechaOpcional(datos, "fechaDesde"),
                ManejadorCalendarizacion.fechaOpcional(datos, "fechaHasta"));
    }
}
