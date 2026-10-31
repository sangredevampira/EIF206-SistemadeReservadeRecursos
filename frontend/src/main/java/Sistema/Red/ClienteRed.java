
package Sistema.Red;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;


public final class ClienteRed {

    private static final ClienteRed INSTANCIA = new ClienteRed();

    private volatile Socket socket;
    private BufferedReader entrada;
    private BufferedWriter salida;
    private Thread hiloLector;

    private volatile Consumer<String> receptorMensajes = mensaje -> {};
    private volatile Consumer<Exception> receptorErrores = error -> {};

    private static final ObjectMapper JSON = new ObjectMapper();
    private volatile Consumer<String> receptorAlertas =
            mensaje -> {};
    private final ConcurrentHashMap<String, CompletableFuture<String>>
            solicitudesPendientes = new ConcurrentHashMap<>();

    private final Object bloqueoEscritura = new Object();

    private ClienteRed() {
    }

    public static ClienteRed getInstancia() {
        return INSTANCIA;
    }

    public synchronized void conectar(String ip, int puerto)
            throws IOException {

        if (estaConectado()) {
            return;
        }

        Socket nuevoSocket = new Socket();

        try {
            nuevoSocket.connect(
                    new InetSocketAddress(ip, puerto),
                    5000
            );

            BufferedReader nuevaEntrada = new BufferedReader(
                    new InputStreamReader(
                            nuevoSocket.getInputStream(),
                            StandardCharsets.UTF_8
                    )
            );

            BufferedWriter nuevaSalida = new BufferedWriter(
                    new OutputStreamWriter(
                            nuevoSocket.getOutputStream(),
                            StandardCharsets.UTF_8
                    )
            );

            entrada = nuevaEntrada;
            salida = nuevaSalida;
            socket = nuevoSocket;

            hiloLector = new Thread(
                    () -> escucharMensajes(
                            nuevoSocket,
                            nuevaEntrada
                    ),
                    "ClienteRed-Lector"
            );

            hiloLector.setDaemon(true);
            hiloLector.start();

        } catch (IOException e) {
            nuevoSocket.close();
            throw e;
        }
    }

    private void escucharMensajes(
            Socket conexion,
            BufferedReader lector
    ) {

        try {
            String mensaje;

            while ((mensaje = lector.readLine()) != null) {
                procesarMensaje(mensaje);
            }

        } catch (Exception e) {
            if (!conexion.isClosed()) {
                notificarError(e);
            }

        } finally {
            cerrarSiActual(conexion);
        }
    }

    private synchronized void cerrarSiActual(Socket conexion) {
        if (socket == conexion) {
            cerrar();
        }
    }

    public void enviarMensaje(String mensaje) throws IOException {

        if (mensaje == null ||
                mensaje.contains("\n") ||
                mensaje.contains("\r")) {
            throw new IllegalArgumentException(
                    "El mensaje debe ocupar una sola línea."
            );
        }

        synchronized (bloqueoEscritura) {

            if (!estaConectado()) {
                throw new IOException(
                        "No existe conexión con el servidor."
                );
            }

            salida.write(mensaje);
            salida.newLine();
            salida.flush();
        }
    }

    public boolean estaConectado() {
        Socket conexion = socket;
        return conexion != null
                && conexion.isConnected()
                && !conexion.isClosed();
    }

    public void setReceptorMensajes(
            Consumer<String> receptor
    ) {
        this.receptorMensajes =
                receptor == null ? mensaje -> {} : receptor;
    }

    public void setReceptorErrores(
            Consumer<Exception> receptor
    ) {
        this.receptorErrores =
                receptor == null ? error -> {} : receptor;
    }

    public synchronized void cerrar() {

        Socket conexion = socket;
        socket = null;

        if (conexion != null) {
            try {
                conexion.close();
            } catch (IOException e) {
                notificarError(e);
            }
        }
        solicitudesPendientes.forEach((id, futuro) ->
                futuro.completeExceptionally(
                        new IOException(
                                "Conexión cerrada antes de recibir la respuesta."
                        )
                )
        );

        solicitudesPendientes.clear();
    }

    private void procesarMensaje(String mensaje) {

        try {
            JsonNode contenido = JSON.readTree(mensaje);

            if (contenido != null && contenido.isObject()) {

                String tipo = contenido.path("tipo").asText("");

                if ("EVENTO".equalsIgnoreCase(tipo)) {
                    notificarReceptor(receptorAlertas, mensaje);
                    return;
                }

                if ("RESPUESTA".equalsIgnoreCase(tipo)
                        || "ERROR".equalsIgnoreCase(tipo)) {

                    String idSolicitud = contenido
                            .path("idSolicitud")
                            .asText("");

                    CompletableFuture<String> futuro =
                            solicitudesPendientes.remove(idSolicitud);

                    if (futuro != null) {
                        futuro.complete(mensaje);
                        return;
                    }
                }
            }

            notificarReceptor(receptorMensajes, mensaje);

        } catch (JsonProcessingException e) {
            notificarError(e);
        }
    }

    private <T> void notificarReceptor(
            Consumer<T> receptor,
            T valor
    ) {
        try {
            receptor.accept(valor);
        } catch (RuntimeException e) {
            notificarError(e);
        }
    }

    private void notificarError(Exception error) {
        try {
            receptorErrores.accept(error);
        } catch (RuntimeException ignorada) {
            // Un receptor defectuoso no debe finalizar el hilo lector.
        }
    }

    public void setReceptorAlertas(
            Consumer<String> receptor
    ) {
        this.receptorAlertas =
                receptor == null ? mensaje -> {} : receptor;
    }

    public CompletableFuture<String> enviarSolicitud(
            String operacion, JsonNode datos) {

        if (operacion == null || operacion.isBlank()) {
            throw new IllegalArgumentException(
                    "Debe indicar la operación solicitada."
            );
        }

        String idSolicitud = UUID.randomUUID().toString();

        ObjectNode peticion = JSON.createObjectNode();

        peticion.put("tipo", "SOLICITUD");
        peticion.put("idSolicitud", idSolicitud);
        peticion.put("operacion", operacion);

        if (datos == null) {
            peticion.putNull("datos");
        } else {
            peticion.set("datos", datos);
        }

        CompletableFuture<String> futuro = new CompletableFuture<>();

        solicitudesPendientes.put(idSolicitud, futuro);

        futuro.orTimeout(10, TimeUnit.SECONDS)
                .whenComplete((respuesta, error) ->
                        solicitudesPendientes.remove(idSolicitud, futuro)
                );

        try {
            enviarMensaje(JSON.writeValueAsString(peticion));
        } catch (IOException e) {
            solicitudesPendientes.remove(idSolicitud, futuro);
            futuro.completeExceptionally(e);
        }

        return futuro;
    }
}
