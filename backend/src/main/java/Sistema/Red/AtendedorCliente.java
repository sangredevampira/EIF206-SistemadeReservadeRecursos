package Sistema.Red;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

// Atiende a UN cliente conectado, en su propio hilo.
public class AtendedorCliente implements Runnable {

    private static final Logger LOG = Logger.getLogger(AtendedorCliente.class.getName());

    private final Socket socket;
    private final DespachadorPeticiones despachador;
    private final GestorClientes gestor;
    private final ContextoSesion contexto = new ContextoSesion();   // la sesión de ESTA conexión
    private final BufferedReader entrada;
    private final BufferedWriter salida;

    public AtendedorCliente(Socket socket, DespachadorPeticiones despachador, GestorClientes gestor)
            throws IOException {
        this.socket = socket;
        this.despachador = despachador;
        this.gestor = gestor;
        this.entrada = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.salida = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    @Override
    public void run() {
        gestor.registrar(this);
        try {
            String linea;
            while ((linea = entrada.readLine()) != null) {   // null = el cliente cerró la conexión
                if (linea.isBlank()) continue;

                try {
                    enviar(procesar(linea));
                } catch (RuntimeException e) {
                    // algo inesperado: se responde error y la conexión sigue viva
                    LOG.log(Level.SEVERE, "Error inesperado al atender un mensaje", e);
                    enviar(MensajeRed.respuestaError(null, null, "Error interno del servidor. Intente de nuevo."));
                }
            }
        } catch (IOException e) {
            LOG.log(Level.FINE, "Conexión cerrada: " + e.getMessage());   // normal al desconectarse
        } finally {
            cerrar();
            gestor.retirar(this);
        }
    }

    // Convierte la línea en mensaje y lo despacha. Un JSON inválido responde error y NO cierra la conexión.
    private MensajeRed procesar(String linea) {
        MensajeRed solicitud;
        try {
            solicitud = JsonRed.leer(linea);
        } catch (Exception e) {
            return MensajeRed.respuestaError(null, null, "El mensaje recibido no es válido.");
        }
        if (solicitud == null)
            return MensajeRed.respuestaError(null, null, "El mensaje recibido no es válido.");

        return despachador.despachar(solicitud, contexto);
    }

    // synchronized: si una respuesta y un evento se envían a la vez, sus líneas no se mezclan
    public synchronized boolean enviar(MensajeRed mensaje) {
        try {
            salida.write(JsonRed.aLinea(mensaje));
            salida.newLine();
            salida.flush();
            return true;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "No se pudo enviar al cliente: " + e.getMessage());
            return false;
        }
    }

    public boolean estaAutenticado() { return contexto.estaAutenticado(); }

    public ContextoSesion getContexto() { return contexto; }

    public void cerrar() {
        try {
            socket.close();
        } catch (IOException e) {
            // ya estaba cerrado
        }
    }
}