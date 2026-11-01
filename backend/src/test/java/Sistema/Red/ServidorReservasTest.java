package Sistema.Red;

import Sistema.DTO.EstadoReserva;
import Sistema.DTO.LoginResultadoDTO;
import Sistema.DTO.ReservaDTO;
import Sistema.DTO.Rol;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

// Pruebas del servidor con clientes reales por socket. No usan MySQL.
class ServidorReservasTest {

    private static final int ESPERA_MS = 3000;   // límite para que una prueba nunca se quede colgada

    private DespachadorPeticiones despachador;
    private GestorClientes gestor;
    private ServidorReservas servidor;
    private final List<ClientePrueba> clientes = new ArrayList<>();

    @BeforeEach
    void iniciarServidor() throws IOException {
        despachador = new DespachadorPeticiones();
        gestor = new GestorClientes();

        // Manejadores falsos, solo para las pruebas
        despachador.registrar(Operaciones.AUTH_LOGIN, (datos, ctx) -> {
            ctx.iniciarSesion(new LoginResultadoDTO("u1", Rol.FUNCIONARIO, null));
            return null;
        });
        despachador.registrar("OP_ECO", (datos, ctx) -> Map.of("saludo", "hola"));
        despachador.registrar("OP_REGLA", (datos, ctx) -> {
            throw new IllegalArgumentException("Regla rota.");
        });
        despachador.registrar("OP_ERROR", (datos, ctx) -> {
            throw new IllegalStateException("detalle interno secreto");
        });
        despachador.registrar("OP_DATOS", (datos, ctx) -> datos == null ? "nulo" : "con datos");
        despachador.registrar("OP_RESERVAR", (datos, ctx) -> {
            gestor.difundirReserva(reservaDePrueba(), ctx);   // así lo hará ManejadorReservas
            return null;
        });

        servidor = new ServidorReservas(despachador, gestor);
        servidor.iniciar(0);   // puerto libre elegido por el sistema
    }

    @AfterEach
    void detenerServidor() {
        for (ClientePrueba cliente : clientes) {
            cliente.cerrar();
        }
        servidor.detener();
    }

    // ---------- pruebas ----------

    @Test
    void operacionRegistradaRespondeConElMismoIdSolicitud() throws Exception {
        ClientePrueba cliente = new ClientePrueba();
        iniciarSesion(cliente);

        cliente.enviar(solicitud("abc-123", "OP_ECO", null));
        MensajeRed respuesta = cliente.leer();

        assertEquals(TipoMensaje.RESPUESTA, respuesta.getTipo());
        assertEquals("abc-123", respuesta.getIdSolicitud());
        assertTrue(exito(respuesta));
        assertEquals("hola", respuesta.getDatos().get("saludo").asText());
    }

    @Test
    void illegalArgumentDaExitoFalseConSuMensajeYLaConexionSigueViva() throws Exception {
        ClientePrueba cliente = new ClientePrueba();
        iniciarSesion(cliente);

        cliente.enviar(solicitud("r1", "OP_REGLA", null));
        MensajeRed error = cliente.leer();
        assertEquals("r1", error.getIdSolicitud());
        assertFalse(exito(error));
        assertEquals("Regla rota.", texto(error));

        // la conexión sigue viva
        cliente.enviar(solicitud("r2", "OP_ECO", null));
        assertTrue(exito(cliente.leer()));
    }

    @Test
    void unaExcepcionInesperadaDaMensajeGenericoYLaConexionSigueViva() throws Exception {
        ClientePrueba cliente = new ClientePrueba();
        iniciarSesion(cliente);

        cliente.enviar(solicitud("e1", "OP_ERROR", null));
        MensajeRed error = cliente.leer();
        assertEquals("e1", error.getIdSolicitud());
        assertFalse(exito(error));
        assertEquals("Error interno del servidor. Intente de nuevo.", texto(error));
        assertFalse(texto(error).contains("secreto"));   // el detalle interno no viaja al cliente

        cliente.enviar(solicitud("e2", "OP_ECO", null));
        assertTrue(exito(cliente.leer()));
    }

    @Test
    void unaOperacionDesconocidaDaError() throws Exception {
        ClientePrueba cliente = new ClientePrueba();
        iniciarSesion(cliente);

        cliente.enviar(solicitud("x1", "NO_EXISTE", null));
        MensajeRed error = cliente.leer();

        assertEquals("x1", error.getIdSolicitud());
        assertFalse(exito(error));
        assertEquals("Operación no reconocida.", texto(error));
    }

    @Test
    void sinSesionRechazaLoQueNoSeaLoginYConSesionYaPermite() throws Exception {
        ClientePrueba cliente = new ClientePrueba();

        cliente.enviar(solicitud("s1", "OP_ECO", null));
        MensajeRed rechazo = cliente.leer();
        assertEquals("s1", rechazo.getIdSolicitud());
        assertFalse(exito(rechazo));
        assertEquals("Debe iniciar sesión.", texto(rechazo));

        iniciarSesion(cliente);

        cliente.enviar(solicitud("s2", "OP_ECO", null));
        assertTrue(exito(cliente.leer()));
    }

    @Test
    void unJsonInvalidoNoCierraLaConexion() throws Exception {
        ClientePrueba cliente = new ClientePrueba();

        cliente.enviarLinea("esto no es json");
        MensajeRed error = cliente.leer();
        assertFalse(exito(error));
        assertEquals("El mensaje recibido no es válido.", texto(error));

        // la conexión sigue viva: el login funciona
        iniciarSesion(cliente);
    }

    @Test
    void datosAusenteONullLlegaAlManejadorComoNull() throws Exception {
        ClientePrueba cliente = new ClientePrueba();
        iniciarSesion(cliente);

        cliente.enviar(solicitud("d1", "OP_DATOS", null));                       // sin datos
        assertEquals("nulo", cliente.leer().getDatos().asText());

        cliente.enviar(solicitud("d2", "OP_DATOS", NullNode.getInstance()));     // "datos":null
        assertEquals("nulo", cliente.leer().getDatos().asText());

        cliente.enviar(solicitud("d3", "OP_DATOS", JsonRed.aNodo(Map.of("a", 1))));
        assertEquals("con datos", cliente.leer().getDatos().asText());
    }

    @Test
    void laReservaNuevaLlegaSoloAlOtroClienteAutenticado() throws Exception {
        ClientePrueba origen = new ClientePrueba();
        ClientePrueba otro = new ClientePrueba();
        ClientePrueba sinSesion = new ClientePrueba();
        iniciarSesion(origen);
        iniciarSesion(otro);

        origen.enviar(solicitud("r1", "OP_RESERVAR", null));

        // lo primero que recibe quien reservó es SU respuesta, no el evento
        MensajeRed respuesta = origen.leer();
        assertEquals(TipoMensaje.RESPUESTA, respuesta.getTipo());
        assertTrue(exito(respuesta));

        // el otro cliente autenticado recibe el evento con la reserva completa
        MensajeRed evento = otro.leer();
        assertEquals(TipoMensaje.EVENTO, evento.getTipo());
        assertEquals(Operaciones.RESERVA_CREADA, evento.getOperacion());
        assertEquals("RES-000001", evento.getDatos().get("idReserva").asText());

        // ni quien reservó ni quien no inició sesión reciben nada más
        assertFalse(origen.hayMensajePendiente(300));
        assertFalse(sinSesion.hayMensajePendiente(300));
    }

    @Test
    void variosClientesConectadosALaVezSonAtendidos() throws Exception {
        List<ClientePrueba> varios = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            varios.add(new ClientePrueba());   // los 5 conectados al mismo tiempo
        }
        for (ClientePrueba cliente : varios) {
            iniciarSesion(cliente);
        }
        for (ClientePrueba cliente : varios) {
            cliente.enviar(solicitud("v", "OP_ECO", null));
        }
        for (ClientePrueba cliente : varios) {
            assertTrue(exito(cliente.leer()));
        }
    }

    // ---------- apoyo ----------

    private MensajeRed solicitud(String id, String operacion, JsonNode datos) {
        MensajeRed mensaje = MensajeRed.solicitud(operacion, datos);
        mensaje.setIdSolicitud(id);
        return mensaje;
    }

    private void iniciarSesion(ClientePrueba cliente) throws Exception {
        cliente.enviar(solicitud("login", Operaciones.AUTH_LOGIN, null));
        assertTrue(exito(cliente.leer()), "El login de prueba debía salir bien.");
    }

    // Si MensajeRed llama distinto a estos getters (por ejemplo isExito), se cambia solo aquí
    private boolean exito(MensajeRed mensaje) {
        return Boolean.TRUE.equals(mensaje.getExito());
    }

    private String texto(MensajeRed mensaje) {
        return mensaje.getMensaje();
    }

    private ReservaDTO reservaDePrueba() {
        ReservaDTO reserva = new ReservaDTO();
        reserva.setIdReserva("RES-000001");
        reserva.setIdFuncionario("u1");
        reserva.setNombreFuncionario("Funcionario de prueba");
        reserva.setNombreActividad("Reunión");
        reserva.setFecha(LocalDate.of(2026, 11, 3));
        reserva.setHoraInicio(LocalTime.of(9, 0));
        reserva.setHoraFin(LocalTime.of(10, 0));
        reserva.setRecursosReservados(List.of());
        reserva.setEstado(EstadoReserva.ACTIVA);
        return reserva;
    }

    // Cliente falso: un socket que habla el protocolo
    private class ClientePrueba {
        private final Socket socket;
        private final BufferedReader entrada;
        private final BufferedWriter salida;

        ClientePrueba() throws IOException {
            socket = new Socket("localhost", servidor.getPuerto());
            socket.setSoTimeout(ESPERA_MS);   // si el servidor no responde, la prueba falla (no se cuelga)
            entrada = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            salida = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            clientes.add(this);
        }

        void enviarLinea(String linea) throws IOException {
            salida.write(linea);
            salida.newLine();
            salida.flush();
        }

        void enviar(MensajeRed mensaje) throws Exception {
            enviarLinea(JsonRed.aLinea(mensaje));
        }

        MensajeRed leer() throws Exception {
            String linea = entrada.readLine();
            assertNotNull(linea, "El servidor cerró la conexión.");
            return JsonRed.leer(linea);
        }

        // true si llega algo dentro de 'ms' milisegundos
        boolean hayMensajePendiente(int ms) throws IOException {
            socket.setSoTimeout(ms);
            try {
                return entrada.readLine() != null;
            } catch (SocketTimeoutException e) {
                return false;
            } finally {
                socket.setSoTimeout(ESPERA_MS);
            }
        }

        void cerrar() {
            try {
                socket.close();
            } catch (IOException e) {
                // ya estaba cerrado
            }
        }
    }
}