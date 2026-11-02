package Sistema.Red;

import Sistema.DTO.Funcionario;
import Sistema.DTO.LoginResultadoDTO;
import Sistema.DTO.ReservaDTO;
import Sistema.DTO.Rol;
import Sistema.Service.ReservaService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

// Prueba el manejador por medio del despachador, con un servicio y un gestor falsos (sin sockets ni MySQL).
class ManejadorReservasTest {

    // ReservaService falso: anota lo que recibe y devuelve lo que la prueba le diga
    static class ServicioFalso extends ReservaService {
        boolean seLlamoRegistrar = false;
        String idFuncionarioRecibido;
        String nombreRecibido;
        String actividadRecibida;
        LocalDate fechaRecibida;
        LocalTime inicioRecibido;
        LocalTime finRecibido;
        List<String> categoriasRecibidas;
        RuntimeException falloAlRegistrar;

        ReservaDTO reservaExistente;
        boolean seLlamoCancelar = false;

        List<ReservaDTO> listaADevolver = List.of();
        LocalDate desdeRecibido;
        LocalDate hastaRecibido;

        @Override
        public ReservaDTO registrarReserva(String idFuncionario, String nombreFuncionario, String nombreActividad,
                                           LocalDate fecha, LocalTime horaInicio, LocalTime horaFin,
                                           List<String> idsCategoriasSolicitadas) {
            seLlamoRegistrar = true;
            idFuncionarioRecibido = idFuncionario;
            nombreRecibido = nombreFuncionario;
            actividadRecibida = nombreActividad;
            fechaRecibida = fecha;
            inicioRecibido = horaInicio;
            finRecibido = horaFin;
            categoriasRecibidas = idsCategoriasSolicitadas;
            if (falloAlRegistrar != null) throw falloAlRegistrar;
            return reservaDe("RES-000001", idFuncionario);
        }

        @Override
        public boolean cancelarReserva(String idReserva) {
            seLlamoCancelar = true;
            return true;
        }

        @Override
        public ReservaDTO buscarReservaID(String id) { return reservaExistente; }

        @Override
        public List<ReservaDTO> buscarPorFuncionario(String idFuncionario) { return listaADevolver; }

        @Override
        public List<ReservaDTO> buscarPorPeriodo(LocalDate desde, LocalDate hasta) {
            desdeRecibido = desde;
            hastaRecibido = hasta;
            return listaADevolver;
        }
    }

    // GestorClientes falso: anota la difusion en vez de enviar por sockets
    static class GestorFalso extends GestorClientes {
        int veces = 0;
        ReservaDTO ultima;
        ContextoSesion origen;

        @Override
        public void difundirReserva(ReservaDTO reserva, ContextoSesion origen) {
            veces++;
            ultima = reserva;
            this.origen = origen;
        }
    }

    private ServicioFalso servicio;
    private GestorFalso gestor;
    private DespachadorPeticiones despachador;
    private ContextoSesion contexto;

    @BeforeEach
    void preparar() {
        servicio = new ServicioFalso();
        gestor = new GestorFalso();
        despachador = new DespachadorPeticiones();
        new ManejadorReservas(servicio, gestor).registrarEn(despachador);

        contexto = new ContextoSesion();
        contexto.iniciarSesion(new LoginResultadoDTO("u1", Rol.FUNCIONARIO,
                new Funcionario("u1", "Ana Pérez", "8888-0000")));
    }

    // --- RES_CREAR ---

    @Test
    void crearUsaLaIdentidadDeLaSesionYDifundeLaReserva() throws Exception {
        MensajeRed respuesta = enviar(Operaciones.RES_CREAR, datosDeReserva());

        assertTrue(exito(respuesta));
        assertEquals("RES-000001", respuesta.getDatos().get("idReserva").asText());

        assertEquals("u1", servicio.idFuncionarioRecibido);
        assertEquals("Ana Pérez", servicio.nombreRecibido);
        assertEquals("Reunión", servicio.actividadRecibida);
        assertEquals(LocalDate.of(2026, 11, 3), servicio.fechaRecibida);
        assertEquals(LocalTime.of(8, 0), servicio.inicioRecibido);
        assertEquals(LocalTime.of(10, 0), servicio.finRecibido);
        assertEquals(List.of("CAT-1", "CAT-2"), servicio.categoriasRecibidas);

        assertEquals(1, gestor.veces);
        assertEquals("RES-000001", gestor.ultima.getIdReserva());
        assertSame(contexto, gestor.origen);   // el origen se excluye de la difusion
    }

    @Test
    void crearIgnoraElFuncionarioQueMandaElCliente() throws Exception {
        Map<String, Object> datos = datosDeReserva();
        datos.put("idFuncionario", "otro");
        datos.put("nombreFuncionario", "Otro");

        enviar(Operaciones.RES_CREAR, datos);

        assertEquals("u1", servicio.idFuncionarioRecibido);
        assertEquals("Ana Pérez", servicio.nombreRecibido);
    }

    @Test
    void crearConUnaFechaInvalidaDaErrorYNoLlamaAlServicio() throws Exception {
        Map<String, Object> datos = datosDeReserva();
        datos.put("fecha", "03/11/2026");

        MensajeRed respuesta = enviar(Operaciones.RES_CREAR, datos);

        assertFalse(exito(respuesta));
        assertEquals("Los datos de la solicitud no son válidos.", respuesta.getMensaje());
        assertFalse(servicio.seLlamoRegistrar);
        assertEquals(0, gestor.veces);
    }

    @Test
    void siElServicioFallaNoSeDifundeNada() throws Exception {
        servicio.falloAlRegistrar = new IllegalArgumentException("No hay recursos disponibles en las categorías: Salas");

        MensajeRed respuesta = enviar(Operaciones.RES_CREAR, datosDeReserva());

        assertFalse(exito(respuesta));
        assertEquals("No hay recursos disponibles en las categorías: Salas", respuesta.getMensaje());
        assertEquals(0, gestor.veces);
    }

    // --- RES_CANCELAR ---

    @Test
    void unFuncionarioNoPuedeCancelarLaReservaDeOtro() throws Exception {
        servicio.reservaExistente = reservaDe("RES-000007", "otro");

        MensajeRed respuesta = enviar(Operaciones.RES_CANCELAR, Map.of("idReserva", "RES-000007"));

        assertFalse(exito(respuesta));
        assertEquals("Solo puede acceder a sus propias reservas.", respuesta.getMensaje());
        assertFalse(servicio.seLlamoCancelar);
    }

    @Test
    void unFuncionarioCancelaSuPropiaReserva() throws Exception {
        servicio.reservaExistente = reservaDe("RES-000007", "u1");

        MensajeRed respuesta = enviar(Operaciones.RES_CANCELAR, Map.of("idReserva", "RES-000007"));

        assertTrue(exito(respuesta));
        assertTrue(respuesta.getDatos().asBoolean());
        assertTrue(servicio.seLlamoCancelar);
    }

    @Test
    void elAdministradorPuedeCancelarLaReservaDeCualquiera() throws Exception {
        contexto.iniciarSesion(new LoginResultadoDTO("admin", Rol.ADMINISTRADOR, null));
        servicio.reservaExistente = reservaDe("RES-000007", "otro");

        MensajeRed respuesta = enviar(Operaciones.RES_CANCELAR, Map.of("idReserva", "RES-000007"));

        assertTrue(exito(respuesta));
        assertTrue(servicio.seLlamoCancelar);
    }

    // --- consultas ---

    @Test
    void buscarPorFuncionarioSoloPermiteLasPropias() throws Exception {
        servicio.listaADevolver = List.of(reservaDe("RES-000001", "u1"));

        MensajeRed ajena = enviar(Operaciones.RES_BUSCAR_FUNCIONARIO, Map.of("idFuncionario", "otro"));
        assertFalse(exito(ajena));
        assertEquals("Solo puede acceder a sus propias reservas.", ajena.getMensaje());

        MensajeRed propia = enviar(Operaciones.RES_BUSCAR_FUNCIONARIO, Map.of("idFuncionario", "u1"));
        assertTrue(exito(propia));
        assertEquals(1, propia.getDatos().size());
    }

    @Test
    void buscarPorPeriodoDevuelveLasReservasDelRango() throws Exception {
        servicio.listaADevolver = List.of(reservaDe("RES-000001", "u1"), reservaDe("RES-000002", "otro"));

        MensajeRed respuesta = enviar(Operaciones.RES_BUSCAR_PERIODO,
                Map.of("fechaDesde", "2026-11-01", "fechaHasta", "2026-11-30"));

        assertTrue(exito(respuesta));
        assertEquals(LocalDate.of(2026, 11, 1), servicio.desdeRecibido);
        assertEquals(LocalDate.of(2026, 11, 30), servicio.hastaRecibido);
        assertEquals(2, respuesta.getDatos().size());
    }

    // --- apoyo ---

    private Map<String, Object> datosDeReserva() {
        Map<String, Object> datos = new HashMap<>();
        datos.put("nombreActividad", "Reunión");
        datos.put("fecha", "2026-11-03");
        datos.put("horaInicio", "08:00");        // acepta sin segundos
        datos.put("horaFin", "10:00:00");        // y con segundos
        datos.put("idsCategorias", new ArrayList<>(List.of("CAT-1", "CAT-2")));
        return datos;
    }

    private MensajeRed enviar(String operacion, Map<String, ?> datos) throws Exception {
        JsonNode nodo = JsonRed.aNodo(datos);
        MensajeRed solicitud = MensajeRed.solicitud(operacion, nodo);
        solicitud.setIdSolicitud("t1");
        return despachador.despachar(solicitud, contexto);
    }

    private static ReservaDTO reservaDe(String id, String idFuncionario) {
        ReservaDTO reserva = new ReservaDTO();
        reserva.setIdReserva(id);
        reserva.setIdFuncionario(idFuncionario);
        return reserva;
    }

    private boolean exito(MensajeRed mensaje) {
        return Boolean.TRUE.equals(mensaje.getExito());
    }
}
