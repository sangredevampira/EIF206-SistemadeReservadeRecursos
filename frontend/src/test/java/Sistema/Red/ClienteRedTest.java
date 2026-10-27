
package Sistema.Red;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.CompletableFuture;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

class ClienteRedTest {

    @Test
    void clientePuedeEnviarYRecibirMensajes() throws Exception {

        ClienteRed cliente = ClienteRed.getInstancia();

        CountDownLatch mensajeRecibido = new CountDownLatch(1);
        AtomicReference<String> respuesta = new AtomicReference<>();

        ExecutorService executor = Executors.newSingleThreadExecutor();

        try (ServerSocket servidor = new ServerSocket(0)) {

            int puerto = servidor.getLocalPort();

            // Servidor temporal para la prueba.
            Future<String> peticionServidor = executor.submit(() -> {

                try (Socket conexion = servidor.accept();

                     BufferedReader entrada = new BufferedReader(
                             new InputStreamReader(
                                     conexion.getInputStream(),
                                     StandardCharsets.UTF_8));

                     BufferedWriter salida = new BufferedWriter(
                             new OutputStreamWriter(
                                     conexion.getOutputStream(),
                                     StandardCharsets.UTF_8))) {

                    String peticion = entrada.readLine();

                    salida.write("{\"tipo\":\"PONG\"}");
                    salida.newLine();
                    salida.flush();

                    return peticion;
                }
            });

            cliente.setReceptorMensajes(mensaje -> {
                respuesta.set(mensaje);
                mensajeRecibido.countDown();
            });

            cliente.conectar("127.0.0.1", puerto);

            assertTrue(cliente.estaConectado());

            cliente.enviarMensaje("{\"tipo\":\"PING\"}");

            assertEquals(
                    "{\"tipo\":\"PING\"}",
                    peticionServidor.get(5, TimeUnit.SECONDS)
            );

            assertTrue(
                    mensajeRecibido.await(5, TimeUnit.SECONDS),
                    "No se recibió respuesta del servidor."
            );

            assertEquals(
                    "{\"tipo\":\"PONG\"}",
                    respuesta.get()
            );

        } finally {

            cliente.cerrar();
            cliente.setReceptorMensajes(mensaje -> {});
            cliente.setReceptorErrores(error -> {});

            executor.shutdownNow();
        }
    }


    @Test
    void clienteDistingueAlertasDeRespuestas() throws Exception {

        ClienteRed cliente = ClienteRed.getInstancia();

        CountDownLatch alertaRecibida = new CountDownLatch(1);
        CountDownLatch respuestaRecibida = new CountDownLatch(1);

        AtomicReference<String> alerta = new AtomicReference<>();
        AtomicReference<String> respuesta = new AtomicReference<>();

        ExecutorService executor = Executors.newSingleThreadExecutor();

        try (ServerSocket servidor = new ServerSocket(0)) {

            Future<?> tareaServidor = executor.submit(() -> {

                try (Socket conexion = servidor.accept();

                     BufferedWriter salida = new BufferedWriter(
                             new OutputStreamWriter(
                                     conexion.getOutputStream(),
                                     StandardCharsets.UTF_8))) {

                    salida.write(
                            "{\"tipo\":\"EVENTO\","
                                    + "\"operacion\":\"RESERVA_CREADA\"}"
                    );
                    salida.newLine();

                    salida.write("{\"tipo\":\"RESPUESTA\"}");
                    salida.newLine();

                    salida.flush();
                }

                return null;
            });

            cliente.setReceptorAlertas(mensaje -> {
                alerta.set(mensaje);
                alertaRecibida.countDown();
            });

            cliente.setReceptorMensajes(mensaje -> {
                respuesta.set(mensaje);
                respuestaRecibida.countDown();
            });

            cliente.conectar(
                    "127.0.0.1",
                    servidor.getLocalPort()
            );

            assertTrue(
                    alertaRecibida.await(5, TimeUnit.SECONDS),
                    "No se recibió la alerta."
            );

            assertTrue(
                    respuestaRecibida.await(5, TimeUnit.SECONDS),
                    "No se recibió la respuesta."
            );

            assertTrue(alerta.get().contains("RESERVA_CREADA"));
            assertTrue(respuesta.get().contains("RESPUESTA"));

            tareaServidor.get(5, TimeUnit.SECONDS);

        } finally {

            cliente.cerrar();

            cliente.setReceptorAlertas(mensaje -> {});
            cliente.setReceptorMensajes(mensaje -> {});
            cliente.setReceptorErrores(error -> {});

            executor.shutdownNow();
        }
    }


    @Test
    void clienteRelacionaRespuestasAunqueLleguenDesordenadas()
            throws Exception {

        ClienteRed cliente = ClienteRed.getInstancia();
        ObjectMapper json = new ObjectMapper();

        CountDownLatch alertaRecibida = new CountDownLatch(1);
        AtomicReference<String> alerta = new AtomicReference<>();

        ExecutorService executor = Executors.newSingleThreadExecutor();

        try (ServerSocket servidor = new ServerSocket(0)) {

            Future<?> servidorSimulado = executor.submit(() -> {

                try (Socket conexion = servidor.accept();

                     BufferedReader entrada = new BufferedReader(
                             new InputStreamReader(
                                     conexion.getInputStream(),
                                     StandardCharsets.UTF_8));

                     BufferedWriter salida = new BufferedWriter(
                             new OutputStreamWriter(
                                     conexion.getOutputStream(),
                                     StandardCharsets.UTF_8))) {

                    JsonNode primera = json.readTree(entrada.readLine());
                    JsonNode segunda = json.readTree(entrada.readLine());

                    assertEquals("PRIMERA",
                            primera.path("operacion").asText());

                    assertEquals("SEGUNDA",
                            segunda.path("operacion").asText());

                    // El servidor envía una alerta entre solicitudes.
                    salida.write(
                            "{\"tipo\":\"EVENTO\","
                                    + "\"operacion\":\"RESERVA_CREADA\"}"
                    );
                    salida.newLine();

                    // Responde primero la SEGUNDA solicitud.
                    salida.write(
                            "{\"tipo\":\"RESPUESTA\","
                                    + "\"idSolicitud\":\""
                                    + segunda.path("idSolicitud").asText()
                                    + "\",\"resultado\":\"segunda\"}"
                    );
                    salida.newLine();

                    // Luego responde la PRIMERA.
                    salida.write(
                            "{\"tipo\":\"RESPUESTA\","
                                    + "\"idSolicitud\":\""
                                    + primera.path("idSolicitud").asText()
                                    + "\",\"resultado\":\"primera\"}"
                    );
                    salida.newLine();

                    salida.flush();
                }

                return null;
            });

            cliente.setReceptorAlertas(mensaje -> {
                alerta.set(mensaje);
                alertaRecibida.countDown();
            });

            cliente.conectar(
                    "127.0.0.1",
                    servidor.getLocalPort()
            );

            CompletableFuture<String> respuestaPrimera =
                    cliente.enviarSolicitud(
                            "PRIMERA", json.createObjectNode()
                    );

            CompletableFuture<String> respuestaSegunda =
                    cliente.enviarSolicitud(
                            "SEGUNDA", json.createObjectNode()
                    );

            JsonNode resultadoPrimera = json.readTree(
                    respuestaPrimera.get(5, TimeUnit.SECONDS)
            );

            JsonNode resultadoSegunda = json.readTree(
                    respuestaSegunda.get(5, TimeUnit.SECONDS)
            );

            assertEquals(
                    "primera",
                    resultadoPrimera.path("resultado").asText()
            );

            assertEquals(
                    "segunda",
                    resultadoSegunda.path("resultado").asText()
            );

            assertTrue(
                    alertaRecibida.await(5, TimeUnit.SECONDS)
            );

            assertTrue(alerta.get().contains("RESERVA_CREADA"));

            servidorSimulado.get(5, TimeUnit.SECONDS);

        } finally {
            cliente.cerrar();

            cliente.setReceptorMensajes(mensaje -> {});
            cliente.setReceptorAlertas(mensaje -> {});
            cliente.setReceptorErrores(error -> {});

            executor.shutdownNow();
        }
    }


    @Test
    void clienteManejaDesconexionYPermiteReconectar()
            throws Exception {

        ClienteRed cliente = ClienteRed.getInstancia();
        ObjectMapper json = new ObjectMapper();

        ExecutorService executor =
                Executors.newSingleThreadExecutor();

        try {

            // PRIMERA PARTE:
            // El servidor recibe una solicitud y se desconecta
            // sin enviar una respuesta.

            try (ServerSocket servidor = new ServerSocket(0)) {

                Future<?> tareaServidor = executor.submit(() -> {

                    try (Socket conexion = servidor.accept();

                         BufferedReader entrada = new BufferedReader(
                                 new InputStreamReader(
                                         conexion.getInputStream(),
                                         StandardCharsets.UTF_8))) {

                        String solicitud = entrada.readLine();

                        assertNotNull(solicitud);

                        // Cerramos la conexion sin responder.
                    }

                    return null;
                });

                cliente.conectar(
                        "127.0.0.1",
                        servidor.getLocalPort()
                );

                CompletableFuture<String> pendiente =
                        cliente.enviarSolicitud(
                                "CONSULTAR",
                                json.createObjectNode()
                        );

                ExecutionException error = assertThrows(
                        ExecutionException.class,
                        () -> pendiente.get(5, TimeUnit.SECONDS)
                );

                assertInstanceOf(
                        IOException.class,
                        error.getCause()
                );

                assertFalse(cliente.estaConectado());

                tareaServidor.get(5, TimeUnit.SECONDS);
            }

            // SEGUNDA PARTE:
            // Creamos otro servidor y comprobamos
            // que el cliente puede reconectarse.

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
                                json.readTree(entrada.readLine());

                        ObjectNode respuesta =
                                json.createObjectNode();

                        respuesta.put("tipo", "RESPUESTA");

                        respuesta.put(
                                "idSolicitud",
                                solicitud.path("idSolicitud").asText()
                        );

                        respuesta.put("resultado", "OK");

                        salida.write(
                                json.writeValueAsString(respuesta)
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

                assertTrue(cliente.estaConectado());

                CompletableFuture<String> pendiente =
                        cliente.enviarSolicitud(
                                "PING",
                                json.createObjectNode()
                        );

                JsonNode respuesta = json.readTree(
                        pendiente.get(5, TimeUnit.SECONDS)
                );

                assertEquals(
                        "OK",
                        respuesta.path("resultado").asText()
                );

                tareaServidor.get(5, TimeUnit.SECONDS);
            }

        } finally {

            cliente.cerrar();

            cliente.setReceptorMensajes(mensaje -> {});
            cliente.setReceptorAlertas(mensaje -> {});
            cliente.setReceptorErrores(error -> {});

            executor.shutdownNow();
        }
    }



}
