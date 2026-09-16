package Sistema.Service.IAService;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class ExtractorIAReserva {

    private static final String MODELO = "gemini-3.7-flash";
    private static final String ENDPOINT_BASE =
            "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_INTENTOS = 3;  // nos sirve para manejar una cantidad de intentos al query

    public static ReservaExtraida extraer(String frase, List<String> categoriasDisponibles) throws Exception {
        String apiKey = System.getenv("GEMINI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("No se encontró la variable de entorno GEMINI_API_KEY.");
        }

        String hoy = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);

        String prompt = "Hoy es " + hoy + ". A partir de la siguiente frase de un funcionario que quiere "
                + "hacer una reserva de recursos, extrae los datos. "
                + "Responde SOLO con un JSON válido, sin texto adicional, sin explicaciones, "
                + "sin marcas de código, con esta forma exacta: "
                + "{\"actividad\": \"...\", \"fecha\": \"YYYY-MM-DD\", "
                + "\"horaInicio\": \"HH:mm\", \"horaFin\": \"HH:mm\", "
                + "\"categorias\": [\"...\"]}. "
                + "El campo \"categorias\" debe contener SOLO valores tomados literalmente de esta lista "
                + "de categorías disponibles (elige las que apliquen, puede ser una o varias): "
                + categoriasDisponibles + ". "
                + "Frase del funcionario: \"" + frase + "\"";

        String cuerpoJson = construirCuerpoPeticion(prompt);
        String url = ENDPOINT_BASE + MODELO + ":generateContent";

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))   // antes 20
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(cuerpoJson))
                .build();

        HttpResponse<String> response = enviarConReintentos(client, request);

        String textoRespuesta = extraerTexto(response.body());
        JsonNode datos = MAPPER.readTree(limpiarJson(textoRespuesta));

        return construirReservaExtraida(datos);
    }
    /**
     * Envía la petición a Gemini reintentando hasta MAX_INTENTOS veces
     * si el servidor responde 503 (modelo saturado / alta demanda).
     * Cualquier otro código de error (401, 400, etc.) se propaga de inmediato,
     * ya que reintentar no soluciona ese tipo de fallas.
     */
    private static HttpResponse<String> enviarConReintentos(HttpClient client, HttpRequest request) throws Exception {
        Exception ultimoError = null;

        for (int intento = 1; intento <= MAX_INTENTOS; intento++) {
            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    return response;
                }

                if (response.statusCode() != 503) {
                    throw new Exception("Error de la API Gemini (" + response.statusCode() + "): " + response.body());
                }

                ultimoError = new Exception(
                        "Gemini respondió 503. Respuesta: " + response.body()
                );

            } catch (java.net.http.HttpTimeoutException e) {
                // La petición no respondió a tiempo; se trata igual que un 503,
                // ya que reintentar sí puede solucionarlo (a diferencia de un 401/400).
                ultimoError = new Exception("Tiempo de espera agotado, intento " + intento + " de " + MAX_INTENTOS, e);
            }

            if (intento < MAX_INTENTOS) {
                Thread.sleep(1500L * intento);
            }
        }

        throw new Exception("El servicio de IA no respondió después de " + MAX_INTENTOS
                + " intentos. Intente de nuevo en unos minutos.", ultimoError);
    }

    //arma { "contents": [ { "parts": [ { "text": "..." } ] } ] } con nodos de jackson
    static String construirCuerpoPeticion(String prompt) throws Exception {
        ObjectNode parte = MAPPER.createObjectNode().put("text", prompt);
        ArrayNode partes = MAPPER.createArrayNode().add(parte);
        ObjectNode contenido = MAPPER.createObjectNode();
        contenido.set("parts", partes);
        ArrayNode contents = MAPPER.createArrayNode().add(contenido);
        ObjectNode cuerpo = MAPPER.createObjectNode();
        cuerpo.set("contents", contents);
        return MAPPER.writeValueAsString(cuerpo);
    }

    // navega candidates -> content -> parts -> text, igual que en el manual de gemini
    static String extraerTexto(String jsonRespuesta) throws Exception {
        JsonNode raiz = MAPPER.readTree(jsonRespuesta);
        JsonNode candidatos = raiz.path("candidates");

        if (!candidatos.isArray() || candidatos.isEmpty()) {
            throw new Exception("Gemini no devolvió contenido (posible bloqueo de seguridad "
                    + "o respuesta vacía). Respuesta completa: " + jsonRespuesta);
        }

        return candidatos.path(0)
                .path("content")
                .path("parts").path(0)
                .path("text").asText();
    }

    static String limpiarJson(String texto) {
        String limpio = texto.trim();
        if (limpio.startsWith("```")) {
            limpio = limpio.replaceFirst("^```(json)?", "");
            limpio = limpio.replaceFirst("```$", "");
        }
        return limpio.trim();
    }

    static ReservaExtraida construirReservaExtraida(JsonNode datos) {
        ReservaExtraida r = new ReservaExtraida();
        r.setActividad(datos.path("actividad").asText(""));

        String fechaTxt = datos.path("fecha").asText(null);
        if (fechaTxt != null && !fechaTxt.isBlank()) {
            r.setFecha(LocalDate.parse(fechaTxt));
        }

        String horaInicioTxt = datos.path("horaInicio").asText(null);
        if (horaInicioTxt != null && !horaInicioTxt.isBlank()) {
            r.setHoraInicio(LocalTime.parse(normalizarHora(horaInicioTxt)));
        }

        String horaFinTxt = datos.path("horaFin").asText(null);
        if (horaFinTxt != null && !horaFinTxt.isBlank()) {
            r.setHoraFin(LocalTime.parse(normalizarHora(horaFinTxt)));
        }

        List<String> categorias = new ArrayList<>();
        JsonNode arr = datos.path("categorias");
        if (arr.isArray()) {
            for (JsonNode nodo : arr) {
                categorias.add(nodo.asText());
            }
        }
        r.setCategoriasSugeridas(categorias);

        return r;
    }

    static String normalizarHora(String hora) {
        String[] partes = hora.split(":");
        String hh = partes[0].length() == 1 ? "0" + partes[0] : partes[0];
        String mm = partes.length > 1 ? partes[1] : "00";
        if (mm.length() == 1) mm = "0" + mm;
        return hh + ":" + mm;
    }
}
