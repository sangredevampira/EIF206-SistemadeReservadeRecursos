package ui;

import Sistema.DTO.CategoriaDTO;
import Sistema.DTO.EstadoReserva;
import Sistema.DTO.ReservaDTO;
import Sistema.Service.ICategoriaService;
import Sistema.Service.IReservaService;
import Sistema.util.SessionManager;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

class ReservasOperacionesAsincronasTest {
    private static final CategoriaDTO SALAS = new CategoriaDTO("CAT-000001", "Salas");
    private static final ReservaDTO ORIGINAL = reserva("RES-000001", "Original", EstadoReserva.ACTIVA);
    private static final ReservaDTO NUEVA = reserva("RES-000002", "Nueva", EstadoReserva.ACTIVA);

    @BeforeAll
    static void iniciarJavaFX() throws Exception {
        CountDownLatch iniciada = new CountDownLatch(1);
        try {
            Platform.startup(() -> {
                Platform.setImplicitExit(false);
                iniciada.countDown();
            });
        } catch (IllegalStateException e) {
            Platform.runLater(iniciada::countDown);
        }
        assertTrue(iniciada.await(10, TimeUnit.SECONDS));
    }

    @BeforeEach
    void iniciarSesion() {
        SessionManager.getInstancia().iniciarSesion("FUNC-01", "FUNCIONARIO", null);
    }

    @AfterEach
    void cerrarSesion() {
        SessionManager.getInstancia().cerrarSesion();
    }

    @Test
    void errorDeRecargaConservaReservasValidas() throws Exception {
        Servicio servicio = new Servicio();
        Vista vista = vista(servicio);
        servicio.fallar("buscarPorFuncionario", new IllegalStateException("red"));

        fx(() -> {
            vista.controlador().cargarDatosDesdeSistema();
            return null;
        });
        esperarAlertas(vista, 1);

        verificarTabla(vista, List.of(ORIGINAL));
        assertEquals("No se pudo comunicar con el servidor. Intente de nuevo.",
                vista.controlador().alertas.getFirst().mensaje());
    }

    @Test
    void recargaValidaSinReservasLimpiaTablaSinAlerta() throws Exception {
        Servicio servicio = new Servicio();
        Vista vista = vista(servicio);
        servicio.fallar(
                "buscarPorFuncionario",
                new IllegalArgumentException("El usuario no tiene reservas registradas")
        );

        fx(() -> {
            vista.controlador().cargarDatosDesdeSistema();
            return null;
        });

        verificarTabla(vista, List.of());
        assertTrue(vista.controlador().alertas.isEmpty());
    }

    @Test
    void registroExitosoUsaInstantaneaYRecargaAsincronicamente() throws Exception {
        Servicio servicio = new Servicio();
        Vista vista = vista(servicio);
        llenarFormulario(vista);
        Puerta registro = servicio.pausar("registrarReserva", NUEVA);
        servicio.responder("buscarPorFuncionario", List.of(ORIGINAL, NUEVA));

        pulsar(vista, "btnReservar");
        registro.esperarInicio();
        fx(() -> {
            assertControlesEscrituraBloqueados(vista);
            vista.texto("txtActividad").setText("Cambio tardío");
            vista.fecha().setValue(LocalDate.now().plusDays(5));
            return null;
        });
        registro.liberar();

        esperarOperacionTerminada(vista);
        verificarTabla(vista, List.of(ORIGINAL, NUEVA));
        assertEquals("Actividad capturada", servicio.ultimoRegistro[2]);
        assertEquals(LocalDate.now().plusDays(1), servicio.ultimoRegistro[3]);
        assertEquals(List.of("CAT-000001"), servicio.ultimoRegistro[6]);
        assertEquals(Alert.AlertType.INFORMATION, vista.controlador().alertas.getFirst().tipo());
        fx(() -> {
            assertEquals("", vista.texto("txtActividad").getText());
            assertNull(vista.fecha().getValue());
            return null;
        });
    }

    @Test
    void conflictoDeDisponibilidadConservaFormularioYMensajeBackend() throws Exception {
        Servicio servicio = new Servicio();
        Vista vista = vista(servicio);
        llenarFormulario(vista);
        String conflicto = "No hay recursos disponibles en las categorías: Salas";
        servicio.fallar("registrarReserva", new IllegalArgumentException(conflicto));

        pulsar(vista, "btnReservar");
        esperarAlertas(vista, 1);
        esperarOperacionTerminada(vista);

        assertEquals(conflicto, vista.controlador().alertas.getFirst().mensaje());
        verificarFormulario(vista);
        verificarTabla(vista, List.of(ORIGINAL));
    }

    @Test
    void errorConexionAlRegistrarConservaFormularioYRestauraControles() throws Exception {
        Servicio servicio = new Servicio();
        Vista vista = vista(servicio);
        llenarFormulario(vista);
        servicio.fallar("registrarReserva", new IllegalStateException("red"));

        pulsar(vista, "btnReservar");
        esperarAlertas(vista, 1);
        esperarOperacionTerminada(vista);

        assertEquals("No se pudo comunicar con el servidor. Intente de nuevo.",
                vista.controlador().alertas.getFirst().mensaje());
        verificarFormulario(vista);
    }

    @Test
    void registroLentoNoBloqueaJavaFXNiPermiteDuplicados() throws Exception {
        Servicio servicio = new Servicio();
        Vista vista = vista(servicio);
        llenarFormulario(vista);
        Puerta registro = servicio.pausar(
                "registrarReserva",
                new IllegalArgumentException("Rechazado")
        );

        pulsar(vista, "btnReservar");
        registro.esperarInicio();
        CountDownLatch pulso = new CountDownLatch(1);
        Platform.runLater(pulso::countDown);
        assertTrue(pulso.await(1, TimeUnit.SECONDS));
        fx(() -> {
            assertControlesEscrituraBloqueados(vista);
            vista.boton("btnReservar").getOnAction().handle(new ActionEvent());
            return null;
        });
        assertEquals(1, servicio.cantidad("registrarReserva"));

        registro.liberar();
        esperarAlertas(vista, 1);
        esperarOperacionTerminada(vista);
    }

    @Test
    void cancelacionExitosaRecargaDespuesDeConfirmacion() throws Exception {
        Servicio servicio = new Servicio();
        Vista vista = vista(servicio);
        seleccionar(vista, ORIGINAL);
        ReservaDTO cancelada = reserva("RES-000001", "Original", EstadoReserva.CANCELADA);
        servicio.responder("cancelarReserva", true);
        servicio.responder("buscarPorFuncionario", List.of(cancelada));

        pulsar(vista, "btnCancelarReserva");
        esperarOperacionTerminada(vista);

        assertEquals("RES-000001", servicio.ultimoIdCancelado);
        verificarTabla(vista, List.of(cancelada));
        assertEquals(Alert.AlertType.INFORMATION, vista.controlador().alertas.getFirst().tipo());
    }

    @Test
    void cancelacionRechazadaConservaSeleccionYDatos() throws Exception {
        Servicio servicio = new Servicio();
        Vista vista = vista(servicio);
        llenarFormulario(vista);
        seleccionar(vista, ORIGINAL);
        servicio.fallar(
                "cancelarReserva",
                new IllegalArgumentException("Esta reserva ya se encuentra cancelada")
        );

        pulsar(vista, "btnCancelarReserva");
        esperarAlertas(vista, 1);
        esperarOperacionTerminada(vista);

        fx(() -> {
            assertSame(ORIGINAL, vista.tabla().getSelectionModel().getSelectedItem());
            assertFalse(vista.boton("btnCancelarReserva").isDisabled());
            return null;
        });
        verificarFormulario(vista);
        verificarTabla(vista, List.of(ORIGINAL));
    }

    @Test
    void respuestaFalseDeCancelacionNoCambiaLaTabla() throws Exception {
        Servicio servicio = new Servicio();
        Vista vista = vista(servicio);
        seleccionar(vista, ORIGINAL);
        servicio.responder("cancelarReserva", false);

        pulsar(vista, "btnCancelarReserva");
        esperarAlertas(vista, 1);
        esperarOperacionTerminada(vista);

        assertEquals("El servidor no confirmó la cancelación de la reserva.",
                vista.controlador().alertas.getFirst().mensaje());
        verificarTabla(vista, List.of(ORIGINAL));
    }

    @Test
    void cancelacionLentaNoCambiaTablaNiPermiteDuplicados() throws Exception {
        Servicio servicio = new Servicio();
        Vista vista = vista(servicio);
        seleccionar(vista, ORIGINAL);
        Puerta cancelacion = servicio.pausar(
                "cancelarReserva",
                new IllegalArgumentException("Rechazada")
        );

        pulsar(vista, "btnCancelarReserva");
        cancelacion.esperarInicio();
        fx(() -> {
            assertControlesEscrituraBloqueados(vista);
            assertEquals(List.of(ORIGINAL), vista.tabla().getItems());
            vista.boton("btnCancelarReserva").getOnAction().handle(new ActionEvent());
            return null;
        });
        assertEquals(1, servicio.cantidad("cancelarReserva"));

        cancelacion.liberar();
        esperarAlertas(vista, 1);
        esperarOperacionTerminada(vista);
    }

    @Test
    void registroExitosoYFalloDeRecargaConservanTablaPrevia() throws Exception {
        Servicio servicio = new Servicio();
        Vista vista = vista(servicio);
        llenarFormulario(vista);
        servicio.responder("registrarReserva", NUEVA);
        servicio.fallar("buscarPorFuncionario", new IllegalStateException("red"));

        pulsar(vista, "btnReservar");
        esperarAlertas(vista, 2);
        esperarOperacionTerminada(vista);

        assertEquals(Alert.AlertType.INFORMATION, vista.controlador().alertas.getFirst().tipo());
        assertEquals(Alert.AlertType.ERROR, vista.controlador().alertas.getLast().tipo());
        verificarTabla(vista, List.of(ORIGINAL));
    }

    @Test
    void cargaAntiguaNoSobrescribeNiDesbloqueaRegistroVigente() throws Exception {
        Servicio servicio = new Servicio();
        Vista vista = vista(servicio);
        llenarFormulario(vista);
        Puerta carga = servicio.pausar("buscarPorFuncionario", List.of(NUEVA));
        fx(() -> {
            vista.controlador().cargarDatosDesdeSistema();
            return null;
        });
        carga.esperarInicio();
        Puerta registro = servicio.pausar(
                "registrarReserva",
                new IllegalArgumentException("Rechazado")
        );
        pulsar(vista, "btnReservar");
        registro.esperarInicio();

        carga.liberar();
        fx(() -> {
            assertControlesEscrituraBloqueados(vista);
            assertEquals(List.of(ORIGINAL), vista.tabla().getItems());
            return null;
        });

        registro.liberar();
        esperarAlertas(vista, 1);
        esperarOperacionTerminada(vista);
        verificarTabla(vista, List.of(ORIGINAL));
    }

    @Test
    void errorTardioDeCargaInvalidadaNoMuestraAlerta() throws Exception {
        Servicio servicio = new Servicio();
        Vista vista = vista(servicio);
        Puerta carga = servicio.pausar(
                "buscarPorFuncionario",
                new IllegalStateException("error tardío")
        );
        fx(() -> {
            vista.controlador().cargarDatosDesdeSistema();
            return null;
        });
        carga.esperarInicio();
        servicio.responder("buscarPorFuncionario", List.of(NUEVA));
        fx(() -> {
            vista.controlador().cargarDatosDesdeSistema();
            return null;
        });
        verificarTabla(vista, List.of(NUEVA));

        carga.liberar();
        verificarTabla(vista, List.of(NUEVA));
        assertTrue(vista.controlador().alertas.isEmpty());
    }

    private static Vista vista(Servicio servicio) throws Exception {
        Vista vista = fx(() -> {
            Controlador controlador = new Controlador(
                    servicio.proxy(IReservaService.class),
                    servicio.proxy(ICategoriaService.class)
            );
            FXMLLoader cargador = new FXMLLoader(
                    ReservasOperacionesAsincronasTest.class.getResource(
                            "/Sistema/view/ReservasView.fxml"
                    )
            );
            cargador.setControllerFactory(tipo -> controlador);
            cargador.load();
            return new Vista(cargador, controlador);
        });
        esperarFx(() -> !vista.listaCategorias().isDisabled()
                && !vista.tabla().isDisabled()
                && vista.listaCategorias().getItems().equals(List.of(SALAS))
                && vista.tabla().getItems().equals(List.of(ORIGINAL)));
        return vista;
    }

    private static void llenarFormulario(Vista vista) throws Exception {
        fx(() -> {
            vista.texto("txtActividad").setText("Actividad capturada");
            vista.fecha().setValue(LocalDate.now().plusDays(1));
            vista.hora("cbHoraInicio").setValue(LocalTime.of(8, 0));
            vista.hora("cbHoraFin").setValue(LocalTime.of(9, 0));
            vista.listaCategorias().getSelectionModel().select(SALAS);
            return null;
        });
    }

    private static void verificarFormulario(Vista vista) throws Exception {
        fx(() -> {
            assertEquals("Actividad capturada", vista.texto("txtActividad").getText());
            assertEquals(LocalDate.now().plusDays(1), vista.fecha().getValue());
            assertEquals(LocalTime.of(8, 0), vista.hora("cbHoraInicio").getValue());
            assertEquals(LocalTime.of(9, 0), vista.hora("cbHoraFin").getValue());
            assertEquals(List.of(SALAS), vista.listaCategorias().getSelectionModel().getSelectedItems());
            return null;
        });
    }

    private static void seleccionar(Vista vista, ReservaDTO reserva) throws Exception {
        fx(() -> {
            vista.tabla().getSelectionModel().select(reserva);
            return null;
        });
    }

    private static void pulsar(Vista vista, String id) throws Exception {
        fx(() -> {
            vista.boton(id).fire();
            return null;
        });
    }

    private static void esperarOperacionTerminada(Vista vista) throws Exception {
        esperarFx(() -> !vista.boton("btnReservar").isDisabled()
                && !vista.tabla().isDisabled()
                && !vista.texto("txtActividad").isDisabled());
    }

    private static void esperarAlertas(Vista vista, int cantidad) throws Exception {
        esperar(() -> vista.controlador().alertas.size() == cantidad);
    }

    private static void verificarTabla(Vista vista, List<ReservaDTO> reservas) throws Exception {
        esperarFx(() -> !vista.tabla().isDisabled() && vista.tabla().getItems().equals(reservas));
    }

    private static void assertControlesEscrituraBloqueados(Vista vista) {
        assertTrue(vista.boton("btnReservar").isDisabled());
        assertTrue(vista.boton("btnCancelarReserva").isDisabled());
        assertTrue(vista.boton("btnLimpiar").isDisabled());
        assertTrue(vista.boton("btnImprimirReservas").isDisabled());
        assertTrue(vista.tabla().isDisabled());
        assertTrue(vista.texto("txtActividad").isDisabled());
        assertTrue(vista.fecha().isDisabled());
        assertTrue(vista.listaCategorias().isDisabled());
    }

    private static ReservaDTO reserva(String id, String actividad, EstadoReserva estado) {
        return new ReservaDTO(
                id,
                "FUNC-01",
                "Ana",
                actividad,
                LocalDate.now().plusDays(1),
                LocalTime.of(8, 0),
                LocalTime.of(9, 0),
                List.of(),
                estado
        );
    }

    private static void esperarFx(BooleanSupplier condicion) throws Exception {
        esperar(() -> fx(condicion::getAsBoolean));
    }

    private static void esperar(Callable<Boolean> condicion) throws Exception {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (System.nanoTime() < limite) {
            if (condicion.call()) return;
            Thread.sleep(10);
        }
        fail("No se alcanzó el estado esperado.");
    }

    private static <T> T fx(Callable<T> accion) throws Exception {
        if (Platform.isFxApplicationThread()) return accion.call();
        FutureTask<T> tarea = new FutureTask<>(accion);
        Platform.runLater(tarea);
        return tarea.get(10, TimeUnit.SECONDS);
    }

    private record AlertaCapturada(Alert.AlertType tipo, String titulo, String mensaje) {
    }

    private static final class Controlador extends ReservasViewController {
        private final List<AlertaCapturada> alertas = new CopyOnWriteArrayList<>();

        private Controlador(IReservaService reservas, ICategoriaService categorias) {
            super(reservas, categorias);
        }

        @Override
        public void cargarTabla(List<ReservaDTO> reservas) {
            assertTrue(Platform.isFxApplicationThread());
            super.cargarTabla(reservas);
        }

        @Override
        void mostrarAlerta(Alert.AlertType tipo, String titulo, String mensaje) {
            assertTrue(Platform.isFxApplicationThread());
            alertas.add(new AlertaCapturada(tipo, titulo, mensaje));
        }
    }

    private record Vista(FXMLLoader cargador, Controlador controlador) {
        Button boton(String id) {
            return (Button) cargador.getNamespace().get(id);
        }

        TextField texto(String id) {
            return (TextField) cargador.getNamespace().get(id);
        }

        DatePicker fecha() {
            return (DatePicker) cargador.getNamespace().get("dpFecha");
        }

        @SuppressWarnings("unchecked")
        ComboBox<LocalTime> hora(String id) {
            return (ComboBox<LocalTime>) cargador.getNamespace().get(id);
        }

        @SuppressWarnings("unchecked")
        ListView<CategoriaDTO> listaCategorias() {
            return (ListView<CategoriaDTO>) cargador.getNamespace().get("lvCategorias");
        }

        @SuppressWarnings("unchecked")
        TableView<ReservaDTO> tabla() {
            return (TableView<ReservaDTO>) cargador.getNamespace().get("tvMisReservas");
        }
    }

    private static final class Servicio {
        private final Map<String, ConcurrentLinkedQueue<Callable<Object>>> respuestas =
                new ConcurrentHashMap<>();
        private final Map<String, AtomicInteger> llamadas = new ConcurrentHashMap<>();
        private volatile Object[] ultimoRegistro;
        private volatile String ultimoIdCancelado;

        private void responder(String metodo, Object valor) {
            agregar(metodo, () -> valor);
        }

        private void fallar(String metodo, RuntimeException error) {
            agregar(metodo, () -> {
                throw error;
            });
        }

        private Puerta pausar(String metodo, Object resultado) {
            Puerta puerta = new Puerta(resultado);
            agregar(metodo, puerta::obtener);
            return puerta;
        }

        private void agregar(String metodo, Callable<Object> respuesta) {
            respuestas.computeIfAbsent(metodo, clave -> new ConcurrentLinkedQueue<>())
                    .add(respuesta);
        }

        private int cantidad(String metodo) {
            return llamadas.getOrDefault(metodo, new AtomicInteger()).get();
        }

        private <T> T proxy(Class<T> interfaz) {
            return interfaz.cast(Proxy.newProxyInstance(
                    interfaz.getClassLoader(),
                    new Class<?>[]{interfaz},
                    (proxy, metodo, argumentos) -> {
                        assertFalse(Platform.isFxApplicationThread(), metodo.getName());
                        llamadas.computeIfAbsent(metodo.getName(), clave -> new AtomicInteger())
                                .incrementAndGet();
                        if (metodo.getName().equals("registrarReserva")) {
                            ultimoRegistro = argumentos.clone();
                        }
                        if (metodo.getName().equals("cancelarReserva")) {
                            ultimoIdCancelado = (String) argumentos[0];
                        }

                        ConcurrentLinkedQueue<Callable<Object>> cola = respuestas.get(metodo.getName());
                        Callable<Object> respuesta = cola == null ? null : cola.poll();
                        if (respuesta != null) return respuesta.call();

                        return switch (metodo.getName()) {
                            case "listarTodos" -> List.of(SALAS);
                            case "buscarPorFuncionario" -> List.of(ORIGINAL);
                            case "registrarReserva" -> NUEVA;
                            case "cancelarReserva" -> true;
                            default -> null;
                        };
                    }
            ));
        }
    }

    private static final class Puerta {
        private final Object resultado;
        private final CountDownLatch iniciada = new CountDownLatch(1);
        private final CountDownLatch permitida = new CountDownLatch(1);
        private volatile Thread hilo;

        private Puerta(Object resultado) {
            this.resultado = resultado;
        }

        private Object obtener() throws Exception {
            hilo = Thread.currentThread();
            iniciada.countDown();
            assertTrue(permitida.await(10, TimeUnit.SECONDS));
            if (resultado instanceof RuntimeException error) throw error;
            return resultado;
        }

        private void esperarInicio() throws InterruptedException {
            assertTrue(iniciada.await(5, TimeUnit.SECONDS));
        }

        private void liberar() throws Exception {
            permitida.countDown();
            hilo.join(5_000);
            assertFalse(hilo.isAlive());
            fx(() -> null);
        }
    }
}
