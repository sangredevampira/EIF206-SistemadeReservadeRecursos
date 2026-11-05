package Sistema.Service.Red;

import Sistema.Red.ClienteRed;
import Sistema.DTO.EstadoReserva;
import Sistema.DTO.ReservaDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Pruebas del proxy de reservas con un servidor TCP simulado (mismo patrón que EstadisticasRecursosProxyTest)
class ProxyReservaServiceTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ClienteRed cliente = ClienteRed.getInstancia();
    private final AtomicReference<JsonNode> solicitudRecibida =
            new AtomicReference<>();

    @AfterEach
    void limpiar() {
        cliente.cerrar();
    }

    // ---------- registrarReserva ----------

    @Test
    void registrarReservaEnviaResCrearSinIdNiNombreDelFuncionario() throws Exception {
        probarConServidor(
                solicitud -> respuestaExitosa(solicitud, reservaJson()),
                () -> new ProxyReservaService().registrarReserva(
                        "func1", "Ana Pérez", "Reunión",
                        LocalDate.of(2026, 11, 3), LocalTime.of(8, 0), LocalTime.of(10, 0),
                        List.of("CAT-000001", "CAT-000002"))
        );

        JsonNode solicitud = solicitudRecibida.get();
        JsonNode datos = solicitud.path("datos");

        assertEquals("RES_CREAR", solicitud.path("operacion").asText());
        assertEquals("Reunión", datos.path("nombreActividad").asText());
        assertEquals("2026-11-03", datos.path("fecha").asText());
        assertEquals("08:00", datos.path("horaInicio").asText());
        assertEquals("10:00", datos.path("horaFin").asText());
        assertEquals(2, datos.path("idsCategorias").size());
        assertEquals("CAT-000001", datos.path("idsCategorias").get(0).asText());
        assertEquals("CAT-000002", datos.path("idsCategorias").get(1).asText());

        // el servidor usa la sesión: estos dos campos no viajan
        assertFalse(datos.has("idFuncionario"));
        assertFalse(datos.has("nombreFuncionario"));
    }

    @Test
    void registrarReservaConFechaOHoraNulaLasEnviaComoNull() throws Exception {
        probarConServidor(
                solicitud -> respuestaExitosa(solicitud, reservaJson()),
                () -> new ProxyReservaService().registrarReserva(
                        "func1", "Ana Pérez", "Reunión",
                        null, null, null,
                        List.of("CAT-000001"))
        );

        JsonNode datos = solicitudRecibida.get().path("datos");

        // la clave viaja con valor null (no se lanza error: lo valida la Lógica en el Backend)
        assertTrue(datos.has("fecha"));
        assertTrue(datos.path("fecha").isNull());
        assertTrue(datos.has("horaInicio"));
        assertTrue(datos.path("horaInicio").isNull());
        assertTrue(datos.has("horaFin"));
        assertTrue(datos.path("horaFin").isNull());
    }

    @Test
    void registrarReservaConvierteLaRespuestaEnUnReservaDTOCompleto() throws Exception {
        ReservaDTO reserva = probarConServidor(
                solicitud -> respuestaExitosa(solicitud, reservaJson()),
                () -> new ProxyReservaService().registrarReserva(
                        "func1", "Ana Pérez", "Reunión",
                        LocalDate.of(2026, 11, 3), LocalTime.of(8, 0), LocalTime.of(10, 0),
                        List.of("CAT-000001"))
        );

        verificarReservaCompleta(reserva);
    }

    @Test
    void registrarReservaConExitoFalseLanzaIllegalArgumentConElMensajeExacto() throws Exception {
        IllegalArgumentException error = probarConServidor(
                solicitud -> respuestaError(solicitud, "No hay recursos disponibles en las categorías: Salas"),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new ProxyReservaService().registrarReserva(
                                "func1", "Ana Pérez", "Reunión",
                                LocalDate.of(2026, 11, 3), LocalTime.of(8, 0), LocalTime.of(10, 0),
                                List.of("CAT-000001")))
        );

        assertEquals("No hay recursos disponibles en las categorías: Salas", error.getMessage());
    }

    // ---------- cancelarReserva ----------

    @Test
    void cancelarReservaEnviaResCancelarYDevuelveTrueCuandoDatosEsTrue() throws Exception {
        boolean cancelada = probarConServidor(
                solicitud -> respuestaExitosa(solicitud, JSON.getNodeFactory().booleanNode(true)),
                () -> new ProxyReservaService().cancelarReserva("RES-000001")
        );

        JsonNode solicitud = solicitudRecibida.get();
        assertEquals("RES_CANCELAR", solicitud.path("operacion").asText());
        assertEquals("RES-000001", solicitud.path("datos").path("idReserva").asText());
        assertTrue(cancelada);
    }

    @Test
    void cancelarReservaDevuelveFalseCuandoDatosEsFalseONull() throws Exception {
        boolean conFalse = probarConServidor(
                solicitud -> respuestaExitosa(solicitud, JSON.getNodeFactory().booleanNode(false)),
                () -> new ProxyReservaService().cancelarReserva("RES-000001")
        );
        assertFalse(conFalse);

        boolean conNull = probarConServidor(
                solicitud -> respuestaExitosa(solicitud, JSON.nullNode()),
                () -> new ProxyReservaService().cancelarReserva("RES-000001")
        );
        assertFalse(conNull);
    }

    // ---------- buscarReservaID ----------

    @Test
    void buscarReservaIDEnviaResBuscarIdYDevuelveElReservaDTO() throws Exception {
        ReservaDTO reserva = probarConServidor(
                solicitud -> respuestaExitosa(solicitud, reservaJson()),
                () -> new ProxyReservaService().buscarReservaID("RES-000001")
        );

        JsonNode solicitud = solicitudRecibida.get();
        assertEquals("RES_BUSCAR_ID", solicitud.path("operacion").asText());
        assertEquals("RES-000001", solicitud.path("datos").path("idReserva").asText());
        verificarReservaCompleta(reserva);
    }

    @Test
    void buscarReservaIDConExitoFalseLanzaIllegalArgument() throws Exception {
        IllegalArgumentException error = probarConServidor(
                solicitud -> respuestaError(solicitud, "La reserva solicitada no fue encontrada"),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new ProxyReservaService().buscarReservaID("RES-999999"))
        );

        assertEquals("La reserva solicitada no fue encontrada", error.getMessage());
    }

    // ---------- buscarPorFuncionario ----------

    @Test
    void buscarPorFuncionarioEnviaResBuscarFuncionarioYDevuelveLaLista() throws Exception {
        List<ReservaDTO> reservas = probarConServidor(
                solicitud -> respuestaExitosa(solicitud, JSON.createArrayNode().add(reservaJson())),
                () -> new ProxyReservaService().buscarPorFuncionario("func1")
        );

        JsonNode solicitud = solicitudRecibida.get();
        assertEquals("RES_BUSCAR_FUNCIONARIO", solicitud.path("operacion").asText());
        assertEquals("func1", solicitud.path("datos").path("idFuncionario").asText());
        assertEquals(1, reservas.size());
        verificarReservaCompleta(reservas.get(0));
    }

    @Test
    void buscarPorFuncionarioAceptaListaVacia() throws Exception {
        List<ReservaDTO> reservas = probarConServidor(
                solicitud -> respuestaExitosa(solicitud, JSON.createArrayNode()),
                () -> new ProxyReservaService().buscarPorFuncionario("func1")
        );

        assertTrue(reservas.isEmpty());
    }

    // ---------- buscarPorPeriodo ----------

    @Test
    void buscarPorPeriodoEnviaResBuscarPeriodoYDevuelveLaLista() throws Exception {
        List<ReservaDTO> reservas = probarConServidor(
                solicitud -> respuestaExitosa(solicitud, JSON.createArrayNode().add(reservaJson())),
                () -> new ProxyReservaService().buscarPorPeriodo(
                        LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30))
        );

        JsonNode solicitud = solicitudRecibida.get();
        assertEquals("RES_BUSCAR_PERIODO", solicitud.path("operacion").asText());
        assertEquals("2026-11-01", solicitud.path("datos").path("fechaDesde").asText());
        assertEquals("2026-11-30", solicitud.path("datos").path("fechaHasta").asText());
        assertEquals(1, reservas.size());
        assertEquals("RES-000001", reservas.get(0).getIdReserva());
    }

    // ---------- respuestas que no se pueden leer ----------

    @Test
    void siDatosNoEsUnArregloLanzaIllegalStateConListaValida() throws Exception {
        IllegalStateException error = probarConServidor(
                solicitud -> respuestaExitosa(solicitud, reservaJson()),   // un objeto, no un arreglo
                () -> assertThrows(
                        IllegalStateException.class,
                        () -> new ProxyReservaService().buscarPorFuncionario("func1"))
        );

        assertTrue(error.getMessage().contains("lista válida"));
    }

    @Test
    void siUnaReservaNoSePuedeConvertirLanzaIllegalStateYNoIllegalArgument() throws Exception {
        // "fecha" con un texto que no es una fecha: la reserva no se puede convertir
        ObjectNode defectuosa = reservaJson();
        defectuosa.put("fecha", "no-es-una-fecha");

        IllegalStateException error = probarConServidor(
                solicitud -> respuestaExitosa(solicitud, JSON.createArrayNode().add(defectuosa)),
                () -> assertThrows(
                        IllegalStateException.class,
                        () -> new ProxyReservaService().buscarPorPeriodo(
                                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)))
        );

        // IllegalStateException no es subclase de IllegalArgumentException: no se confunde con un error de regla de negocio
        assertEquals("No fue posible convertir las reservas recibidas.", error.getMessage());
    }

    // ---------- formato de la solicitud ----------

    @Test
    void todasLasSolicitudesLlevanTipoSolicitudEIdSolicitud() throws Exception {
        ProxyReservaService proxy = new ProxyReservaService();

        // una por operación; la última solicitud recibida se revisa después de cada una
        verificarFormato(() -> proxy.registrarReserva("f", "n", "a",
                LocalDate.of(2026, 11, 3), LocalTime.of(8, 0), LocalTime.of(10, 0), List.of("C")),
                reservaJson());
        verificarFormato(() -> proxy.cancelarReserva("RES-000001"),
                JSON.getNodeFactory().booleanNode(true));
        verificarFormato(() -> proxy.buscarReservaID("RES-000001"), reservaJson());
        verificarFormato(() -> proxy.buscarPorFuncionario("func1"), JSON.createArrayNode());
        verificarFormato(() -> proxy.buscarPorPeriodo(
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)), JSON.createArrayNode());
    }

    private void verificarFormato(Callable<?> accion, JsonNode datosRespuesta) throws Exception {
        probarConServidor(solicitud -> respuestaExitosa(solicitud, datosRespuesta), accion);

        JsonNode solicitud = solicitudRecibida.get();
        assertEquals("SOLICITUD", solicitud.path("tipo").asText());
        assertNotNull(solicitud.get("idSolicitud"));
        assertFalse(solicitud.path("idSolicitud").asText().isBlank());
    }

    // ---------- apoyo ----------

    private void verificarReservaCompleta(ReservaDTO reserva) {
        assertNotNull(reserva);
        assertEquals("RES-000001", reserva.getIdReserva());
        assertEquals("func1", reserva.getIdFuncionario());
        assertEquals("Ana Pérez", reserva.getNombreFuncionario());
        assertEquals("Reunión", reserva.getNombreActividad());
        assertEquals(LocalDate.of(2026, 11, 3), reserva.getFecha());
        assertEquals(LocalTime.of(8, 0), reserva.getHoraInicio());
        assertEquals(LocalTime.of(10, 0), reserva.getHoraFin());
        assertEquals(EstadoReserva.ACTIVA, reserva.getEstado());

        assertEquals(1, reserva.getRecursosReservados().size());
        assertEquals("REC-0001", reserva.getRecursosReservados().get(0).getId());
        assertEquals("Sala 1", reserva.getRecursosReservados().get(0).getDescripcion());
        assertEquals("CAT-000001", reserva.getRecursosReservados().get(0).getCategoria().getId());
        assertEquals("Sala para 10 personas",
                reserva.getRecursosReservados().get(0).getCategoria().getDescripcion());
    }

    // Una reserva tal como la manda el servidor (las horas con segundos)
    private ObjectNode reservaJson() {
        ObjectNode reserva = JSON.createObjectNode();
        reserva.put("idReserva", "RES-000001");
        reserva.put("idFuncionario", "func1");
        reserva.put("nombreFuncionario", "Ana Pérez");
        reserva.put("nombreActividad", "Reunión");
        reserva.put("fecha", "2026-11-03");
        reserva.put("horaInicio", "08:00:00");
        reserva.put("horaFin", "10:00:00");
        reserva.put("estado", "ACTIVA");

        ArrayNode recursos = reserva.putArray("recursosReservados");
        ObjectNode recurso = recursos.addObject();
        recurso.put("id", "REC-0001");
        recurso.put("descripcion", "Sala 1");
        recurso.putObject("categoria")
                .put("id", "CAT-000001")
                .put("descripcion", "Sala para 10 personas");
        return reserva;
    }

    private ObjectNode respuestaExitosa(JsonNode solicitud, JsonNode datos) {
        ObjectNode respuesta = respuestaBase(solicitud);
        respuesta.put("exito", true);
        respuesta.set("datos", datos);
        return respuesta;
    }

    private ObjectNode respuestaError(JsonNode solicitud, String mensaje) {
        ObjectNode respuesta = respuestaBase(solicitud);
        respuesta.put("exito", false);
        respuesta.put("mensaje", mensaje);
        respuesta.putNull("datos");
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
