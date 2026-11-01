package Sistema.Red;

import Sistema.DTO.ReservaDTO;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

// Lista de clientes conectados y difusión de eventos.
public class GestorClientes {

    private static final Logger LOG = Logger.getLogger(GestorClientes.class.getName());

    // Conjunto seguro entre hilos: los clientes entran y salen mientras otro hilo recorre la lista
    private final Set<AtendedorCliente> clientes = ConcurrentHashMap.newKeySet();

    public void registrar(AtendedorCliente cliente) { clientes.add(cliente); }

    public void retirar(AtendedorCliente cliente) { clientes.remove(cliente); }

    // Avisa de una reserva nueva a los clientes con sesión, menos a quien la hizo (su contexto)
    public void difundirReserva(ReservaDTO reserva, ContextoSesion origen) {
        MensajeRed evento;
        try {
            evento = MensajeRed.evento(Operaciones.RESERVA_CREADA, JsonRed.aNodo(reserva));
        } catch (Exception e) {
            // la reserva ya está guardada: la alerta se pierde, pero el éxito de la reserva no
            LOG.log(Level.WARNING, "No se pudo armar el evento de reserva: " + e.getMessage());
            return;
        }
        for (AtendedorCliente cliente : clientes) {
            if (cliente.getContexto() != origen && cliente.estaAutenticado()) cliente.enviar(evento);   // si falla con uno, sigue con los demás
        }
    }
}