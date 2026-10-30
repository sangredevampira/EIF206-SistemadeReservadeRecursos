
package Sistema.Red;

import Sistema.DTO.ResultadoOperacionDTO;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class ProxyBaseTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ClienteRed cliente = ClienteRed.getInstancia();

    // Subclase de prueba para acceder al metodo protegido.
    private static class ProxyPrueba extends ProxyBase {

        public <T> T consultar(
                String operacion,
                Object datos,
                Class<T> tipo
        ) {
            return ejecutar(operacion, datos, tipo);
        }
    }

    @AfterEach
    void limpiar() {
        cliente.cerrar();
    }

    @Test
    void convierteRespuestaExitosaEnDTO() throws Exception {

        probarConServidor(solicitud -> {

            ObjectNode respuesta = respuestaBase(solicitud);

            respuesta.put("exito", true);

            ObjectNode datos = respuesta.putObject("datos");
            datos.put("exito", true);
            datos.put("mensaje", "Guardado correctamente");

            return respuesta;

        }, () -> {

            ProxyPrueba proxy = new ProxyPrueba();

            ResultadoOperacionDTO resultado = proxy.consultar(
                    "TEST_GUARDAR",
                    Map.of("nombre", "Prueba"),
                    ResultadoOperacionDTO.class
            );

            assertNotNull(resultado);

            assertEquals(
                    "Guardado correctamente",
                    resultado.getMensaje()
            );
        });
    }

    @Test
    void lanzaExcepcionCuandoServidorRechazaOperacion()
            throws Exception {

        probarConServidor(solicitud -> {

            ObjectNode respuesta = respuestaBase(solicitud);

            respuesta.put("exito", false);
            respuesta.put(
                    "mensaje",
                    "No hay recursos disponibles"
            );
            respuesta.putNull("datos");

            return respuesta;

        }, () -> {

            ProxyPrueba proxy = new ProxyPrueba();

            IllegalArgumentException error = assertThrows(
                    IllegalArgumentException.class,
                    () -> proxy.consultar(
                            "TEST_RESERVAR",
                            Map.of("id", "R-001"),
                            ResultadoOperacionDTO.class
                    )
            );

            assertEquals(
                    "No hay recursos disponibles",
                    error.getMessage()
            );
        });
    }

    @Test
    void rechazaRespuestaSinCampoExito() throws Exception {

        probarConServidor(solicitud -> {

            ObjectNode respuesta = respuestaBase(solicitud);

            respuesta.putObject("datos")
                    .put("mensaje", "Respuesta incompleta");

            return respuesta;

        }, () -> {

            ProxyPrueba proxy = new ProxyPrueba();

            IllegalStateException error = assertThrows(
                    IllegalStateException.class,
                    () -> proxy.consultar(
                            "TEST_INVALIDO",
                            null,
                            ResultadoOperacionDTO.class
                    )
            );

            assertTrue(
                    error.getMessage().contains("estado válido")
            );
        });
    }

    // --------------------------------------------------
    // Metodos auxiliares para simular el servidor TCP
    // --------------------------------------------------

    private ObjectNode respuestaBase(JsonNode solicitud) {

        ObjectNode respuesta = JSON.createObjectNode();

        respuesta.put("tipo", "RESPUESTA");

        respuesta.put(
                "idSolicitud",
                solicitud.path("idSolicitud").asText()
        );

        return respuesta;
    }

    private void probarConServidor(
            Function<JsonNode, ObjectNode> crearRespuesta,
            Runnable accionCliente
    ) throws Exception {

        ExecutorService executor =
                Executors.newSingleThreadExecutor();

        try (ServerSocket servidor = new ServerSocket(0)) {

            Future<?> tareaServidor = executor.submit(() -> {

                try (Socket conexion = servidor.accept();

                     BufferedReader entrada = new BufferedReader(
                             new InputStreamReader(
                                     conexion.getInputStream(),
                                     StandardCharsets.UTF_8));

                     BufferedWriter salida = new BufferedWriter(
                             new OutputStreamWriter(
                                     conexion.getOutputStream(),
                                     StandardCharsets.UTF_8))) {

                    JsonNode solicitud =
                            JSON.readTree(entrada.readLine());

                    ObjectNode respuesta =
                            crearRespuesta.apply(solicitud);

                    salida.write(
                            JSON.writeValueAsString(respuesta)
                    );

                    salida.newLine();
                    salida.flush();
                }

                return null;
            });

            cliente.conectar(
                    "127.0.0.1",
                    servidor.getLocalPort()
            );

            accionCliente.run();

            tareaServidor.get(5, TimeUnit.SECONDS);

        } finally {

            cliente.cerrar();
            executor.shutdownNow();
        }
    }
}
