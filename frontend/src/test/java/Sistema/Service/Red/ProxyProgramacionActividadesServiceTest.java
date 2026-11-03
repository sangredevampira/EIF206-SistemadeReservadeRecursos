package Sistema.Service.Red;

import Sistema.DTO.ProgramacionActividadesDTO;
import Sistema.Red.ClienteRed;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

// Pruebas del proxy de programación de actividades con un servidor TCP simulado
class ProxyProgramacionActividadesServiceTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ClienteRed cliente = ClienteRed.getInstancia();
    private final AtomicReference<JsonNode> solicitudRecibida = new AtomicReference<>();

    @AfterEach
    void limpiar() {
        cliente.cerrar();
    }

    @Test
    void consultarEnviaActConsultarConFechasIso() throws Exception {
        probarConServidor(
                solicitud -> respuestaExitosa(solicitud, programacionJson()),
                () -> new ProxyProgramacionActividadesService()
                        .consultar(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 8)));

        JsonNode solicitud = solicitudRecibida.get();
        assertEquals("ACT_CONSULTAR", solicitud.path("operacion").asText());
        assertEquals("2026-11-02", solicitud.path("datos").path("fechaDesde").asText());
        assertEquals("2026-11-08", solicitud.path("datos").path("fechaHasta").asText());
    }

    @Test
    void consultarConFechasNulasLasEnviaComoNull() throws Exception {
        probarConServidor(
                solicitud -> respuestaExitosa(solicitud, programacionJson()),
                () -> new ProxyProgramacionActividadesService().consultar(null, null));

        JsonNode datos = solicitudRecibida.get().path("datos");
        assertTrue(datos.has("fechaDesde"));
        assertTrue(datos.path("fechaDesde").isNull());
        assertTrue(datos.has("fechaHasta"));
        assertTrue(datos.path("fechaHasta").isNull());
    }

    @Test
    void consultarConvierteElDtoIncluyendoElMapaAnidado() throws Exception {
        ProgramacionActividadesDTO dto = probarConServidor(
                solicitud -> respuestaExitosa(solicitud, programacionJson()),
                () -> new ProxyProgramacionActividadesService()
                        .consultar(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 8)));

        assertEquals(2, dto.getHoras().size());
        assertEquals("08:00", dto.getHoras().get(1));
        assertEquals(1, dto.getActividadesPorFecha().size());
        assertEquals("Reunión\nAna",
                dto.getActividadesPorFecha().get("2026-11-03").get("08:00"));
    }

    @Test
    void consultarConExitoFalseLanzaIllegalArgumentConElMensaje() throws Exception {
        IllegalArgumentException error = probarConServidor(
                solicitud -> respuestaError(solicitud, "La fecha desde no puede ser posterior a la fecha hasta."),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ProxyProgramacionActividadesService()
                                .consultar(LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 2))));

        assertEquals("La fecha desde no puede ser posterior a la fecha hasta.", error.getMessage());
    }

    @Test
    void consultarConRespuestaIlegibleLanzaIllegalState() throws Exception {
        // "actividadesPorFecha" debería ser un objeto de objetos
        ObjectNode defectuosa = JSON.createObjectNode();
        defectuosa.put("actividadesPorFecha", "no-es-un-mapa");

        probarConServidor(
                solicitud -> respuestaExitosa(solicitud, defectuosa),
                () -> assertThrows(IllegalStateException.class,
                        () -> new ProxyProgramacionActividadesService()
                                .consultar(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 8))));
    }

    @Test
    void consultarSinDatosLanzaIllegalState() throws Exception {
        probarConServidor(
                solicitud -> respuestaExitosa(solicitud, JSON.nullNode()),
                () -> assertThrows(IllegalStateException.class,
                        () -> new ProxyProgramacionActividadesService()
                                .consultar(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 8))));
    }

    // ---------- apoyo ----------

    private ObjectNode programacionJson() {
        ObjectNode dto = JSON.createObjectNode();
        dto.putArray("horas").add("07:30").add("08:00");
        dto.putObject("actividadesPorFecha")
                .putObject("2026-11-03")
                .put("08:00", "Reunión\nAna");
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
