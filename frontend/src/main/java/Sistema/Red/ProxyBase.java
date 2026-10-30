
package Sistema.Red;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.concurrent.ExecutionException;

public abstract class ProxyBase {

    protected final ClienteRed cliente;
    protected final ObjectMapper json;

    protected ProxyBase() {
        this.cliente = ClienteRed.getInstancia();

        this.json = new ObjectMapper();
        this.json.registerModule(new JavaTimeModule());
    }

    /**
     * Envía una solicitud al servidor y devuelve los datos
     * de la respuesta como un JsonNode.
     */
    protected JsonNode ejecutar(
            String operacion,
            Object datos
    ) {

        try {
            JsonNode datosJson = datos == null
                    ? json.nullNode()
                    : json.valueToTree(datos);

            String mensaje = cliente
                    .enviarSolicitud(operacion, datosJson)
                    .get();

            JsonNode respuesta = json.readTree(mensaje);

            if (respuesta == null
                    || !respuesta.isObject()
                    || !"RESPUESTA".equalsIgnoreCase(
                    respuesta.path("tipo").asText("")
            )) {

                throw new IllegalStateException(
                        "El servidor envió una respuesta inválida."
                );
            }

            JsonNode campoExito = respuesta.path("exito");

            if (!campoExito.isBoolean()) {
                throw new IllegalStateException(
                        "La respuesta no contiene un estado válido."
                );
            }

            if (!campoExito.asBoolean()) {

                String mensajeError = respuesta
                        .path("mensaje")
                        .asText("Operación rechazada.");

                if (mensajeError.isBlank()) {
                    mensajeError = "Operación rechazada.";
                }

                throw new IllegalArgumentException(mensajeError);
            }

            return respuesta.path("datos");

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                    "La operación fue interrumpida.", e
            );

        } catch (ExecutionException e) {

            throw new IllegalStateException(
                    "No fue posible comunicarse con el servidor.",
                    e.getCause()
            );

        } catch (JsonProcessingException e) {

            throw new IllegalStateException(
                    "No fue posible interpretar la respuesta.",
                    e
            );
        }
    }

    /**
     * Ejecuta una operación y transforma su resultado
     * al tipo Java solicitado.
     */
    protected <T> T ejecutar(
            String operacion,
            Object datos,
            Class<T> tipoResultado
    ) {

        JsonNode resultado = ejecutar(operacion, datos);

        if (tipoResultado == Void.class) {
            return null;
        }

        if (resultado == null
                || resultado.isNull()
                || resultado.isMissingNode()) {
            return null;
        }

        try {
            return json.treeToValue(
                    resultado,
                    tipoResultado
            );

        } catch (JsonProcessingException e) {

            throw new IllegalStateException(
                    "No fue posible convertir los datos recibidos.",
                    e
            );
        }
    }
}
