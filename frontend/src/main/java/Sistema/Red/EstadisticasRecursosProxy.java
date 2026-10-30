package Sistema.Red;

import Sistema.DTO.EstadisticaCategoriaDTO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Consulta al Backend las reservas de recursos agrupadas por categoría.
 *
 * El nombre de la operación y la forma de los datos son provisionales hasta
 * que se publique el contrato TCP definitivo.
 */
public class EstadisticasRecursosProxy extends ProxyBase {

    public static final String OPERACION = "EST_RECURSOS";

    public List<EstadisticaCategoriaDTO> consultar(
            LocalDate desde,
            LocalDate hasta
    ) {
        validarPeriodo(desde, hasta);

        JsonNode resultado = ejecutar(
                OPERACION,
                Map.of(
                        "desde", desde.toString(),
                        "hasta", hasta.toString()
                )
        );

        if (resultado == null || !resultado.isArray()) {
            throw new IllegalStateException(
                    "El servidor no devolvió una lista válida de estadísticas de recursos."
            );
        }

        for (JsonNode estadistica : resultado) {
            if (esEstadisticaInvalida(estadistica)) {
                throw new IllegalStateException(
                        "El servidor devolvió una estadística de recursos inválida."
                );
            }
        }

        try {
            List<EstadisticaCategoriaDTO> estadisticas = json.convertValue(
                    resultado,
                    new TypeReference<List<EstadisticaCategoriaDTO>>() { }
            );

            return List.copyOf(estadisticas);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "No fue posible convertir las estadísticas de recursos recibidas.",
                    e
            );
        }
    }

    private boolean esEstadisticaInvalida(JsonNode estadistica) {
        if (estadistica == null || !estadistica.isObject()) {
            return true;
        }

        JsonNode categoria = estadistica.get("categoria");
        JsonNode cantidad = estadistica.get("cantidad");

        return categoria == null
                || !categoria.isTextual()
                || categoria.asText().isBlank()
                || cantidad == null
                || !cantidad.isIntegralNumber()
                || !cantidad.canConvertToInt()
                || cantidad.intValue() < 0;
    }

    private void validarPeriodo(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null) {
            throw new IllegalArgumentException(
                    "Debe indicar ambas fechas del período."
            );
        }

        if (desde.isAfter(hasta)) {
            throw new IllegalArgumentException(
                    "La fecha 'desde' no puede ser posterior a la fecha 'hasta'."
            );
        }
    }
}
