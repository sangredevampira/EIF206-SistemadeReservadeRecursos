package Sistema.Service.Red;

import Sistema.DTO.ReservaDTO;
import Sistema.Red.Operaciones;
import Sistema.Red.ProxyBase;
import Sistema.Service.IReservaService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Representa en el Frontend al ReservaService del Backend: no tiene reglas de negocio, solo manda cada
 * pedido por la red (RES_*) y devuelve el resultado. Si el servidor responde exito=false, ProxyBase lanza
 * IllegalArgumentException con su mensaje, que es lo que ya esperan los controladores.
 */
public class ProxyReservaService extends ProxyBase implements IReservaService {

    @Override
    public ReservaDTO registrarReserva(String idFuncionario, String nombreFuncionario, String nombreActividad,
                                       LocalDate fecha, LocalTime horaInicio, LocalTime horaFin,
                                       List<String> idsCategoriasSolicitadas) {
        // idFuncionario y nombreFuncionario NO viajan: el servidor usa al funcionario de la sesion.
        // Fechas y horas como texto ("2026-11-03", "08:00"); un dato vacio viaja como null y la Logica lo valida.
        Map<String, Object> datos = new HashMap<>();
        datos.put("nombreActividad", nombreActividad);
        datos.put("fecha", comoTexto(fecha));
        datos.put("horaInicio", comoTexto(horaInicio));
        datos.put("horaFin", comoTexto(horaFin));
        datos.put("idsCategorias", idsCategoriasSolicitadas);
        return ejecutar(Operaciones.RES_CREAR, datos, ReservaDTO.class);
    }

    @Override
    public boolean cancelarReserva(String idReserva) {
        Boolean cancelada = ejecutar(Operaciones.RES_CANCELAR, datosCon("idReserva", idReserva), Boolean.class);
        return Boolean.TRUE.equals(cancelada);
    }

    @Override
    public ReservaDTO buscarReservaID(String id) {
        return ejecutar(Operaciones.RES_BUSCAR_ID, datosCon("idReserva", id), ReservaDTO.class);
    }

    @Override
    public List<ReservaDTO> buscarPorFuncionario(String idFuncionario) {
        return aLista(ejecutar(Operaciones.RES_BUSCAR_FUNCIONARIO, datosCon("idFuncionario", idFuncionario)));
    }

    @Override
    public List<ReservaDTO> buscarPorPeriodo(LocalDate fechaDesde, LocalDate fechaHasta) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("fechaDesde", comoTexto(fechaDesde));
        datos.put("fechaHasta", comoTexto(fechaHasta));
        return aLista(ejecutar(Operaciones.RES_BUSCAR_PERIODO, datos));
    }

    // --- apoyo ---

    private static Map<String, Object> datosCon(String campo, String valor) {
        Map<String, Object> datos = new HashMap<>();
        datos.put(campo, valor);
        return datos;
    }

    private static String comoTexto(LocalDate fecha) {
        return (fecha == null) ? null : fecha.toString();
    }

    private static String comoTexto(LocalTime hora) {
        return (hora == null) ? null : hora.toString();
    }

    private List<ReservaDTO> aLista(JsonNode resultado) {
        if (resultado == null || !resultado.isArray()) {
            throw new IllegalStateException("El servidor no devolvió una lista válida de reservas.");
        }
        try {
            return json.convertValue(resultado, new TypeReference<List<ReservaDTO>>() { });
        } catch (IllegalArgumentException e) {
            // no es un fallo de regla de negocio: es una respuesta que no se pudo leer
            throw new IllegalStateException("No fue posible convertir las reservas recibidas.", e);
        }
    }
}
