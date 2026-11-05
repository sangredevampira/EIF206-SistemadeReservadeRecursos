package Sistema.Red;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.TreeMap;

/**
 * Consulta al Backend el conteo de actividades agrupado por semana.
 *
 * El nombre de la operación y la forma de los datos son provisionales hasta
 * que se publique el contrato TCP definitivo.
 */
public class EstadisticasActividadesProxy extends ProxyBase {

    public static final String OPERACION = "EST_ACTIVIDADES";

    public Map<LocalDate, Integer> consultar(
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

        if (resultado == null || !resultado.isObject()) {
            throw new IllegalStateException(
                    "El servidor no devolvió un conteo semanal válido."
            );
        }

        Map<LocalDate, Integer> conteoSemanal = new TreeMap<>();

        resultado.fields().forEachRemaining(entrada -> {
            JsonNode cantidad = entrada.getValue();

            if (!cantidad.isIntegralNumber()
                    || !cantidad.canConvertToInt()
                    || cantidad.intValue() < 0) {
                throw new IllegalStateException(
                        "El servidor devolvió una cantidad semanal inválida."
                );
            }

            try {
                conteoSemanal.put(
                        LocalDate.parse(entrada.getKey()),
                        cantidad.intValue()
                );
            } catch (DateTimeParseException e) {
                throw new IllegalStateException(
                        "El servidor devolvió una fecha semanal inválida.",
                        e
                );
            }
        });

        return conteoSemanal;
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
