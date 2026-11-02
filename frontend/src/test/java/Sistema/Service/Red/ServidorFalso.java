package Sistema.Service.Red;

import Sistema.Red.ClienteRed;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Servidor falso para probar los proxies de Derek sin el Backend. Mismo mecanismo
 * que probarConServidor de EstadisticasRecursosProxyTest (de Sofia, privado):
 * un ServerSocket en un puerto libre acepta una conexion, lee UNA solicitud,
 * la guarda y responde; el ClienteRed real (unico) se conecta a el.
 *
 * ClienteRed es unico para toda la JVM y conectar() no hace nada si ya esta
 * conectado. Por eso cada prueba debe empezar y terminar con cerrarCliente()
 * (en @BeforeEach y @AfterEach), y aqui tambien se cierra en el finally: asi una
 * prueba nunca habla con el servidor falso de otra.
 */
final class ServidorFalso {

    static final ObjectMapper JSON = new ObjectMapper();

    private final ClienteRed cliente = ClienteRed.getInstancia();
    private final AtomicReference<JsonNode> solicitud = new AtomicReference<>();

    void cerrarCliente() {
        cliente.cerrar();
    }

    // La solicitud completa que envio el proxy (tipo, idSolicitud, operacion, datos)
    JsonNode solicitudRecibida() {
        return solicitud.get();
    }

    String operacionRecibida() {
        return solicitud.get().path("operacion").asText();
    }

    JsonNode datosRecibidos() {
        return solicitud.get().path("datos");
    }

    // Responde exito=true con esos datos (puede ser null: viaja "datos": null)
    <T> T conExito(JsonNode datos, Callable<T> accion) throws Exception {
        return conRespuesta(s -> {
            ObjectNode respuesta = respuestaBase(s);
            respuesta.put("exito", true);
            if (datos == null) {
                respuesta.putNull("datos");
            } else {
                respuesta.set("datos", datos);
            }
            return respuesta;
        }, accion);
    }

    // Responde exito=false con ese mensaje, como el DespachadorPeticiones
    <T> T conFallo(String mensaje, Callable<T> accion) throws Exception {
        return conRespuesta(s -> {
            ObjectNode respuesta = respuestaBase(s);
            respuesta.put("exito", false);
            respuesta.put("mensaje", mensaje);
            respuesta.putNull("datos");
            return respuesta;
        }, accion);
    }

    <T> T conRespuesta(Function<JsonNode, ObjectNode> crearRespuesta, Callable<T> accion) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try (ServerSocket servidor = new ServerSocket(0)) {
            Future<?> tareaServidor = executor.submit(() -> {
                try (Socket conexion = servidor.accept();
                     BufferedReader entrada = new BufferedReader(
                             new InputStreamReader(conexion.getInputStream(), StandardCharsets.UTF_8));
                     BufferedWriter salida = new BufferedWriter(
                             new OutputStreamWriter(conexion.getOutputStream(), StandardCharsets.UTF_8))) {

                    JsonNode recibida = JSON.readTree(entrada.readLine());
                    solicitud.set(recibida);

                    salida.write(JSON.writeValueAsString(crearRespuesta.apply(recibida)));
                    salida.newLine();
                    salida.flush();
                }
                return null;
            });

            cliente.conectar("127.0.0.1", servidor.getLocalPort());
            T resultado = accion.call();
            tareaServidor.get(5, TimeUnit.SECONDS);
            return resultado;
        } finally {
            cliente.cerrar();
            executor.shutdownNow();
        }
    }

    private static ObjectNode respuestaBase(JsonNode solicitud) {
        ObjectNode respuesta = JSON.createObjectNode();
        respuesta.put("tipo", "RESPUESTA");
        respuesta.put("idSolicitud", solicitud.path("idSolicitud").asText());
        return respuesta;
    }
}
