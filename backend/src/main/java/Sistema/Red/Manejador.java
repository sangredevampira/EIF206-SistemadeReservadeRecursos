package Sistema.Red;

import com.fasterxml.jackson.databind.JsonNode;

// Atiende UNA operación del protocolo. El despachador lo llama cuando llega esa operación.
@FunctionalInterface
public interface Manejador {

    // datos: el objeto "datos" de la solicitud (null si no vino).
    // Devuelve lo que viajará en "datos" de la respuesta (o null).
    // Para fallar: lanzar IllegalArgumentException("mensaje legible").
    Object manejar(JsonNode datos, ContextoSesion contexto);
}