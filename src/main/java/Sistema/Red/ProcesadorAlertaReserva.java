
package Sistema.Red;

import Sistema.DTO.ReservaDTO;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.util.Optional;

public class ProcesadorAlertaReserva {

    private final ObjectMapper json;

    public ProcesadorAlertaReserva() {
        json = new ObjectMapper();
        json.registerModule(new JavaTimeModule());
    }

    public Optional<ReservaDTO> procesar(String mensaje)
            throws IOException {

        if (mensaje == null || mensaje.isBlank()) {
            return Optional.empty();
        }

        JsonNode contenido = json.readTree(mensaje);

        if (contenido == null || !contenido.isObject()) {
            return Optional.empty();
        }

        String tipo = contenido.path("tipo").asText("");
        String operacion = contenido.path("operacion").asText("");

        if (!"EVENTO".equalsIgnoreCase(tipo)
                || !"RESERVA_CREADA".equalsIgnoreCase(operacion)) {
            return Optional.empty();
        }

        JsonNode datos = contenido.path("datos");

        if (!datos.isObject()) {
            throw new IOException(
                    "La notificación no contiene los datos de la reserva."
            );
        }

        ReservaDTO reserva =
                json.treeToValue(datos, ReservaDTO.class);

        if (reserva.getIdReserva() == null
                || reserva.getIdReserva().isBlank()) {
            throw new IOException(
                    "La notificación no contiene un ID de reserva válido."
            );
        }

        return Optional.of(reserva);
    }
}
