package Sistema.Red;

import Sistema.DTO.EstadisticaCategoriaDTO;
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
import java.util.List;
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

class EstadisticasRecursosProxyTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ClienteRed cliente = ClienteRed.getInstancia();
    private final AtomicReference<JsonNode> solicitudRecibida =
            new AtomicReference<>();

    @AfterEach
    void limpiar() {
        cliente.cerrar();
    }

    @Test
    void enviaPeriodoConFechasCorrectas() throws Exception {
        consultarConRespuesta(listaRecursos());

        JsonNode datos = solicitudRecibida.get().path("datos");

        assertEquals("2026-03-01", datos.path("desde").asText());
        assertEquals("2026-03-31", datos.path("hasta").asText());
    }

    @Test
    void utilizaOperacionEstadisticasRecursos() throws Exception {
        consultarConRespuesta(listaRecursos());

        JsonNode solicitud = solicitudRecibida.get();

        assertEquals("SOLICITUD", solicitud.path("tipo").asText());
        assertEquals("EST_RECURSOS", solicitud.path("operacion").asText());
        assertTrue(!solicitud.path("idSolicitud").asText().isBlank());
    }

    @Test
    void convierteRespuestaEnListaDeCategorias() throws Exception {
        List<EstadisticaCategoriaDTO> resultado = consultarConRespuesta(
                listaRecursos()
        );

        assertEquals(2, resultado.size());
        assertEquals("Equipo audiovisual", resultado.get(0).getCategoria());
        assertEquals(4, resultado.get(0).getCantidad());
        assertEquals("Laboratorio", resultado.get(1).getCategoria());
        assertEquals(2, resultado.get(1).getCantidad());
    }

    @Test
    void aceptaListaVacia() throws Exception {
        List<EstadisticaCategoriaDTO> resultado = consultarConRespuesta(
                JSON.createArrayNode()
        );

        assertTrue(resultado.isEmpty());
    }

    @Test
    void propagaMensajeDeErrorDelServidor() throws Exception {
        IllegalArgumentException error = probarConServidor(
                solicitud -> {
                    ObjectNode respuesta = respuestaBase(solicitud);
                    respuesta.put("exito", false);
                    respuesta.put("mensaje", "No se pudo calcular el período");
                    respuesta.putNull("datos");
                    return respuesta;
                },
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new EstadisticasRecursosProxy().consultar(
                                LocalDate.of(2026, 3, 1),
                                LocalDate.of(2026, 3, 31)
                        )
                )
        );

        assertEquals("No se pudo calcular el período", error.getMessage());
    }

    @Test
    void rechazaDatosInesperados() throws Exception {
        IllegalStateException error = probarConServidor(
                solicitud -> respuestaExitosa(
                        solicitud,
                        JSON.createObjectNode().put("categoria", "Laboratorio")
                ),
                () -> assertThrows(
                        IllegalStateException.class,
                        () -> new EstadisticasRecursosProxy().consultar(
                                LocalDate.of(2026, 3, 1),
                                LocalDate.of(2026, 3, 31)
                        )
                )
        );

        assertTrue(error.getMessage().contains("lista válida"));
    }

    private List<EstadisticaCategoriaDTO> consultarConRespuesta(
            JsonNode datos
    ) throws Exception {
        return probarConServidor(
                solicitud -> respuestaExitosa(solicitud, datos),
                () -> new EstadisticasRecursosProxy().consultar(
                        LocalDate.of(2026, 3, 1),
                        LocalDate.of(2026, 3, 31)
                )
        );
    }

    private ArrayNode listaRecursos() {
        ArrayNode datos = JSON.createArrayNode();
        datos.addObject()
                .put("categoria", "Equipo audiovisual")
                .put("cantidad", 4);
        datos.addObject()
                .put("categoria", "Laboratorio")
                .put("cantidad", 2);
        return datos;
    }

    private ObjectNode respuestaExitosa(
            JsonNode solicitud,
            JsonNode datos
    ) {
        ObjectNode respuesta = respuestaBase(solicitud);
        respuesta.put("exito", true);
        respuesta.set("datos", datos);
        return respuesta;
    }

    private ObjectNode respuestaBase(JsonNode solicitud) {
        ObjectNode respuesta = JSON.createObjectNode();
        respuesta.put("tipo", "RESPUESTA");
        respuesta.put(
                "idSolicitud",
                solicitud.path("idSolicitud").asText()
        );
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
                             new InputStreamReader(
                                     conexion.getInputStream(),
                                     StandardCharsets.UTF_8
                             )
                     );
                     BufferedWriter salida = new BufferedWriter(
                             new OutputStreamWriter(
                                     conexion.getOutputStream(),
                                     StandardCharsets.UTF_8
                             )
                     )) {
                    JsonNode solicitud = JSON.readTree(entrada.readLine());
                    solicitudRecibida.set(solicitud);

                    salida.write(JSON.writeValueAsString(
                            crearRespuesta.apply(solicitud)
                    ));
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
