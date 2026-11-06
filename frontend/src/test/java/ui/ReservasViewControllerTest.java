package ui;

import Sistema.DTO.CategoriaDTO;
import Sistema.DTO.EstadoReserva;
import Sistema.DTO.ReservaDTO;
import Sistema.DTO.ResultadoOperacionDTO;
import Sistema.Service.ICategoriaService;
import Sistema.Service.IReservaService;
import Sistema.util.SessionManager;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.TableView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.GraphicsEnvironment;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class ReservasViewControllerTest {

    @BeforeAll
    static void iniciarJavaFX() throws Exception {
        assumeFalse(
                GraphicsEnvironment.isHeadless(),
                "La carga FXML requiere un entorno gráfico para JavaFX."
        );

        CountDownLatch iniciado = new CountDownLatch(1);
        try {
            Platform.startup(() -> {
                Platform.setImplicitExit(false);
                iniciado.countDown();
            });
        } catch (IllegalStateException e) {
            Platform.runLater(iniciado::countDown);
        }

        assertTrue(iniciado.await(10, TimeUnit.SECONDS));
    }

    @BeforeEach
    void iniciarSesion() {
        SessionManager.getInstancia().iniciarSesion(
                "FUNC-01",
                "FUNCIONARIO",
                null
        );
    }

    @AfterEach
    void cerrarSesion() {
        SessionManager.getInstancia().cerrarSesion();
    }

    @Test
    void cargaCorrectaMuestraLasReservasRecibidas() throws Exception {
        ReservaDTO reserva = reserva("RES-000001", "Planificación");
        ReservaServiceFalso servicio = new ReservaServiceFalso(() -> List.of(reserva));
        VistaCargada vista = cargarVista(servicio, new CategoriaServiceFalso());

        vista.controlador().esperarCargaExitosa();

        ejecutarEnJavaFX(() -> {
            assertEquals(List.of(reserva), tabla(vista).getItems());
            assertTrue(vista.controlador().alertas.isEmpty());
            return null;
        });
    }

    @Test
    void cargaCorrectaSinRegistrosDejaLaTablaVaciaSinError() throws Exception {
        ReservaServiceFalso servicio = new ReservaServiceFalso(List::of);
        VistaCargada vista = cargarVista(servicio, new CategoriaServiceFalso());

        vista.controlador().esperarCargaExitosa();

        ejecutarEnJavaFX(() -> {
            assertTrue(tabla(vista).getItems().isEmpty());
            assertTrue(vista.controlador().alertas.isEmpty());
            return null;
        });
    }

    @Test
    void respuestaSinReservasDelServidorTambienEsAusenciaValida() throws Exception {
        ReservaServiceFalso servicio = new ReservaServiceFalso(() -> {
            throw new IllegalArgumentException(
                    "El usuario no tiene reservas registradas"
            );
        });
        VistaCargada vista = cargarVista(servicio, new CategoriaServiceFalso());

        vista.controlador().esperarCargaExitosa();

        ejecutarEnJavaFX(() -> {
            assertTrue(tabla(vista).getItems().isEmpty());
            assertTrue(vista.controlador().alertas.isEmpty());
            return null;
        });
    }

    @Test
    void errorDeComunicacionMuestraMensajeVisibleYNoSimulaResultadoValido()
            throws Exception {
        ReservaServiceFalso servicio = new ReservaServiceFalso(() -> {
            throw new IllegalStateException("No fue posible comunicarse");
        });
        VistaCargada vista = cargarVista(servicio, new CategoriaServiceFalso());

        esperarAlertas(vista.controlador(), 1);

        ejecutarEnJavaFX(() -> {
            assertTrue(tabla(vista).getItems().isEmpty());
            assertEquals(
                    new AlertaCapturada(
                            AlertType.ERROR,
                            "Error de conexión",
                            "No se pudo comunicar con el servidor. Intente de nuevo."
                    ),
                    vista.controlador().alertas.get(0)
            );
            return null;
        });
    }

    @Test
    void errorDevueltoPorServidorMuestraSuMensaje() throws Exception {
        ReservaServiceFalso servicio = new ReservaServiceFalso(() -> {
            throw new IllegalArgumentException("El servidor rechazó la consulta.");
        });
        VistaCargada vista = cargarVista(servicio, new CategoriaServiceFalso());

        esperarAlertas(vista.controlador(), 1);

        ejecutarEnJavaFX(() -> {
            assertTrue(tabla(vista).getItems().isEmpty());
            assertEquals(
                    new AlertaCapturada(
                            AlertType.ERROR,
                            "No se pudieron cargar las reservas",
                            "El servidor rechazó la consulta."
                    ),
                    vista.controlador().alertas.get(0)
            );
            return null;
        });
    }

    @Test
    void fallosSimultaneosDeLaCargaInicialMuestranUnSoloMensaje()
            throws Exception {
        ReservaServiceFalso reservas = new ReservaServiceFalso(() -> {
            throw new IllegalStateException("Servidor no disponible");
        });
        CategoriaServiceFalso categorias = new CategoriaServiceFalso(() -> {
            throw new IllegalStateException("Servidor no disponible");
        });
        VistaCargada vista = cargarVista(reservas, categorias);

        reservas.esperarConsulta();
        categorias.esperarConsulta();
        esperarAlertas(vista.controlador(), 1);
        esperarEventosJavaFX();

        assertEquals(1, vista.controlador().alertas.size());
    }

    @Test
    void respuestaAnteriorNoReemplazaUnaCargaMasReciente() throws Exception {
        RespuestaControlada primera = RespuestaControlada.error(
                new IllegalStateException("Error tardío")
        );
        ReservaDTO vigente = reserva("RES-000002", "Carga vigente");
        RespuestaControlada segunda = RespuestaControlada.exito(List.of(vigente));
        ReservaServiceFalso servicio = new ReservaServiceFalso(
                primera::obtener,
                segunda::obtener
        );
        VistaCargada vista = cargarVista(servicio, new CategoriaServiceFalso());
        primera.esperarInicio();

        ejecutarEnJavaFX(() -> {
            vista.controlador().cargarDatosDesdeSistema();
            return null;
        });
        segunda.esperarInicio();
        segunda.responder();
        vista.controlador().esperarCargaExitosa();

        primera.responder();
        primera.esperarFinalizacion();
        esperarEventosJavaFX();

        ejecutarEnJavaFX(() -> {
            assertEquals(List.of(vigente), tabla(vista).getItems());
            assertTrue(vista.controlador().alertas.isEmpty());
            return null;
        });
    }

    private static VistaCargada cargarVista(
            IReservaService reservas,
            ICategoriaService categorias
    ) throws Exception {
        ControladorPrueba controlador = new ControladorPrueba(reservas, categorias);
        return ejecutarEnJavaFX(() -> {
            URL recurso = ReservasViewControllerTest.class.getResource(
                    "/Sistema/view/ReservasView.fxml"
            );
            FXMLLoader cargador = new FXMLLoader(recurso);
            cargador.setControllerFactory(tipo -> controlador);
            Parent raiz = cargador.load();
            return new VistaCargada(cargador, raiz, controlador);
        });
    }

    @SuppressWarnings("unchecked")
    private static TableView<ReservaDTO> tabla(VistaCargada vista) {
        return (TableView<ReservaDTO>) assertInstanceOf(
                TableView.class,
                vista.cargador().getNamespace().get("tvMisReservas")
        );
    }

    private static ReservaDTO reserva(String id, String actividad) {
        return new ReservaDTO(
                id,
                "FUNC-01",
                "Ana",
                actividad,
                LocalDate.now().plusDays(1),
                LocalTime.of(8, 0),
                LocalTime.of(9, 0),
                List.of(),
                EstadoReserva.ACTIVA
        );
    }

    private static void esperarAlertas(ControladorPrueba controlador, int cantidad)
            throws Exception {
        esperarHasta(() -> controlador.alertas.size() == cantidad);
    }

    private static void esperarEventosJavaFX() throws Exception {
        Thread.sleep(100);
        ejecutarEnJavaFX(() -> null);
    }

    private static void esperarHasta(Callable<Boolean> condicion) throws Exception {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < limite) {
            if (condicion.call()) return;
            Thread.sleep(20);
        }
        fail("La interfaz no alcanzó el estado esperado.");
    }

    private static <T> T ejecutarEnJavaFX(Callable<T> accion) throws Exception {
        if (Platform.isFxApplicationThread()) return accion.call();
        FutureTask<T> tarea = new FutureTask<>(accion);
        Platform.runLater(tarea);
        return tarea.get(10, TimeUnit.SECONDS);
    }

    private static final class ControladorPrueba extends ReservasViewController {
        private final List<AlertaCapturada> alertas = new CopyOnWriteArrayList<>();
        private final CountDownLatch cargaExitosa = new CountDownLatch(1);

        private ControladorPrueba(
                IReservaService reservas,
                ICategoriaService categorias
        ) {
            super(reservas, categorias);
        }

        @Override
        public void cargarTabla(List<ReservaDTO> reservas) {
            super.cargarTabla(reservas);
            cargaExitosa.countDown();
        }

        @Override
        void mostrarAlerta(AlertType tipo, String titulo, String mensaje) {
            alertas.add(new AlertaCapturada(tipo, titulo, mensaje));
        }

        private void esperarCargaExitosa() throws InterruptedException {
            assertTrue(cargaExitosa.await(5, TimeUnit.SECONDS));
        }
    }

    private static final class ReservaServiceFalso implements IReservaService {
        private final List<Callable<List<ReservaDTO>>> respuestas;
        private final AtomicInteger siguiente = new AtomicInteger();
        private final CountDownLatch consultada = new CountDownLatch(1);

        @SafeVarargs
        private ReservaServiceFalso(Callable<List<ReservaDTO>>... respuestas) {
            this.respuestas = List.of(respuestas);
        }

        @Override
        public List<ReservaDTO> buscarPorFuncionario(String idFuncionario) {
            consultada.countDown();
            try {
                return respuestas.get(siguiente.getAndIncrement()).call();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        private void esperarConsulta() throws InterruptedException {
            assertTrue(consultada.await(5, TimeUnit.SECONDS));
        }

        @Override
        public ReservaDTO registrarReserva(
                String idFuncionario,
                String nombreFuncionario,
                String nombreActividad,
                LocalDate fecha,
                LocalTime horaInicio,
                LocalTime horaFin,
                List<String> idsCategoriasSolicitadas
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean cancelarReserva(String idReserva) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ReservaDTO buscarReservaID(String id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<ReservaDTO> buscarPorPeriodo(
                LocalDate fechaDesde,
                LocalDate fechaHasta
        ) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class CategoriaServiceFalso implements ICategoriaService {
        private final Callable<List<CategoriaDTO>> respuesta;
        private final CountDownLatch consultada = new CountDownLatch(1);

        private CategoriaServiceFalso() {
            this(() -> List.of(new CategoriaDTO("CAT-000001", "Salas")));
        }

        private CategoriaServiceFalso(Callable<List<CategoriaDTO>> respuesta) {
            this.respuesta = respuesta;
        }

        @Override
        public List<CategoriaDTO> listarTodos() {
            consultada.countDown();
            try {
                return respuesta.call();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        private void esperarConsulta() throws InterruptedException {
            assertTrue(consultada.await(5, TimeUnit.SECONDS));
        }

        @Override
        public ResultadoOperacionDTO registrar(CategoriaDTO categoria) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ResultadoOperacionDTO modificar(CategoriaDTO categoria) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ResultadoOperacionDTO eliminar(String id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<CategoriaDTO> buscar(String descripcionParcial) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class RespuestaControlada {
        private final List<ReservaDTO> resultado;
        private final RuntimeException error;
        private final CountDownLatch iniciada = new CountDownLatch(1);
        private final CountDownLatch permitida = new CountDownLatch(1);
        private final CountDownLatch finalizada = new CountDownLatch(1);

        private RespuestaControlada(
                List<ReservaDTO> resultado,
                RuntimeException error
        ) {
            this.resultado = resultado;
            this.error = error;
        }

        private static RespuestaControlada exito(List<ReservaDTO> resultado) {
            return new RespuestaControlada(resultado, null);
        }

        private static RespuestaControlada error(RuntimeException error) {
            return new RespuestaControlada(null, error);
        }

        private List<ReservaDTO> obtener() throws InterruptedException {
            iniciada.countDown();
            try {
                assertTrue(permitida.await(5, TimeUnit.SECONDS));
                if (error != null) throw error;
                return resultado;
            } finally {
                finalizada.countDown();
            }
        }

        private void esperarInicio() throws InterruptedException {
            assertTrue(iniciada.await(5, TimeUnit.SECONDS));
        }

        private void responder() {
            permitida.countDown();
        }

        private void esperarFinalizacion() throws InterruptedException {
            assertTrue(finalizada.await(5, TimeUnit.SECONDS));
        }
    }

    private record AlertaCapturada(AlertType tipo, String titulo, String mensaje) {
    }

    private record VistaCargada(
            FXMLLoader cargador,
            Parent raiz,
            ControladorPrueba controlador
    ) {
    }
}
