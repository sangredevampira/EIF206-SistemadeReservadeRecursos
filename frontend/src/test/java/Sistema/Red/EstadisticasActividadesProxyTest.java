package Sistema.Red;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

class EstadisticasActividadesProxyTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ClienteRed cliente = ClienteRed.getInstancia();
    private final AtomicReference<JsonNode> solicitudRecibida =
            new AtomicReference<>();

    @AfterEach
    void limpiar() {
        cliente.cerrar();
    }

    @Test
    void enviaOperacionYPeriodoCorrectos() throws Exception {
        consultarConRespuesta(JSON.createObjectNode());

        JsonNode solicitud = solicitudRecibida.get();
        JsonNode datos = solicitud.path("datos");

        assertEquals("SOLICITUD", solicitud.path("tipo").asText());
        assertEquals("EST_ACTIVIDADES", solicitud.path("operacion").asText());
        assertTrue(!solicitud.path("idSolicitud").asText().isBlank());
        assertEquals("2026-04-01", datos.path("desde").asText());
        assertEquals("2026-04-30", datos.path("hasta").asText());
    }

    @Test
    void convierteConteoSemanal() throws Exception {
        ObjectNode datos = JSON.createObjectNode();
        datos.put("2026-04-06", 3);
        datos.put("2026-04-13", 5);

        Map<LocalDate, Integer> resultado = consultarConRespuesta(datos);

        assertEquals(2, resultado.size());
        assertEquals(3, resultado.get(LocalDate.of(2026, 4, 6)));
        assertEquals(5, resultado.get(LocalDate.of(2026, 4, 13)));
    }

    @Test
    void mantieneOrdenCronologico() throws Exception {
        ObjectNode datos = JSON.createObjectNode();
        datos.put("2026-04-20", 1);
        datos.put("2026-04-06", 3);
        datos.put("2026-04-13", 5);

        Map<LocalDate, Integer> resultado = consultarConRespuesta(datos);

        assertEquals(
                List.of(
                        LocalDate.of(2026, 4, 6),
                        LocalDate.of(2026, 4, 13),
                        LocalDate.of(2026, 4, 20)
                ),
                new ArrayList<>(resultado.keySet())
        );
    }

    @Test
    void aceptaPeriodoSinActividades() throws Exception {
        Map<LocalDate, Integer> resultado = consultarConRespuesta(
                JSON.createObjectNode()
        );

        assertTrue(resultado.isEmpty());
    }

    @Test
    void propagaMensajeDeErrorDelServidor() throws Exception {
        IllegalArgumentException error = probarConServidor(
                solicitud -> {
                    ObjectNode respuesta = respuestaBase(solicitud);
                    respuesta.put("exito", false);
                    respuesta.put("mensaje", "No fue posible consultar las actividades");
                    respuesta.putNull("datos");
                    return respuesta;
                },
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new EstadisticasActividadesProxy().consultar(
                                LocalDate.of(2026, 4, 1),
                                LocalDate.of(2026, 4, 30)
                        )
                )
        );

        assertEquals(
                "No fue posible consultar las actividades",
                error.getMessage()
        );
    }

    @Test
    void rechazaDatosInesperados() throws Exception {
        ObjectNode datos = JSON.createObjectNode();
        datos.put("semana-invalida", "tres");

        IllegalStateException error = probarConServidor(
                solicitud -> respuestaExitosa(solicitud, datos),
                () -> assertThrows(
                        IllegalStateException.class,
                        () -> new EstadisticasActividadesProxy().consultar(
                                LocalDate.of(2026, 4, 1),
                                LocalDate.of(2026, 4, 30)
                        )
                )
        );

        assertTrue(error.getMessage().contains("cantidad semanal inválida"));
    }

    private Map<LocalDate, Integer> consultarConRespuesta(
            JsonNode datos
    ) throws Exception {
        return probarConServidor(
                solicitud -> respuestaExitosa(solicitud, datos),
                () -> new EstadisticasActividadesProxy().consultar(
                        LocalDate.of(2026, 4, 1),
                        LocalDate.of(2026, 4, 30)
                )
        );
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
