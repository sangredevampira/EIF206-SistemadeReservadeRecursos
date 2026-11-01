package Sistema.Red;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

// JSON del protocolo: un mensaje por línea, fechas ISO (docs/00_contratos.md, sección 2).
public final class JsonRed {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private JsonRed() {}

    // Una sola línea: Jackson escapa los saltos de línea dentro de los textos.
    public static String aLinea(MensajeRed mensaje) {
        try {
            return MAPPER.writeValueAsString(mensaje);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo escribir el mensaje", e);
        }
    }

    public static MensajeRed leer(String linea) {
        try {
            return MAPPER.readValue(linea, MensajeRed.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("El mensaje recibido no es un JSON válido", e);
        }
    }

    public static JsonNode aNodo(Object objeto) {
        return MAPPER.valueToTree(objeto);
    }

    public static <T> T desde(JsonNode datos, Class<T> clase) {
        try {
            return MAPPER.treeToValue(datos, clase);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Los datos de la solicitud no son válidos", e);
        }
    }
}
