package Sistema.Red;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

// Abre el puerto, acepta conexiones y lanza un hilo por cliente.
public class ServidorReservas {

    private static final Logger LOG = Logger.getLogger(ServidorReservas.class.getName());

    private final DespachadorPeticiones despachador;
    private final GestorClientes gestor;
    private volatile ServerSocket serverSocket;

    public ServidorReservas(DespachadorPeticiones despachador, GestorClientes gestor) {
        this.despachador = despachador;
        this.gestor = gestor;
    }

    // puerto 0 = el sistema elige uno libre (así lo usan las pruebas)
    public void iniciar(int puerto) throws IOException {
        serverSocket = new ServerSocket(puerto);
        new Thread(this::aceptarConexiones, "servidor-aceptador").start();
    }

    // El puerto real, útil cuando se inició con 0
    public int getPuerto() { return serverSocket.getLocalPort(); }

    public void detener() {
        try {
            serverSocket.close();   // hace que accept() termine y el hilo aceptador salga
        } catch (IOException e) {
            LOG.log(Level.FINE, "Error al cerrar el servidor: " + e.getMessage());
        }
    }

    private void aceptarConexiones() {
        while (!serverSocket.isClosed()) {
            try {
                atender(serverSocket.accept());   // se queda esperando hasta que llegue un cliente
            } catch (IOException e) {
                if (!serverSocket.isClosed())
                    LOG.log(Level.WARNING, "Error al aceptar una conexión: " + e.getMessage());
            }
        }
    }

    private void atender(Socket socket) {
        try {
            AtendedorCliente cliente = new AtendedorCliente(socket, despachador, gestor);
            Thread hilo = new Thread(cliente, "cliente-" + socket.getRemoteSocketAddress());
            hilo.setDaemon(true);   // si el servidor se cierra, estos hilos no lo impiden
            hilo.start();
        } catch (IOException e) {
            LOG.log(Level.WARNING, "No se pudo atender al cliente: " + e.getMessage());
            try {
                socket.close();
            } catch (IOException ignorado) {
                // ya estaba cerrado
            }
        }
    }
}