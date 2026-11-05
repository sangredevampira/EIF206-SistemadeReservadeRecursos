package Sistema.Service.Red;

import Sistema.DTO.CalendarizacionDTO;
import Sistema.Red.ClienteRed;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Pruebas del proxy de calendarización con un servidor TCP simulado
class ProxyCalendarizacionServiceTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ClienteRed cliente = ClienteRed.getInstancia();
    private final AtomicReference<JsonNode> solicitudRecibida = new AtomicReference<>();

    @AfterEach
    void limpiar() {
        cliente.cerrar();
    }

    @Test
    void consultarEnviaCalConsultarConCategoriaYFechaIso() throws Exception {
        probarConServidor(
                solicitud -> respuestaExitosa(solicitud, calendarizacionJson()),
                () -> new ProxyCalendarizacionService().consultar("CAT-000001", LocalDate.of(2026, 11, 3)));

        JsonNode solicitud = solicitudRecibida.get();
        assertEquals("CAL_CONSULTAR", solicitud.path("operacion").asText());
        assertEquals("CAT-000001", solicitud.path("datos").path("idCategoria").asText());
        assertEquals("2026-11-03", solicitud.path("datos").path("fecha").asText());
    }

    @Test
    void consultarConFechaNulaLaEnviaComoNull() throws Exception {
        probarConServidor(
                solicitud -> respuestaExitosa(solicitud, calendarizacionJson()),
                () -> new ProxyCalendarizacionService().consultar("CAT-000001", null));

        JsonNode datos = solicitudRecibida.get().path("datos");
        assertTrue(datos.has("fecha"));
        assertTrue(datos.path("fecha").isNull());
    }

    @Test
    void consultarConvierteElDtoConHorasRecursosYEstados() throws Exception {
        CalendarizacionDTO dto = probarConServidor(
                solicitud -> respuestaExitosa(solicitud, calendarizacionJson()),
                () -> new ProxyCalendarizacionService().consultar("CAT-000001", LocalDate.of(2026, 11, 3)));

        assertEquals(2, dto.getHoras().size());
        assertEquals("00:00", dto.getHoras().get(0));
        assertEquals(1, dto.getRecursos().size());
        assertEquals("REC-0001", dto.getRecursos().get(0).getRecurso().getId());
        assertEquals("Sala 1", dto.getRecursos().get(0).getRecurso().getDescripcion());
        assertEquals("CAT-000001", dto.getRecursos().get(0).getRecurso().getCategoria().getId());
        assertEquals("Fuera de horario", dto.getRecursos().get(0).getEstados().get(0));
        assertEquals("Reservado - Reunión - Ana", dto.getRecursos().get(0).getEstados().get(1));
    }

    @Test
    void consultarAceptaCategoriaSinRecursos() throws Exception {
        ObjectNode vacia = JSON.createObjectNode();
        vacia.putArray("horas").add("00:00");
        vacia.putArray("recursos");

        CalendarizacionDTO dto = probarConServidor(
                solicitud -> respuestaExitosa(solicitud, vacia),
                () -> new ProxyCalendarizacionService().consultar("CAT-000001", LocalDate.of(2026, 11, 3)));

        assertTrue(dto.getRecursos().isEmpty());
    }

    @Test
    void consultarConExitoFalseLanzaIllegalArgumentConElMensaje() throws Exception {
        IllegalArgumentException error = probarConServidor(
                solicitud -> respuestaError(solicitud, "La categoría no existe."),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ProxyCalendarizacionService().consultar("CAT-999", LocalDate.of(2026, 11, 3))));

        assertEquals("La categoría no existe.", error.getMessage());
    }

    @Test
    void consultarConRespuestaIlegibleLanzaIllegalState() throws Exception {
        // "horas" debería ser un arreglo
        ObjectNode defectuosa = JSON.createObjectNode();
        defectuosa.put("horas", "no-es-un-arreglo");

        probarConServidor(
                solicitud -> respuestaExitosa(solicitud, defectuosa),
                () -> assertThrows(IllegalStateException.class,
                        () -> new ProxyCalendarizacionService().consultar("CAT-000001", LocalDate.of(2026, 11, 3))));
    }

    @Test
    void consultarSinDatosLanzaIllegalState() throws Exception {
        probarConServidor(
                solicitud -> respuestaExitosa(solicitud, JSON.nullNode()),
                () -> assertThrows(IllegalStateException.class,
                        () -> new ProxyCalendarizacionService().consultar("CAT-000001", LocalDate.of(2026, 11, 3))));
    }

    // ---------- apoyo ----------

    private ObjectNode calendarizacionJson() {
        ObjectNode dto = JSON.createObjectNode();
        dto.putArray("horas").add("00:00").add("00:30");
        ArrayNode recursos = dto.putArray("recursos");
        ObjectNode fila = recursos.addObject();
        ObjectNode recurso = fila.putObject("recurso");
        recurso.put("id", "REC-0001");
        recurso.put("descripcion", "Sala 1");
        recurso.putObject("categoria").put("id", "CAT-000001").put("descripcion", "Salas");
        fila.putArray("estados").add("Fuera de horario").add("Reservado - Reunión - Ana");
        return dto;
    }

    private ObjectNode respuestaExitosa(JsonNode solicitud, JsonNode datos) {
        ObjectNode respuesta = respuestaBase(solicitud);
        respuesta.put("exito", true);
        respuesta.set("datos", datos);
        return respuesta;
    }

    private ObjectNode respuestaError(JsonNode solicitud, String mensaje) {
        ObjectNode respuesta = respuestaBase(solicitud);
        respuesta.put("exito", false);
        respuesta.put("mensaje", mensaje);
        respuesta.putNull("datos");
        return respuesta;
    }

    private ObjectNode respuestaBase(JsonNode solicitud) {
        ObjectNode respuesta = JSON.createObjectNode();
        respuesta.put("tipo", "RESPUESTA");
        respuesta.put("idSolicitud", solicitud.path("idSolicitud").asText());
        return respuesta;
    }

    private <T> T probarConServidor(
            Function<JsonNode, ObjectNode> crearRespuesta,
            Callable<T> accionCliente
    ) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try (ServerSocket servidor = new ServerSocket(0)) {
            Future<?> tareaServidor = executor.submit(() -> {
                try (Socket conexion = servidor.accept();
                     BufferedReader entrada = new BufferedReader(
                             new InputStreamReader(conexion.getInputStream(), StandardCharsets.UTF_8));
                     BufferedWriter salida = new BufferedWriter(
                             new OutputStreamWriter(conexion.getOutputStream(), StandardCharsets.UTF_8))) {
                    JsonNode solicitud = JSON.readTree(entrada.readLine());
                    solicitudRecibida.set(solicitud);

                    salida.write(JSON.writeValueAsString(crearRespuesta.apply(solicitud)));
                    salida.newLine();
                    salida.flush();
                }
                return null;
            });

            cliente.conectar("127.0.0.1", servidor.getLocalPort());
            T resultado = accionCliente.call();
            tareaServidor.get(5, TimeUnit.SECONDS);
            return resultado;
        } finally {
            cliente.cerrar();
            executor.shutdownNow();
        }
    }
}
