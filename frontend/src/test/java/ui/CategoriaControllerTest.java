package ui;

import Sistema.DTO.CategoriaDTO;
import Sistema.DTO.ResultadoOperacionDTO;
import Sistema.Service.ICategoriaService;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class CategoriaControllerTest {

    private static final CategoriaDTO SALAS =
            new CategoriaDTO("CAT-000001", "Salas");
    private static final CategoriaDTO EQUIPOS =
            new CategoriaDTO("CAT-000002", "Equipos");

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
    void cargaInicialExitosaMuestraCategorias() throws Exception {
        ServicioFalso servicio = servicioConCargaInicial(List.of(SALAS, EQUIPOS));
        VistaCategorias vista = cargarVista(servicio);

        esperarConsultaTerminada(vista);

        assertEquals(List.of(SALAS, EQUIPOS), elementos(vista));
        assertTrue(vista.controlador().alertas.isEmpty());
        assertFalse(servicio.listarEnJavaFX);
    }

    @Test
    void busquedaValidaSinResultadosDejaTablaVaciaSinError() throws Exception {
        ServicioFalso servicio = servicioConCargaInicial(List.of(SALAS));
        servicio.busquedas.add(List::of);
        VistaCategorias vista = cargarVista(servicio);
        esperarConsultaTerminada(vista);

        buscar(vista, "inexistente");
        esperarTabla(vista, List.of());

        assertEquals("", textoMensaje(vista));
        assertTrue(vista.controlador().alertas.isEmpty());
    }

    @Test
    void errorDeComunicacionEnBusquedaConservaTablaYMuestraAlerta()
            throws Exception {
        ServicioFalso servicio = servicioConCargaInicial(List.of(SALAS));
        servicio.busquedas.add(() -> {
            throw new IllegalStateException("Sin conexión");
        });
        VistaCategorias vista = cargarVista(servicio);
        esperarConsultaTerminada(vista);

        buscar(vista, "Salas");
        esperarAlertas(vista.controlador(), 1);

        assertEquals(List.of(SALAS), elementos(vista));
        assertEquals(
                new AlertaCapturada(
                        "Error de conexión",
                        CategoriaController.MENSAJE_SIN_CONEXION_BUSCAR
                ),
                vista.controlador().alertas.get(0)
        );
        verificarControlesConsultaRestaurados(vista);
    }

    @Test
    void errorDelServidorEnBusquedaMuestraMensajeSinVaciarTabla()
            throws Exception {
        ServicioFalso servicio = servicioConCargaInicial(List.of(SALAS));
        servicio.busquedas.add(() -> {
            throw new IllegalArgumentException("No tiene permiso para esta operación.");
        });
        VistaCategorias vista = cargarVista(servicio);
        esperarConsultaTerminada(vista);

        buscar(vista, "Salas");
        esperarMensaje(vista, "No tiene permiso para esta operación.");

        assertEquals(List.of(SALAS), elementos(vista));
        verificarControlesConsultaRestaurados(vista);
    }

    @Test
    void registroExitosoLimpiaFormularioYRecargaListado() throws Exception {
        CategoriaDTO creada = new CategoriaDTO("CAT-000003", "Vehículos");
        ServicioFalso servicio = servicioConCargaInicial(List.of(SALAS));
        servicio.registros.add(ResultadoOperacionDTO::exito);
        servicio.listados.add(() -> List.of(SALAS, creada));
        VistaCategorias vista = cargarVista(servicio);
        esperarConsultaTerminada(vista);

        escribirDescripcion(vista, "Vehículos");
        ejecutarEnJavaFX(() -> {
            boton(vista, "btnGuardar").fire();
            return null;
        });
        esperarOperacionTerminada(vista, "Categoria registrada correctamente.");

        assertNull(servicio.registrada.getId());
        assertEquals("Vehículos", servicio.registrada.getDescripcion());
        assertEquals(List.of(SALAS, creada), elementos(vista));
        assertFalse(servicio.operacionEnJavaFX);
        verificarFormularioVacio(vista);
    }

    @Test
    void registroFallidoConservaFormularioYRestaurarControles() throws Exception {
        ServicioFalso servicio = servicioConCargaInicial(List.of(SALAS));
        servicio.registros.add(() ->
                ResultadoOperacionDTO.fallo("La categoría ya existe."));
        VistaCategorias vista = cargarVista(servicio);
        esperarConsultaTerminada(vista);

        escribirDescripcion(vista, "Salas");
        ejecutarEnJavaFX(() -> {
            boton(vista, "btnGuardar").fire();
            return null;
        });
        esperarMensaje(vista, "La categoría ya existe.");

        assertEquals("Salas", descripcion(vista));
        verificarControlesOperacionRestaurados(vista);
        assertEquals(1, servicio.listarLlamadas.get());
    }

    @Test
    void modificacionExitosaUsaSeleccionYRecargaListado() throws Exception {
        CategoriaDTO modificada = new CategoriaDTO("CAT-000001", "Salas grandes");
        ServicioFalso servicio = servicioConCargaInicial(List.of(SALAS));
        servicio.modificaciones.add(ResultadoOperacionDTO::exito);
        servicio.listados.add(() -> List.of(modificada));
        VistaCategorias vista = cargarVista(servicio);
        esperarConsultaTerminada(vista);
        seleccionar(vista, SALAS);

        escribirDescripcion(vista, "Salas grandes");
        ejecutarEnJavaFX(() -> {
            boton(vista, "btnGuardar").fire();
            return null;
        });
        esperarOperacionTerminada(vista, "Categoria modificada correctamente.");

        assertEquals("CAT-000001", servicio.modificada.getId());
        assertEquals("Salas grandes", servicio.modificada.getDescripcion());
        assertEquals(List.of(modificada), elementos(vista));
    }

    @Test
    void modificacionFallidaConservaCambiosDelFormulario() throws Exception {
        ServicioFalso servicio = servicioConCargaInicial(List.of(SALAS));
        servicio.modificaciones.add(() ->
                ResultadoOperacionDTO.fallo("La descripción no es válida."));
        VistaCategorias vista = cargarVista(servicio);
        esperarConsultaTerminada(vista);
        seleccionar(vista, SALAS);

        escribirDescripcion(vista, "");
        ejecutarEnJavaFX(() -> {
            boton(vista, "btnGuardar").fire();
            return null;
        });
        esperarMensaje(vista, "La descripción no es válida.");

        assertEquals("", descripcion(vista));
        verificarControlesOperacionRestaurados(vista);
    }

    @Test
    void eliminacionExitosaRecargaListado() throws Exception {
        ServicioFalso servicio = servicioConCargaInicial(List.of(SALAS));
        servicio.eliminaciones.add(ResultadoOperacionDTO::exito);
        servicio.listados.add(List::of);
        VistaCategorias vista = cargarVista(servicio);
        esperarConsultaTerminada(vista);
        seleccionar(vista, SALAS);

        ejecutarEnJavaFX(() -> {
            boton(vista, "btnBorrar").fire();
            return null;
        });
        esperarOperacionTerminada(vista, "Categoria eliminada correctamente.");

        assertEquals("CAT-000001", servicio.idEliminado);
        assertEquals(List.of(), elementos(vista));
    }

    @Test
    void categoriaConRecursosNoSeEliminaVisualmenteYMuestraRechazo()
            throws Exception {
        String rechazo = "No se puede eliminar la categoria porque tiene recursos asociados";
        ServicioFalso servicio = servicioConCargaInicial(List.of(SALAS));
        servicio.eliminaciones.add(() -> ResultadoOperacionDTO.fallo(rechazo));
        VistaCategorias vista = cargarVista(servicio);
        esperarConsultaTerminada(vista);
        seleccionar(vista, SALAS);

        ejecutarEnJavaFX(() -> {
            boton(vista, "btnBorrar").fire();
            return null;
        });
        esperarMensaje(vista, rechazo);

        assertEquals(List.of(SALAS), elementos(vista));
        assertEquals(SALAS, seleccionado(vista));
        assertEquals("Salas", descripcion(vista));
        verificarControlesOperacionRestaurados(vista);
    }

    @Test
    void respuestaLentaNoBloqueaJavaFXYMantieneEstadoDeConsulta()
            throws Exception {
        RespuestaControlada<List<CategoriaDTO>> respuesta =
                new RespuestaControlada<>(List.of(EQUIPOS));
        ServicioFalso servicio = servicioConCargaInicial(List.of(SALAS));
        servicio.busquedas.add(respuesta::obtener);
        VistaCategorias vista = cargarVista(servicio);
        esperarConsultaTerminada(vista);

        buscar(vista, "Equipos");
        respuesta.esperarInicio();

        CountDownLatch pulsoJavaFX = new CountDownLatch(1);
        Platform.runLater(pulsoJavaFX::countDown);
        assertTrue(pulsoJavaFX.await(1, TimeUnit.SECONDS));
        ejecutarEnJavaFX(() -> {
            assertEquals("Buscando categorías...", etiqueta(vista).getText());
            assertTrue(tabla(vista).isDisabled());
            assertTrue(boton(vista, "btnImprimir").isDisabled());
            assertFalse(boton(vista, "btnBuscar").isDisabled());
            return null;
        });

        respuesta.responder();
        esperarTabla(vista, List.of(EQUIPOS));
    }

    @Test
    void dobleGuardadoProduceUnaSolaOperacion() throws Exception {
        CategoriaDTO creada = new CategoriaDTO("CAT-000003", "Vehículos");
        RespuestaControlada<ResultadoOperacionDTO> respuesta =
                new RespuestaControlada<>(ResultadoOperacionDTO.exito());
        ServicioFalso servicio = servicioConCargaInicial(List.of());
        servicio.registros.add(respuesta::obtener);
        servicio.listados.add(() -> List.of(creada));
        VistaCategorias vista = cargarVista(servicio);
        esperarConsultaTerminada(vista);
        escribirDescripcion(vista, "Vehículos");

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
        esperarOperacionTerminada(vista, "Categoria registrada correctamente.");
    }

    @Test
    void respuestaAnteriorNoSobrescribeBusquedaMasReciente() throws Exception {
        RespuestaControlada<List<CategoriaDTO>> anterior =
                new RespuestaControlada<>(List.of(SALAS));
        RespuestaControlada<List<CategoriaDTO>> vigente =
                new RespuestaControlada<>(List.of(EQUIPOS));
        ServicioFalso servicio = servicioConCargaInicial(List.of());
        servicio.busquedas.add(anterior::obtener);
        servicio.busquedas.add(vigente::obtener);
        VistaCategorias vista = cargarVista(servicio);
        esperarConsultaTerminada(vista);

        buscar(vista, "Salas");
        anterior.esperarInicio();
        buscar(vista, "Equipos");
        vigente.esperarInicio();

        vigente.responder();
        esperarTabla(vista, List.of(EQUIPOS));
        anterior.responder();
        anterior.esperarFinalizacion();
        esperarEventosJavaFX();

        assertEquals(List.of(EQUIPOS), elementos(vista));
        assertTrue(vista.controlador().alertas.isEmpty());
    }

    @Test
    void cambioDeFiltroSinBuscarInvalidaRespuestaPendiente() throws Exception {
        RespuestaControlada<List<CategoriaDTO>> respuesta =
                new RespuestaControlada<>(List.of(EQUIPOS));
        ServicioFalso servicio = servicioConCargaInicial(List.of(SALAS));
        servicio.busquedas.add(respuesta::obtener);
        VistaCategorias vista = cargarVista(servicio);
        esperarConsultaTerminada(vista);

        buscar(vista, "Equipos");
        respuesta.esperarInicio();
        ejecutarEnJavaFX(() -> {
            campo(vista, "campoBusquedaDescripcion").setText("Salas");
            return null;
        });
        esperarMensaje(vista, "El filtro cambió. Presione Buscar nuevamente.");

        respuesta.responder();
        respuesta.esperarFinalizacion();
        esperarEventosJavaFX();

        assertEquals(List.of(SALAS), elementos(vista));
        verificarControlesConsultaRestaurados(vista);
    }

    @Test
    void errorDeGuardadoRestauraControlesSinBorrarFormulario() throws Exception {
        ServicioFalso servicio = servicioConCargaInicial(List.of());
        servicio.registros.add(() -> {
            throw new IllegalStateException("Conexión perdida");
        });
        VistaCategorias vista = cargarVista(servicio);
        esperarConsultaTerminada(vista);
        escribirDescripcion(vista, "Vehículos");

        ejecutarEnJavaFX(() -> {
            boton(vista, "btnGuardar").fire();
            return null;
        });
        esperarAlertas(vista.controlador(), 1);

        assertEquals("Vehículos", descripcion(vista));
        verificarControlesOperacionRestaurados(vista);
        assertEquals(
                CategoriaController.MENSAJE_SIN_CONEXION_GUARDAR,
                vista.controlador().alertas.get(0).mensaje()
        );
    }

    @Test
    void escrituraExitosaConFalloDeRecargaConservaExitoYRestauraControles()
            throws Exception {
        ServicioFalso servicio = servicioConCargaInicial(List.of(SALAS));
        servicio.registros.add(ResultadoOperacionDTO::exito);
        servicio.listados.add(() -> {
            throw new IllegalStateException("Falló la recarga");
        });
        VistaCategorias vista = cargarVista(servicio);
        esperarConsultaTerminada(vista);
        escribirDescripcion(vista, "Vehículos");

        ejecutarEnJavaFX(() -> {
            boton(vista, "btnGuardar").fire();
            return null;
        });
        esperarAlertas(vista.controlador(), 1);

        assertEquals("Categoria registrada correctamente.", textoMensaje(vista));
        verificarFormularioVacio(vista);
        verificarControlesOperacionRestaurados(vista);
        assertEquals(
                CategoriaController.MENSAJE_SIN_CONEXION_CARGAR,
                vista.controlador().alertas.get(0).mensaje()
        );
    }

    private static ServicioFalso servicioConCargaInicial(
            List<CategoriaDTO> categorias
    ) {
        ServicioFalso servicio = new ServicioFalso();
        servicio.listados.add(() -> categorias);
        return servicio;
    }

    private static VistaCategorias cargarVista(ServicioFalso servicio)
            throws Exception {
        ControladorPrueba controlador = new ControladorPrueba(servicio);
        return ejecutarEnJavaFX(() -> {
            URL recurso = CategoriaControllerTest.class.getResource(
                    "/Sistema/view/CategoriasView.fxml"
            );
            FXMLLoader cargador = new FXMLLoader(recurso);
            cargador.setControllerFactory(tipo -> controlador);
            Parent raiz = cargador.load();
            return new VistaCategorias(cargador, raiz, controlador);
        });
    }

    private static void buscar(VistaCategorias vista, String descripcion)
            throws Exception {
        ejecutarEnJavaFX(() -> {
            campo(vista, "campoBusquedaDescripcion").setText(descripcion);
            boton(vista, "btnBuscar").fire();
            return null;
        });
    }

    private static void escribirDescripcion(VistaCategorias vista, String descripcion)
            throws Exception {
        ejecutarEnJavaFX(() -> {
            campo(vista, "campoDescripcion").setText(descripcion);
            return null;
        });
    }

    private static void seleccionar(VistaCategorias vista, CategoriaDTO categoria)
            throws Exception {
        ejecutarEnJavaFX(() -> {
            tabla(vista).getSelectionModel().select(categoria);
            return null;
        });
    }

    private static CategoriaDTO seleccionado(VistaCategorias vista) throws Exception {
        return ejecutarEnJavaFX(() ->
                tabla(vista).getSelectionModel().getSelectedItem());
    }

    private static String descripcion(VistaCategorias vista) throws Exception {
        return ejecutarEnJavaFX(() -> campo(vista, "campoDescripcion").getText());
    }

    private static void verificarFormularioVacio(VistaCategorias vista)
            throws Exception {
        ejecutarEnJavaFX(() -> {
            assertEquals("", campo(vista, "campoId").getText());
            assertEquals("", campo(vista, "campoDescripcion").getText());
            return null;
        });
    }

    private static void verificarControlesConsultaRestaurados(VistaCategorias vista)
            throws Exception {
        ejecutarEnJavaFX(() -> {
            assertFalse(tabla(vista).isDisabled());
            assertFalse(boton(vista, "btnBuscar").isDisabled());
            assertFalse(boton(vista, "btnImprimir").isDisabled());
            return null;
        });
    }

    private static void verificarControlesOperacionRestaurados(VistaCategorias vista)
            throws Exception {
        ejecutarEnJavaFX(() -> {
            assertFalse(campo(vista, "campoBusquedaDescripcion").isDisabled());
            assertTrue(campo(vista, "campoId").isDisabled());
            assertFalse(campo(vista, "campoDescripcion").isDisabled());
            assertFalse(boton(vista, "btnGuardar").isDisabled());
            assertFalse(boton(vista, "btnBorrar").isDisabled());
            assertFalse(boton(vista, "btnLimpiar").isDisabled());
            assertFalse(tabla(vista).isDisabled());
            return null;
        });
    }

    private static void esperarConsultaTerminada(VistaCategorias vista)
            throws Exception {
        esperarHasta(() -> ejecutarEnJavaFX(() ->
                !tabla(vista).isDisabled() && etiqueta(vista).getText().isEmpty()
        ));
    }

    private static void esperarOperacionTerminada(
            VistaCategorias vista,
            String mensaje
    ) throws Exception {
        esperarHasta(() -> ejecutarEnJavaFX(() ->
                mensaje.equals(etiqueta(vista).getText())
                        && !boton(vista, "btnGuardar").isDisabled()
        ));
    }

    private static void esperarTabla(
            VistaCategorias vista,
            List<CategoriaDTO> esperadas
    ) throws Exception {
        esperarHasta(() -> ejecutarEnJavaFX(() ->
                !tabla(vista).isDisabled()
                        && List.copyOf(tabla(vista).getItems()).equals(esperadas)
        ));
    }

    private static void esperarMensaje(VistaCategorias vista, String mensaje)
            throws Exception {
        esperarHasta(() -> ejecutarEnJavaFX(() ->
                mensaje.equals(etiqueta(vista).getText())
        ));
    }

    private static void esperarAlertas(ControladorPrueba controlador, int cantidad)
            throws Exception {
        esperarHasta(() -> controlador.alertas.size() == cantidad);
    }

    private static List<CategoriaDTO> elementos(VistaCategorias vista)
            throws Exception {
        return ejecutarEnJavaFX(() -> List.copyOf(tabla(vista).getItems()));
    }

    private static String textoMensaje(VistaCategorias vista) throws Exception {
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
    private static TableView<CategoriaDTO> tabla(VistaCategorias vista) {
        return (TableView<CategoriaDTO>) assertInstanceOf(
                TableView.class,
                vista.cargador().getNamespace().get("tablaListado")
        );
    }

    private static TextField campo(VistaCategorias vista, String id) {
        return assertInstanceOf(
                TextField.class,
                vista.cargador().getNamespace().get(id)
        );
    }

    private static Button boton(VistaCategorias vista, String id) {
        return assertInstanceOf(
                Button.class,
                vista.cargador().getNamespace().get(id)
        );
    }

    private static Label etiqueta(VistaCategorias vista) {
        return assertInstanceOf(
                Label.class,
                vista.cargador().getNamespace().get("lblMensaje")
        );
    }

    private static final class ControladorPrueba extends CategoriaController {
        private final List<AlertaCapturada> alertas = new CopyOnWriteArrayList<>();

        private ControladorPrueba(ICategoriaService servicio) {
            super(servicio);
        }

        @Override
        boolean confirmarBorrado(CategoriaDTO categoria) {
            return true;
        }

        @Override
        void mostrarAlertaError(String titulo, String mensaje) {
            alertas.add(new AlertaCapturada(titulo, mensaje));
        }
    }

    private static final class ServicioFalso implements ICategoriaService {
        private final List<Callable<List<CategoriaDTO>>> listados =
                new ArrayList<>();
        private final List<Callable<List<CategoriaDTO>>> busquedas =
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
        private volatile CategoriaDTO registrada;
        private volatile CategoriaDTO modificada;
        private volatile String idEliminado;
        private volatile boolean listarEnJavaFX;
        private volatile boolean operacionEnJavaFX;

        @Override
        public List<CategoriaDTO> listarTodos() {
            listarEnJavaFX = Platform.isFxApplicationThread();
            return ejecutar(listados, listarLlamadas.getAndIncrement());
        }

        @Override
        public List<CategoriaDTO> buscar(String descripcionParcial) {
            return ejecutar(busquedas, buscarLlamadas.getAndIncrement());
        }

        @Override
        public ResultadoOperacionDTO registrar(CategoriaDTO categoria) {
            operacionEnJavaFX = Platform.isFxApplicationThread();
            registrada = categoria;
            return ejecutar(registros, registrarLlamadas.getAndIncrement());
        }

        @Override
        public ResultadoOperacionDTO modificar(CategoriaDTO categoria) {
            operacionEnJavaFX = Platform.isFxApplicationThread();
            modificada = categoria;
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

    private record VistaCategorias(
            FXMLLoader cargador,
            Parent raiz,
            ControladorPrueba controlador
    ) {
    }
}
