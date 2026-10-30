package ui;

import Sistema.DTO.EstadisticaCategoriaDTO;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Side;
import javafx.scene.Parent;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.awt.GraphicsEnvironment;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class EstadisticasVistasTest {

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

        assertTrue(
                iniciado.await(10, TimeUnit.SECONDS),
                "No se pudo iniciar JavaFX."
        );
    }

    @Test
    void cargaVistaGeneralConAmbasEstadisticas() throws Exception {
        ejecutarEnJavaFX(() -> {
            VistaCargada vista = cargarVista("EstadisticasView.fxml");

            assertNotNull(vista.raiz());
            SplitPane panel = assertInstanceOf(
                    SplitPane.class,
                    vista.raiz().lookup(".split-pane")
            );
            assertEquals(2, panel.getItems().size());
            return null;
        });
    }

    @Test
    void vistaRecursosContieneControlesYOrientacionEsperados()
            throws Exception {
        ejecutarEnJavaFX(() -> {
            VistaCargada vista = cargarVista(
                    "EstadisticasRecursosView.fxml"
            );
            Map<String, Object> elementos = vista.cargador().getNamespace();

            assertNotNull(elementos.get("dpDesdeRecursos"));
            assertNotNull(elementos.get("dpHastaRecursos"));
            assertEquals(
                    "Cargar",
                    boton(elementos, "btnCargarRecursos").getText()
            );
            assertEquals(
                    "Generar PDF",
                    boton(elementos, "btnGenerarPdfRecursos").getText()
            );

            TableView<?> tabla = tabla(elementos, "tvEstadisticasRecursos");
            assertEquals(2, tabla.getColumns().size());
            assertEquals("Categoría", tabla.getColumns().get(0).getText());
            assertEquals("Cantidad", tabla.getColumns().get(1).getText());

            BarChart<?, ?> grafico = grafico(elementos, "barChartRecursos");
            assertInstanceOf(NumberAxis.class, grafico.getXAxis());
            assertInstanceOf(CategoryAxis.class, grafico.getYAxis());
            assertEquals(Side.BOTTOM, grafico.getXAxis().getSide());
            assertEquals(Side.LEFT, grafico.getYAxis().getSide());
            assertSame(elementos.get("ejeCantidadRecursos"), grafico.getXAxis());
            assertNotNull(elementos.get("lblEstadoRecursos"));
            return null;
        });
    }

    @Test
    void vistaActividadesContieneControlesYOrientacionEsperados()
            throws Exception {
        ejecutarEnJavaFX(() -> {
            VistaCargada vista = cargarVista(
                    "EstadisticasActividadesView.fxml"
            );
            Map<String, Object> elementos = vista.cargador().getNamespace();

            assertNotNull(elementos.get("dpDesdeActividades"));
            assertNotNull(elementos.get("dpHastaActividades"));
            assertEquals(
                    "Cargar",
                    boton(elementos, "btnCargarActividades").getText()
            );
            assertEquals(
                    "Generar PDF",
                    boton(elementos, "btnGenerarPdfActividades").getText()
            );

            TableView<?> tabla = tabla(
                    elementos,
                    "tvEstadisticasActividades"
            );
            assertEquals(2, tabla.getColumns().size());
            assertEquals("Semana", tabla.getColumns().get(0).getText());
            assertEquals("Cantidad", tabla.getColumns().get(1).getText());

            BarChart<?, ?> grafico = grafico(
                    elementos,
                    "barChartActividades"
            );
            assertInstanceOf(CategoryAxis.class, grafico.getXAxis());
            assertInstanceOf(NumberAxis.class, grafico.getYAxis());
            assertEquals(Side.BOTTOM, grafico.getXAxis().getSide());
            assertEquals(Side.LEFT, grafico.getYAxis().getSide());
            assertSame(elementos.get("ejeCantidad"), grafico.getYAxis());
            assertNotNull(elementos.get("lblEstadoActividades"));
            return null;
        });
    }

    @Test
    void recursosValidaPeriodoYPdfSinAbrirDialogos() throws Exception {
        ejecutarEnJavaFX(() -> {
            VistaCargada vista = cargarVista(
                    "EstadisticasRecursosView.fxml"
            );
            Map<String, Object> elementos = vista.cargador().getNamespace();
            Label estado = etiqueta(elementos, "lblEstadoRecursos");
            DatePicker desde = fecha(elementos, "dpDesdeRecursos");
            DatePicker hasta = fecha(elementos, "dpHastaRecursos");

            boton(elementos, "btnCargarRecursos").fire();
            assertEquals("Debe indicar ambas fechas del período.", estado.getText());

            desde.setValue(LocalDate.of(2026, 10, 10));
            hasta.setValue(LocalDate.of(2026, 10, 1));
            boton(elementos, "btnCargarRecursos").fire();
            assertEquals(
                    "La fecha 'desde' no puede ser posterior a la fecha 'hasta'.",
                    estado.getText()
            );

            boton(elementos, "btnGenerarPdfRecursos").fire();
            assertEquals(
                    "Cargue primero las estadísticas antes de generar el PDF.",
                    estado.getText()
            );
            return null;
        });
    }

    @Test
    void actividadesValidaPeriodoYPdfSinAbrirDialogos() throws Exception {
        ejecutarEnJavaFX(() -> {
            VistaCargada vista = cargarVista(
                    "EstadisticasActividadesView.fxml"
            );
            Map<String, Object> elementos = vista.cargador().getNamespace();
            Label estado = etiqueta(elementos, "lblEstadoActividades");
            DatePicker desde = fecha(elementos, "dpDesdeActividades");
            DatePicker hasta = fecha(elementos, "dpHastaActividades");

            boton(elementos, "btnCargarActividades").fire();
            assertEquals("Debe indicar ambas fechas del período.", estado.getText());

            desde.setValue(LocalDate.of(2026, 10, 10));
            hasta.setValue(LocalDate.of(2026, 10, 1));
            boton(elementos, "btnCargarActividades").fire();
            assertEquals(
                    "La fecha 'desde' no puede ser posterior a la fecha 'hasta'.",
                    estado.getText()
            );

            boton(elementos, "btnGenerarPdfActividades").fire();
            assertEquals(
                    "Cargue primero las estadísticas antes de generar el PDF.",
                    estado.getText()
            );
            return null;
        });
    }

    @Test
    void recursosRepresentaEnterosYRechazaFechasDesactualizadas()
            throws Exception {
        ejecutarEnJavaFX(() -> {
            VistaCargada vista = cargarVista(
                    "EstadisticasRecursosView.fxml"
            );
            Map<String, Object> elementos = vista.cargador().getNamespace();
            EstadisticasRecursosController controlador =
                    (EstadisticasRecursosController) vista.cargador().getController();
            List<EstadisticaCategoriaDTO> datos = List.of(
                    new EstadisticaCategoriaDTO("Computadoras", 8),
                    new EstadisticaCategoriaDTO("Proyectores", 5)
            );

            @SuppressWarnings("unchecked")
            TableView<EstadisticaCategoriaDTO> tabla =
                    (TableView<EstadisticaCategoriaDTO>) elementos.get(
                            "tvEstadisticasRecursos"
                    );
            tabla.getItems().setAll(datos);
            invocar(
                    controlador,
                    "actualizarGrafico",
                    new Class<?>[]{List.class},
                    datos
            );

            @SuppressWarnings("unchecked")
            BarChart<Number, String> grafico =
                    (BarChart<Number, String>) elementos.get(
                            "barChartRecursos"
                    );
            assertEquals(2, tabla.getItems().size());
            assertEquals(8, tabla.getItems().get(0).getCantidad());
            assertEquals(8, grafico.getData().get(0).getData().get(0)
                    .getXValue().intValue());
            assertEquals(1, ((NumberAxis) grafico.getXAxis()).getTickUnit());

            LocalDate consultadaDesde = LocalDate.of(2026, 10, 1);
            LocalDate consultadaHasta = LocalDate.of(2026, 10, 31);
            establecerCampo(controlador, "datosCalculados", true);
            establecerCampo(controlador, "desdeCargado", consultadaDesde);
            establecerCampo(controlador, "hastaCargado", consultadaHasta);
            fecha(elementos, "dpDesdeRecursos").setValue(
                    consultadaDesde.plusDays(1)
            );
            fecha(elementos, "dpHastaRecursos").setValue(consultadaHasta);

            boton(elementos, "btnGenerarPdfRecursos").fire();
            assertTrue(etiqueta(elementos, "lblEstadoRecursos").getText()
                    .contains("fechas seleccionadas cambiaron"));
            return null;
        });
    }

    @Test
    void actividadesActualizaPresentacionYRechazaFechasDesactualizadas()
            throws Exception {
        ejecutarEnJavaFX(() -> {
            VistaCargada vista = cargarVista(
                    "EstadisticasActividadesView.fxml"
            );
            Map<String, Object> elementos = vista.cargador().getNamespace();
            EstadisticasActividadesController controlador =
                    (EstadisticasActividadesController) vista.cargador()
                            .getController();
            LocalDate primeraSemana = LocalDate.of(2026, 10, 5);
            Map<LocalDate, Integer> conteo = new TreeMap<>();
            conteo.put(primeraSemana, 4);
            conteo.put(primeraSemana.plusWeeks(1), 7);

            invocar(
                    controlador,
                    "mostrarEstadisticas",
                    new Class<?>[]{Map.class},
                    conteo
            );

            TableView<?> tabla = tabla(
                    elementos,
                    "tvEstadisticasActividades"
            );
            @SuppressWarnings("unchecked")
            BarChart<String, Number> grafico =
                    (BarChart<String, Number>) elementos.get(
                            "barChartActividades"
                    );
            TableColumn<?, ?> columnaCantidad = tabla.getColumns().get(1);

            assertEquals(2, tabla.getItems().size());
            assertEquals(4, ((Number) columnaCantidad.getCellData(0)).intValue());
            assertEquals(2, grafico.getData().get(0).getData().size());
            assertEquals(4, grafico.getData().get(0).getData().get(0)
                    .getYValue().intValue());
            assertEquals(1, ((NumberAxis) grafico.getYAxis()).getTickUnit());
            assertEquals(8, ((NumberAxis) grafico.getYAxis()).getUpperBound());

            LocalDate consultadaDesde = LocalDate.of(2026, 10, 1);
            LocalDate consultadaHasta = LocalDate.of(2026, 10, 31);
            establecerCampo(
                    controlador,
                    "fechaDesdeCargada",
                    consultadaDesde
            );
            establecerCampo(
                    controlador,
                    "fechaHastaCargada",
                    consultadaHasta
            );
            fecha(elementos, "dpDesdeActividades").setValue(
                    consultadaDesde.plusDays(1)
            );
            fecha(elementos, "dpHastaActividades").setValue(consultadaHasta);

            boton(elementos, "btnGenerarPdfActividades").fire();
            assertTrue(etiqueta(elementos, "lblEstadoActividades").getText()
                    .contains("Las fechas cambiaron"));
            return null;
        });
    }

    @Test
    void actividadesInformaCuandoElPeriodoNoTieneDatos() throws Exception {
        ejecutarEnJavaFX(() -> {
            VistaCargada vista = cargarVista(
                    "EstadisticasActividadesView.fxml"
            );
            Map<String, Object> elementos = vista.cargador().getNamespace();
            EstadisticasActividadesController controlador =
                    (EstadisticasActividadesController) vista.cargador()
                            .getController();

            invocar(
                    controlador,
                    "mostrarEstadisticas",
                    new Class<?>[]{Map.class},
                    Map.of(LocalDate.of(2026, 10, 5), 0)
            );

            assertEquals(
                    "No hay actividades programadas en el período seleccionado.",
                    etiqueta(elementos, "lblEstadoActividades").getText()
            );
            return null;
        });
    }

    private static VistaCargada cargarVista(String archivo) throws Exception {
        URL recurso = EstadisticasVistasTest.class.getResource(
                "/Sistema/view/" + archivo
        );
        assertNotNull(recurso, "No se encontró la vista " + archivo);

        FXMLLoader cargador = new FXMLLoader(recurso);
        Parent raiz = cargador.load();
        return new VistaCargada(cargador, raiz);
    }

    private static Button boton(Map<String, Object> elementos, String id) {
        return assertInstanceOf(Button.class, elementos.get(id));
    }

    private static DatePicker fecha(Map<String, Object> elementos, String id) {
        return assertInstanceOf(DatePicker.class, elementos.get(id));
    }

    private static Label etiqueta(Map<String, Object> elementos, String id) {
        return assertInstanceOf(Label.class, elementos.get(id));
    }

    private static TableView<?> tabla(
            Map<String, Object> elementos,
            String id
    ) {
        return assertInstanceOf(TableView.class, elementos.get(id));
    }

    private static BarChart<?, ?> grafico(
            Map<String, Object> elementos,
            String id
    ) {
        return assertInstanceOf(BarChart.class, elementos.get(id));
    }

    private static void invocar(
            Object objetivo,
            String nombre,
            Class<?>[] tipos,
            Object... argumentos
    ) throws Exception {
        Method metodo = objetivo.getClass().getDeclaredMethod(nombre, tipos);
        metodo.setAccessible(true);
        metodo.invoke(objetivo, argumentos);
    }

    private static void establecerCampo(
            Object objetivo,
            String nombre,
            Object valor
    ) throws Exception {
        Field campo = objetivo.getClass().getDeclaredField(nombre);
        campo.setAccessible(true);
        campo.set(objetivo, valor);
    }

    private static <T> T ejecutarEnJavaFX(Callable<T> accion)
            throws Exception {
        if (Platform.isFxApplicationThread()) {
            return accion.call();
        }

        FutureTask<T> tarea = new FutureTask<>(accion);
        Platform.runLater(tarea);
        return tarea.get(10, TimeUnit.SECONDS);
    }

    private record VistaCargada(FXMLLoader cargador, Parent raiz) {
    }
}
