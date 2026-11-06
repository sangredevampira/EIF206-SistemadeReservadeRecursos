package ui;

import Sistema.DTO.Funcionario;
import Sistema.DTO.ResultadoOperacionDTO;
import Sistema.Service.IFuncionarioService;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.awt.GraphicsEnvironment;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class FuncionarioControllerTest {

    private static final Funcionario ANA =
            new Funcionario("100", "Ana", "1111-1111");
    private static final Funcionario LUIS =
            new Funcionario("200", "Luis", "2222-2222");

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

    @Test
    void cargaInicialExitosaMuestraFuncionarios() throws Exception {
        ServicioFalso servicio = servicioConCargaInicial(List.of(ANA, LUIS));
        VistaFuncionarios vista = cargarVista(servicio);

        esperarConsultaTerminada(vista);

        assertEquals(List.of(ANA, LUIS), elementos(vista));
        assertTrue(vista.controlador().alertas.isEmpty());
        assertFalse(servicio.listarEnJavaFX);
    }

    @Test
    void busquedaValidaSinResultadosDejaTablaVaciaSinError() throws Exception {
        ServicioFalso servicio = servicioConCargaInicial(List.of(ANA));
        servicio.busquedas.add(List::of);
        VistaFuncionarios vista = cargarVista(servicio);
        esperarConsultaTerminada(vista);

        buscar(vista, "999", "");
        esperarTabla(vista, List.of());

        assertEquals("", textoMensaje(vista));
        assertTrue(vista.controlador().alertas.isEmpty());
    }

    @Test
    void errorDeComunicacionEnBusquedaConservaTablaYMuestraAlerta()
            throws Exception {
        ServicioFalso servicio = servicioConCargaInicial(List.of(ANA));
        servicio.busquedas.add(() -> {
            throw new IllegalStateException("Sin conexión");
        });
        VistaFuncionarios vista = cargarVista(servicio);
        esperarConsultaTerminada(vista);

        buscar(vista, "100", "");
        esperarAlertas(vista.controlador(), 1);

        assertEquals(List.of(ANA), elementos(vista));
        assertEquals(
                new AlertaCapturada(
                        "Error de conexión",
                        FuncionarioController.MENSAJE_SIN_CONEXION_BUSCAR
                ),
                vista.controlador().alertas.get(0)
        );
        verificarControlesConsultaRestaurados(vista);
    }

    @Test
    void errorDelServidorEnBusquedaMuestraMensajeSinVaciarTabla()
            throws Exception {
        ServicioFalso servicio = servicioConCargaInicial(List.of(ANA));
        servicio.busquedas.add(() -> {
            throw new IllegalArgumentException("No tiene permiso para esta operación.");
        });
        VistaFuncionarios vista = cargarVista(servicio);
        esperarConsultaTerminada(vista);

        buscar(vista, "100", "");
        esperarMensaje(vista, "No tiene permiso para esta operación.");

        assertEquals(List.of(ANA), elementos(vista));
        verificarControlesConsultaRestaurados(vista);
    }

    @Test
    void registroExitosoLimpiaFormularioYRecargaListado() throws Exception {
        Funcionario creado = new Funcionario("300", "Eva", "3333-3333");
        ServicioFalso servicio = servicioConCargaInicial(List.of(ANA));
        servicio.registros.add(ResultadoOperacionDTO::exito);
        servicio.listados.add(() -> List.of(ANA, creado));
        VistaFuncionarios vista = cargarVista(servicio);
        esperarConsultaTerminada(vista);

        llenarFormulario(vista, creado);
        ejecutarEnJavaFX(() -> {
            boton(vista, "btnGuardar").fire();
            return null;
        });
        esperarOperacionTerminada(vista, "Funcionario registrado correctamente.");

        assertEquals(List.of(ANA, creado), elementos(vista));
        assertEquals("300", servicio.registrado.getId());
        assertFalse(servicio.operacionEnJavaFX);
        verificarFormularioVacio(vista);
    }

    @Test
    void registroFallidoConservaFormularioYRestaurarControles() throws Exception {
        Funcionario nuevo = new Funcionario("300", "Eva", "3333-3333");
        ServicioFalso servicio = servicioConCargaInicial(List.of(ANA));
        servicio.registros.add(() ->
                ResultadoOperacionDTO.fallo("El ID ya existe."));
        VistaFuncionarios vista = cargarVista(servicio);
        esperarConsultaTerminada(vista);

        llenarFormulario(vista, nuevo);
        ejecutarEnJavaFX(() -> {
            boton(vista, "btnGuardar").fire();
            return null;
        });
        esperarMensaje(vista, "El ID ya existe.");

        verificarFormulario(vista, nuevo);
        verificarControlesOperacionRestaurados(vista);
        assertEquals(1, servicio.listarLlamadas.get());
    }

    @Test
    void modificacionExitosaUsaSeleccionYRecargaListado() throws Exception {
        Funcionario modificado = new Funcionario("100", "Ana Pérez", "9999-9999");
        ServicioFalso servicio = servicioConCargaInicial(List.of(ANA));
        servicio.modificaciones.add(ResultadoOperacionDTO::exito);
        servicio.listados.add(() -> List.of(modificado));
        VistaFuncionarios vista = cargarVista(servicio);
        esperarConsultaTerminada(vista);
        seleccionar(vista, ANA);

        ejecutarEnJavaFX(() -> {
            campo(vista, "campoNombre").setText("Ana Pérez");
            campo(vista, "campoTelefono").setText("9999-9999");
            boton(vista, "btnGuardar").fire();
            return null;
        });
        esperarOperacionTerminada(vista, "Funcionario modificado correctamente.");

        assertEquals("100", servicio.modificado.getId());
        assertEquals("Ana Pérez", servicio.modificado.getNombre());
        assertEquals(List.of(modificado), elementos(vista));
    }

    @Test
    void modificacionFallidaConservaCambiosDelFormulario() throws Exception {
        ServicioFalso servicio = servicioConCargaInicial(List.of(ANA));
        servicio.modificaciones.add(() ->
                ResultadoOperacionDTO.fallo("El teléfono no es válido."));
        VistaFuncionarios vista = cargarVista(servicio);
        esperarConsultaTerminada(vista);
        seleccionar(vista, ANA);

        ejecutarEnJavaFX(() -> {
            campo(vista, "campoTelefono").setText("inválido");
            boton(vista, "btnGuardar").fire();
            return null;
        });
        esperarMensaje(vista, "El teléfono no es válido.");

        ejecutarEnJavaFX(() -> {
            assertEquals("inválido", campo(vista, "campoTelefono").getText());
            assertTrue(campo(vista, "campoId").isDisabled());
            assertFalse(boton(vista, "btnGuardar").isDisabled());
            return null;
        });
    }

    @Test
    void bajaExitosaRecargaListado() throws Exception {
        ServicioFalso servicio = servicioConCargaInicial(List.of(ANA));
        servicio.eliminaciones.add(ResultadoOperacionDTO::exito);
        servicio.listados.add(List::of);
        VistaFuncionarios vista = cargarVista(servicio);
        esperarConsultaTerminada(vista);
        seleccionar(vista, ANA);

        ejecutarEnJavaFX(() -> {
            boton(vista, "btnBorrar").fire();
            return null;
        });
        esperarOperacionTerminada(vista, "Funcionario eliminado correctamente.");

        assertEquals("100", servicio.idEliminado);
        assertEquals(List.of(), elementos(vista));
    }

    @Test
    void bajaFallidaConservaSeleccionYFormulario() throws Exception {
        ServicioFalso servicio = servicioConCargaInicial(List.of(ANA));
        servicio.eliminaciones.add(() ->
                ResultadoOperacionDTO.fallo("El funcionario tiene reservas futuras."));
        VistaFuncionarios vista = cargarVista(servicio);
        esperarConsultaTerminada(vista);
        seleccionar(vista, ANA);

        ejecutarEnJavaFX(() -> {
            boton(vista, "btnBorrar").fire();
            return null;
        });
        esperarMensaje(vista, "El funcionario tiene reservas futuras.");

        verificarFormulario(vista, ANA);
        ejecutarEnJavaFX(() -> {
            assertEquals(ANA, tabla(vista).getSelectionModel().getSelectedItem());
            return null;
        });
    }

    @Test
    void respuestaLentaNoBloqueaJavaFXYMantieneEstadoDeConsulta()
            throws Exception {
        RespuestaControlada<List<Funcionario>> respuesta =
                new RespuestaControlada<>(List.of(LUIS));
        ServicioFalso servicio = servicioConCargaInicial(List.of(ANA));
        servicio.busquedas.add(respuesta::obtener);
        VistaFuncionarios vista = cargarVista(servicio);
        esperarConsultaTerminada(vista);

        buscar(vista, "200", "");
        respuesta.esperarInicio();

        CountDownLatch pulsoJavaFX = new CountDownLatch(1);
        Platform.runLater(pulsoJavaFX::countDown);
        assertTrue(pulsoJavaFX.await(1, TimeUnit.SECONDS));
        ejecutarEnJavaFX(() -> {
            assertEquals("Buscando funcionarios...", etiqueta(vista).getText());
            assertTrue(tabla(vista).isDisabled());
            assertTrue(boton(vista, "btnImprimir").isDisabled());
            assertFalse(boton(vista, "btnBuscar").isDisabled());
            return null;
        });

        respuesta.responder();
        esperarTabla(vista, List.of(LUIS));
    }

    @Test
    void dobleGuardadoProduceUnaSolaOperacion() throws Exception {
        Funcionario nuevo = new Funcionario("300", "Eva", "3333-3333");
        RespuestaControlada<ResultadoOperacionDTO> respuesta =
                new RespuestaControlada<>(ResultadoOperacionDTO.exito());
        ServicioFalso servicio = servicioConCargaInicial(List.of());
        servicio.registros.add(respuesta::obtener);
        servicio.listados.add(() -> List.of(nuevo));
        VistaFuncionarios vista = cargarVista(servicio);
        esperarConsultaTerminada(vista);
        llenarFormulario(vista, nuevo);

        ejecutarEnJavaFX(() -> {
            boton(vista, "btnGuardar").fire();
            return null;
        });
        respuesta.esperarInicio();
        ejecutarEnJavaFX(() -> {
            ActionEvent repetido = new ActionEvent(
                    boton(vista, "btnGuardar"),
                    boton(vista, "btnGuardar")
            );
            boton(vista, "btnGuardar").getOnAction().handle(repetido);
            boton(vista, "btnGuardar").getOnAction().handle(repetido);
            return null;
        });

        assertEquals(1, servicio.registrarLlamadas.get());
        respuesta.responder();
        esperarOperacionTerminada(vista, "Funcionario registrado correctamente.");
    }

    @Test
    void respuestaAnteriorNoSobrescribeBusquedaMasReciente() throws Exception {
        RespuestaControlada<List<Funcionario>> anterior =
                new RespuestaControlada<>(List.of(ANA));
        RespuestaControlada<List<Funcionario>> vigente =
                new RespuestaControlada<>(List.of(LUIS));
        ServicioFalso servicio = servicioConCargaInicial(List.of());
        servicio.busquedas.add(anterior::obtener);
        servicio.busquedas.add(vigente::obtener);
        VistaFuncionarios vista = cargarVista(servicio);
        esperarConsultaTerminada(vista);

        buscar(vista, "100", "");
        anterior.esperarInicio();
        buscar(vista, "200", "");
        vigente.esperarInicio();

        vigente.responder();
        esperarTabla(vista, List.of(LUIS));
        anterior.responder();
        anterior.esperarFinalizacion();
        esperarEventosJavaFX();

        assertEquals(List.of(LUIS), elementos(vista));
        assertTrue(vista.controlador().alertas.isEmpty());
    }

    @Test
    void cambioDeFiltroSinBuscarInvalidaRespuestaPendiente() throws Exception {
        RespuestaControlada<List<Funcionario>> respuesta =
                new RespuestaControlada<>(List.of(LUIS));
        ServicioFalso servicio = servicioConCargaInicial(List.of(ANA));
        servicio.busquedas.add(respuesta::obtener);
        VistaFuncionarios vista = cargarVista(servicio);
        esperarConsultaTerminada(vista);

        buscar(vista, "200", "");
        respuesta.esperarInicio();
        ejecutarEnJavaFX(() -> {
            campo(vista, "campoBusquedaId").setText("100");
            return null;
        });
        esperarMensaje(vista, "Los filtros cambiaron. Presione Buscar nuevamente.");

        respuesta.responder();
        respuesta.esperarFinalizacion();
        esperarEventosJavaFX();

        assertEquals(List.of(ANA), elementos(vista));
        verificarControlesConsultaRestaurados(vista);
    }

    @Test
    void errorDeGuardadoRestauraControlesSinBorrarFormulario() throws Exception {
        Funcionario nuevo = new Funcionario("300", "Eva", "3333-3333");
        ServicioFalso servicio = servicioConCargaInicial(List.of());
        servicio.registros.add(() -> {
            throw new IllegalStateException("Conexión perdida");
        });
        VistaFuncionarios vista = cargarVista(servicio);
        esperarConsultaTerminada(vista);
        llenarFormulario(vista, nuevo);

        ejecutarEnJavaFX(() -> {
            boton(vista, "btnGuardar").fire();
            return null;
        });
        esperarAlertas(vista.controlador(), 1);

        verificarFormulario(vista, nuevo);
        verificarControlesOperacionRestaurados(vista);
        assertEquals(
                FuncionarioController.MENSAJE_SIN_CONEXION_GUARDAR,
                vista.controlador().alertas.get(0).mensaje()
        );
    }

    private static ServicioFalso servicioConCargaInicial(
            List<Funcionario> funcionarios
    ) {
        ServicioFalso servicio = new ServicioFalso();
        servicio.listados.add(() -> funcionarios);
        return servicio;
    }

    private static VistaFuncionarios cargarVista(ServicioFalso servicio)
            throws Exception {
        ControladorPrueba controlador = new ControladorPrueba(servicio);
        return ejecutarEnJavaFX(() -> {
            URL recurso = FuncionarioControllerTest.class.getResource(
                    "/Sistema/view/FuncionariosView.fxml"
            );
            FXMLLoader cargador = new FXMLLoader(recurso);
            cargador.setController(controlador);
            Parent raiz = cargador.load();
            return new VistaFuncionarios(cargador, raiz, controlador);
        });
    }

    private static void buscar(VistaFuncionarios vista, String id, String nombre)
            throws Exception {
        ejecutarEnJavaFX(() -> {
            campo(vista, "campoBusquedaId").setText(id);
            campo(vista, "campoBusquedaNombre").setText(nombre);
            boton(vista, "btnBuscar").fire();
            return null;
        });
    }

    private static void llenarFormulario(
            VistaFuncionarios vista,
            Funcionario funcionario
    ) throws Exception {
        ejecutarEnJavaFX(() -> {
            campo(vista, "campoId").setText(funcionario.getId());
            campo(vista, "campoNombre").setText(funcionario.getNombre());
            campo(vista, "campoTelefono").setText(funcionario.getTelefono());
            return null;
        });
    }

    private static void seleccionar(VistaFuncionarios vista, Funcionario funcionario)
            throws Exception {
        ejecutarEnJavaFX(() -> {
            tabla(vista).getSelectionModel().select(funcionario);
            return null;
        });
    }

    private static void verificarFormulario(
            VistaFuncionarios vista,
            Funcionario funcionario
    ) throws Exception {
        ejecutarEnJavaFX(() -> {
            assertEquals(funcionario.getId(), campo(vista, "campoId").getText());
            assertEquals(funcionario.getNombre(), campo(vista, "campoNombre").getText());
            assertEquals(funcionario.getTelefono(), campo(vista, "campoTelefono").getText());
            return null;
        });
    }

    private static void verificarFormularioVacio(VistaFuncionarios vista)
            throws Exception {
        ejecutarEnJavaFX(() -> {
            assertEquals("", campo(vista, "campoId").getText());
            assertEquals("", campo(vista, "campoNombre").getText());
            assertEquals("", campo(vista, "campoTelefono").getText());
            return null;
        });
    }

    private static void verificarControlesConsultaRestaurados(VistaFuncionarios vista)
            throws Exception {
        ejecutarEnJavaFX(() -> {
            assertFalse(tabla(vista).isDisabled());
            assertFalse(boton(vista, "btnBuscar").isDisabled());
            assertFalse(boton(vista, "btnImprimir").isDisabled());
            return null;
        });
    }

    private static void verificarControlesOperacionRestaurados(VistaFuncionarios vista)
            throws Exception {
        ejecutarEnJavaFX(() -> {
            assertFalse(campo(vista, "campoBusquedaId").isDisabled());
            assertFalse(campo(vista, "campoBusquedaNombre").isDisabled());
            assertFalse(campo(vista, "campoNombre").isDisabled());
            assertFalse(campo(vista, "campoTelefono").isDisabled());
            assertFalse(boton(vista, "btnGuardar").isDisabled());
            assertFalse(boton(vista, "btnBorrar").isDisabled());
            assertFalse(boton(vista, "btnLimpiar").isDisabled());
            assertFalse(tabla(vista).isDisabled());
            return null;
        });
    }

    private static void esperarConsultaTerminada(VistaFuncionarios vista)
            throws Exception {
        esperarHasta(() -> ejecutarEnJavaFX(() ->
                !tabla(vista).isDisabled() && etiqueta(vista).getText().isEmpty()
        ));
    }

    private static void esperarOperacionTerminada(
            VistaFuncionarios vista,
            String mensaje
    ) throws Exception {
        esperarHasta(() -> ejecutarEnJavaFX(() ->
                mensaje.equals(etiqueta(vista).getText())
                        && !boton(vista, "btnGuardar").isDisabled()
        ));
    }

    private static void esperarTabla(
            VistaFuncionarios vista,
            List<Funcionario> esperados
    ) throws Exception {
        esperarHasta(() -> ejecutarEnJavaFX(() ->
                !tabla(vista).isDisabled()
                        && List.copyOf(tabla(vista).getItems()).equals(esperados)
        ));
    }

    private static void esperarMensaje(VistaFuncionarios vista, String mensaje)
            throws Exception {
        esperarHasta(() -> ejecutarEnJavaFX(() ->
                mensaje.equals(etiqueta(vista).getText())
        ));
    }

    private static void esperarAlertas(ControladorPrueba controlador, int cantidad)
            throws Exception {
        esperarHasta(() -> controlador.alertas.size() == cantidad);
    }

    private static List<Funcionario> elementos(VistaFuncionarios vista)
            throws Exception {
        return ejecutarEnJavaFX(() -> List.copyOf(tabla(vista).getItems()));
    }

    private static String textoMensaje(VistaFuncionarios vista) throws Exception {
        return ejecutarEnJavaFX(() -> etiqueta(vista).getText());
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

    @SuppressWarnings("unchecked")
    private static TableView<Funcionario> tabla(VistaFuncionarios vista) {
        return (TableView<Funcionario>) assertInstanceOf(
                TableView.class,
                vista.cargador().getNamespace().get("tablaListado")
        );
    }

    private static TextField campo(VistaFuncionarios vista, String id) {
        return assertInstanceOf(
                TextField.class,
                vista.cargador().getNamespace().get(id)
        );
    }

    private static Button boton(VistaFuncionarios vista, String id) {
        return assertInstanceOf(
                Button.class,
                vista.cargador().getNamespace().get(id)
        );
    }

    private static Label etiqueta(VistaFuncionarios vista) {
        return assertInstanceOf(
                Label.class,
                vista.cargador().getNamespace().get("lblMensaje")
        );
    }

    private static final class ControladorPrueba extends FuncionarioController {
        private final List<AlertaCapturada> alertas = new CopyOnWriteArrayList<>();

        private ControladorPrueba(IFuncionarioService servicio) {
            super(servicio);
        }

        @Override
        boolean confirmarBorrado(Funcionario funcionario) {
            return true;
        }

        @Override
        void mostrarAlertaError(String titulo, String mensaje) {
            alertas.add(new AlertaCapturada(titulo, mensaje));
        }
    }

    private static final class ServicioFalso implements IFuncionarioService {
        private final List<Callable<List<Funcionario>>> listados =
                new ArrayList<>();
        private final List<Callable<List<Funcionario>>> busquedas =
                new ArrayList<>();
        private final List<Callable<ResultadoOperacionDTO>> registros =
                new ArrayList<>();
        private final List<Callable<ResultadoOperacionDTO>> modificaciones =
                new ArrayList<>();
        private final List<Callable<ResultadoOperacionDTO>> eliminaciones =
                new ArrayList<>();
        private final AtomicInteger listarLlamadas = new AtomicInteger();
        private final AtomicInteger buscarLlamadas = new AtomicInteger();
        private final AtomicInteger registrarLlamadas = new AtomicInteger();
        private final AtomicInteger modificarLlamadas = new AtomicInteger();
        private final AtomicInteger eliminarLlamadas = new AtomicInteger();
        private volatile Funcionario registrado;
        private volatile Funcionario modificado;
        private volatile String idEliminado;
        private volatile boolean listarEnJavaFX;
        private volatile boolean operacionEnJavaFX;

        @Override
        public List<Funcionario> listarTodos() {
            listarEnJavaFX = Platform.isFxApplicationThread();
            return ejecutar(listados, listarLlamadas.getAndIncrement());
        }

        @Override
        public List<Funcionario> buscar(String idParcial, String nombreParcial) {
            return ejecutar(busquedas, buscarLlamadas.getAndIncrement());
        }

        @Override
        public ResultadoOperacionDTO registrar(Funcionario funcionario) {
            operacionEnJavaFX = Platform.isFxApplicationThread();
            registrado = funcionario;
            return ejecutar(registros, registrarLlamadas.getAndIncrement());
        }

        @Override
        public ResultadoOperacionDTO modificar(Funcionario funcionario) {
            operacionEnJavaFX = Platform.isFxApplicationThread();
            modificado = funcionario;
            return ejecutar(modificaciones, modificarLlamadas.getAndIncrement());
        }

        @Override
        public ResultadoOperacionDTO eliminar(String id) {
            operacionEnJavaFX = Platform.isFxApplicationThread();
            idEliminado = id;
            return ejecutar(eliminaciones, eliminarLlamadas.getAndIncrement());
        }

        private static <T> T ejecutar(List<Callable<T>> respuestas, int indice) {
            try {
                return respuestas.get(indice).call();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private static final class RespuestaControlada<T> {
        private final T resultado;
        private final CountDownLatch iniciada = new CountDownLatch(1);
        private final CountDownLatch permitida = new CountDownLatch(1);
        private final CountDownLatch finalizada = new CountDownLatch(1);

        private RespuestaControlada(T resultado) {
            this.resultado = resultado;
        }

        private T obtener() throws InterruptedException {
            iniciada.countDown();
            try {
                assertTrue(permitida.await(5, TimeUnit.SECONDS));
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

    private record AlertaCapturada(String titulo, String mensaje) {
    }

    private record VistaFuncionarios(
            FXMLLoader cargador,
            Parent raiz,
            ControladorPrueba controlador
    ) {
    }
}
