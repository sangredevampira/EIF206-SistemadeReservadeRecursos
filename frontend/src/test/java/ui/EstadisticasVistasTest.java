package ui;

import Sistema.DTO.EstadisticaCategoriaDTO;
import Sistema.Red.ClienteRed;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.awt.GraphicsEnvironment;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class EstadisticasVistasTest {

    private static final ObjectMapper JSON = new ObjectMapper();

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

    @AfterEach
    void cerrarClienteRed() {
        ClienteRed.getInstancia().cerrar();
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
            LocalDate consultadaDesde = LocalDate.of(2026, 10, 1);
            LocalDate consultadaHasta = LocalDate.of(2026, 10, 31);
            fecha(elementos, "dpDesdeRecursos").setValue(consultadaDesde);
            fecha(elementos, "dpHastaRecursos").setValue(consultadaHasta);

            invocar(
                    controlador,
                    "completarConsulta",
                    new Class<?>[]{
                            long.class,
                            LocalDate.class,
                            LocalDate.class,
                            List.class
                    },
                    0L,
                    consultadaDesde,
                    consultadaHasta,
                    datos
            );

            @SuppressWarnings("unchecked")
            TableView<EstadisticaCategoriaDTO> tabla =
                    (TableView<EstadisticaCategoriaDTO>) elementos.get(
                            "tvEstadisticasRecursos"
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
            LocalDate consultadaDesde = LocalDate.of(2026, 10, 1);
            LocalDate consultadaHasta = LocalDate.of(2026, 10, 31);
            fecha(elementos, "dpDesdeActividades").setValue(consultadaDesde);
            fecha(elementos, "dpHastaActividades").setValue(consultadaHasta);

            invocar(
                    controlador,
                    "completarConsulta",
                    new Class<?>[]{
                            long.class,
                            LocalDate.class,
                            LocalDate.class,
                            Map.class
                    },
                    0L,
                    consultadaDesde,
                    consultadaHasta,
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

    @Test
    void recursosDescartaRespuestaSiCambianFechasYPermiteNuevaConsulta()
            throws Exception {
        ArrayNode respuestaAnterior = JSON.createArrayNode();
        respuestaAnterior.addObject()
                .put("categoria", "Dato anterior")
                .put("cantidad", 99);
        ArrayNode respuestaActual = JSON.createArrayNode();
        respuestaActual.addObject()
                .put("categoria", "Computadoras")
                .put("cantidad", 4);

        probarCambioDeFechasDuranteConsulta(
                "EstadisticasRecursosView.fxml",
                "Recursos",
                "EST_RECURSOS",
                List.of(respuestaAnterior, respuestaActual),
                elementos -> {
                    @SuppressWarnings("unchecked")
                    TableView<EstadisticaCategoriaDTO> tabla =
                            (TableView<EstadisticaCategoriaDTO>) elementos.get(
                                    "tvEstadisticasRecursos"
                            );
                    assertEquals(1, tabla.getItems().size());
                    assertEquals("Computadoras", tabla.getItems().get(0)
                            .getCategoria());
                    assertEquals(4, tabla.getItems().get(0).getCantidad());
                    assertEquals(4, ((Number) grafico(
                            elementos,
                            "barChartRecursos"
                    ).getData().get(0).getData().get(0).getXValue()).intValue());
                }
        );
    }

    @Test
    void actividadesDescartaRespuestaSiCambianFechasYPermiteNuevaConsulta()
            throws Exception {
        ObjectNode respuestaAnterior = JSON.createObjectNode();
        respuestaAnterior.put("2026-10-05", 99);
        ObjectNode respuestaActual = JSON.createObjectNode();
        respuestaActual.put("2026-11-02", 3);

        probarCambioDeFechasDuranteConsulta(
                "EstadisticasActividadesView.fxml",
                "Actividades",
                "EST_ACTIVIDADES",
                List.of(respuestaAnterior, respuestaActual),
                elementos -> {
                    TableView<?> tabla = tabla(
                            elementos,
                            "tvEstadisticasActividades"
                    );
                    assertEquals(1, tabla.getItems().size());
                    assertEquals(3, ((Number) tabla.getColumns().get(1)
                            .getCellData(0)).intValue());
                    assertEquals(3, ((Number) grafico(
                            elementos,
                            "barChartActividades"
                    ).getData().get(0).getData().get(0).getYValue()).intValue());
                }
        );
    }

    private void probarCambioDeFechasDuranteConsulta(
            String archivoVista,
            String sufijo,
            String operacion,
            List<JsonNode> respuestas,
            VerificadorResultados verificador
    ) throws Exception {
        LocalDate desdeAnterior = LocalDate.of(2026, 10, 1);
        LocalDate hastaAnterior = LocalDate.of(2026, 10, 31);
        LocalDate desdeActual = LocalDate.of(2026, 11, 1);
        LocalDate hastaActual = LocalDate.of(2026, 11, 30);

        try (ServidorEstadisticasSimulado servidor =
                     new ServidorEstadisticasSimulado(respuestas)) {
            ClienteRed.getInstancia().conectar(
                    "127.0.0.1",
                    servidor.getPuerto()
            );
            VistaCargada vista = ejecutarEnJavaFX(
                    () -> cargarVista(archivoVista)
            );
            Map<String, Object> elementos = vista.cargador().getNamespace();
            String idBotonCargar = "btnCargar" + sufijo;
            String idBotonPdf = "btnGenerarPdf" + sufijo;
            String idDesde = "dpDesde" + sufijo;
            String idHasta = "dpHasta" + sufijo;
            String idTabla = "tvEstadisticas" + sufijo;
            String idGrafico = "barChart" + sufijo;
            String idEstado = "lblEstado" + sufijo;

            ejecutarEnJavaFX(() -> {
                fecha(elementos, idDesde).setValue(desdeAnterior);
                fecha(elementos, idHasta).setValue(hastaAnterior);
                boton(elementos, idBotonCargar).fire();
                assertTrue(boton(elementos, idBotonCargar).isDisabled());
                return null;
            });

            servidor.esperarSolicitud(0);
            assertPeriodoEnSolicitud(
                    servidor.getSolicitud(0),
                    operacion,
                    desdeAnterior,
                    hastaAnterior
            );

            ejecutarEnJavaFX(() -> {
                fecha(elementos, idDesde).setValue(desdeActual);
                fecha(elementos, idHasta).setValue(hastaActual);
                return null;
            });
            servidor.responder(0);
            esperarHasta(() -> ejecutarEnJavaFX(() ->
                    !boton(elementos, idBotonCargar).isDisabled()
            ));

            ejecutarEnJavaFX(() -> {
                assertTrue(tabla(elementos, idTabla).getItems().isEmpty());
                assertTrue(grafico(elementos, idGrafico).getData().isEmpty());
                assertTrue(etiqueta(elementos, idEstado)
                        .getText().contains("Las fechas cambiaron"));

                boton(elementos, idBotonPdf).fire();
                assertEquals(
                        "Cargue primero las estadísticas antes de generar el PDF.",
                        etiqueta(elementos, idEstado).getText()
                );

                boton(elementos, idBotonCargar).fire();
                assertTrue(boton(elementos, idBotonCargar).isDisabled());
                return null;
            });

            servidor.esperarSolicitud(1);
            assertPeriodoEnSolicitud(
                    servidor.getSolicitud(1),
                    operacion,
                    desdeActual,
                    hastaActual
            );
            servidor.responder(1);
            esperarHasta(() -> ejecutarEnJavaFX(() ->
                    !boton(elementos, idBotonCargar).isDisabled()
            ));

            ejecutarEnJavaFX(() -> {
                verificador.verificar(elementos);
                return null;
            });
            servidor.esperarFinalizacion();
        }
    }

    private static void assertPeriodoEnSolicitud(
            JsonNode solicitud,
            String operacion,
            LocalDate desde,
            LocalDate hasta
    ) {
        assertEquals(operacion, solicitud.path("operacion").asText());
        assertEquals(desde.toString(), solicitud.path("datos")
                .path("desde").asText());
        assertEquals(hasta.toString(), solicitud.path("datos")
                .path("hasta").asText());
    }

    @FunctionalInterface
    private interface VerificadorResultados {
        void verificar(Map<String, Object> elementos) throws Exception;
    }

    @Test
    void evitaSolicitudesDuplicadasMientrasHayUnaConsultaEnCurso()
            throws Exception {
        ejecutarEnJavaFX(() -> {
            VistaCargada recursos = cargarVista(
                    "EstadisticasRecursosView.fxml"
            );
            VistaCargada actividades = cargarVista(
                    "EstadisticasActividadesView.fxml"
            );

            verificarConsultaDuplicadaIgnorada(
                    recursos,
                    "Recursos",
                    "lblEstadoRecursos"
            );
            verificarConsultaDuplicadaIgnorada(
                    actividades,
                    "Actividades",
                    "lblEstadoActividades"
            );
            return null;
        });
    }

    @Test
    void fallosDeComunicacionLimpianResultadosYRestauranBotones()
            throws Exception {
        ejecutarEnJavaFX(() -> {
            VistaCargada recursos = cargarVista(
                    "EstadisticasRecursosView.fxml"
            );
            EstadisticasRecursosController controladorRecursos =
                    (EstadisticasRecursosController) recursos.cargador()
                            .getController();
            Map<String, Object> elementosRecursos = recursos.cargador()
                    .getNamespace();
            LocalDate desde = LocalDate.of(2026, 10, 1);
            LocalDate hasta = LocalDate.of(2026, 10, 31);

            invocar(
                    controladorRecursos,
                    "completarConsulta",
                    new Class<?>[]{
                            long.class,
                            LocalDate.class,
                            LocalDate.class,
                            List.class
                    },
                    0L,
                    desde,
                    hasta,
                    List.of(new EstadisticaCategoriaDTO("Equipo", 2))
            );
            boton(elementosRecursos, "btnCargarRecursos").setDisable(true);
            establecerCampo(controladorRecursos, "consultaEnCurso", true);
            invocar(
                    controladorRecursos,
                    "mostrarErrorConsulta",
                    new Class<?>[]{long.class, Throwable.class},
                    0L,
                    new IOException("Conexión interrumpida")
            );

            assertTrue(tabla(elementosRecursos, "tvEstadisticasRecursos")
                    .getItems().isEmpty());
            assertTrue(grafico(elementosRecursos, "barChartRecursos")
                    .getData().isEmpty());
            assertTrue(!boton(elementosRecursos, "btnCargarRecursos")
                    .isDisabled());
            assertTrue(etiqueta(elementosRecursos, "lblEstadoRecursos")
                    .getText().contains("Verifique la conexión"));

            VistaCargada actividades = cargarVista(
                    "EstadisticasActividadesView.fxml"
            );
            EstadisticasActividadesController controladorActividades =
                    (EstadisticasActividadesController) actividades.cargador()
                            .getController();
            Map<String, Object> elementosActividades = actividades.cargador()
                    .getNamespace();

            invocar(
                    controladorActividades,
                    "completarConsulta",
                    new Class<?>[]{
                            long.class,
                            LocalDate.class,
                            LocalDate.class,
                            Map.class
                    },
                    0L,
                    desde,
                    hasta,
                    Map.of(LocalDate.of(2026, 10, 5), 3)
            );
            boton(elementosActividades, "btnCargarActividades")
                    .setDisable(true);
            establecerCampo(controladorActividades, "consultaEnCurso", true);
            invocar(
                    controladorActividades,
                    "mostrarErrorConsulta",
                    new Class<?>[]{long.class, Throwable.class},
                    0L,
                    new IOException("Conexión interrumpida")
            );

            assertTrue(tabla(elementosActividades, "tvEstadisticasActividades")
                    .getItems().isEmpty());
            assertTrue(grafico(elementosActividades, "barChartActividades")
                    .getData().isEmpty());
            assertTrue(!boton(elementosActividades, "btnCargarActividades")
                    .isDisabled());
            assertTrue(etiqueta(elementosActividades, "lblEstadoActividades")
                    .getText().contains("Verifique la conexión"));
            return null;
        });
    }

    private static void verificarConsultaDuplicadaIgnorada(
            VistaCargada vista,
            String sufijo,
            String idEstado
    ) throws Exception {
        Map<String, Object> elementos = vista.cargador().getNamespace();
        Object controlador = vista.cargador().getController();
        Label estado = etiqueta(elementos, idEstado);
        estado.setText("Consulta vigente");
        fecha(elementos, "dpDesde" + sufijo).setValue(
                LocalDate.of(2026, 10, 1)
        );
        fecha(elementos, "dpHasta" + sufijo).setValue(
                LocalDate.of(2026, 10, 31)
        );
        establecerCampo(controlador, "consultaEnCurso", true);

        invocar(
                controlador,
                "cargarEstadisticas",
                new Class<?>[]{}
        );

        assertEquals("Consulta vigente", estado.getText());
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

    private static void esperarHasta(Callable<Boolean> condicion)
            throws Exception {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);

        while (System.nanoTime() < limite) {
            if (condicion.call()) {
                return;
            }
            Thread.sleep(20);
        }

        fail("La interfaz no alcanzó el estado esperado.");
    }

    private static final class ServidorEstadisticasSimulado
            implements AutoCloseable {

        private final ServerSocket servidor;
        private final ExecutorService executor;
        private final Future<?> tarea;
        private final List<JsonNode> respuestas;
        private final List<JsonNode> solicitudes = new CopyOnWriteArrayList<>();
        private final CountDownLatch[] solicitudesRecibidas;
        private final CountDownLatch[] respuestasPermitidas;

        private ServidorEstadisticasSimulado(List<JsonNode> respuestas)
                throws IOException {
            this.respuestas = List.copyOf(respuestas);
            this.solicitudesRecibidas = crearLatches(respuestas.size());
            this.respuestasPermitidas = crearLatches(respuestas.size());
            this.servidor = new ServerSocket(0);
            this.executor = Executors.newSingleThreadExecutor();
            this.tarea = executor.submit(this::atenderSolicitudes);
        }

        private Void atenderSolicitudes() throws Exception {
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
                for (int indice = 0; indice < respuestas.size(); indice++) {
                    String linea = entrada.readLine();
                    if (linea == null) {
                        throw new IOException(
                                "El cliente cerró la conexión antes de enviar la solicitud."
                        );
                    }

                    JsonNode solicitud = JSON.readTree(linea);
                    solicitudes.add(solicitud);
                    solicitudesRecibidas[indice].countDown();

                    if (!respuestasPermitidas[indice].await(
                            5,
                            TimeUnit.SECONDS
                    )) {
                        throw new IOException(
                                "La prueba no autorizó la respuesta a tiempo."
                        );
                    }

                    ObjectNode respuesta = JSON.createObjectNode();
                    respuesta.put("tipo", "RESPUESTA");
                    respuesta.put(
                            "idSolicitud",
                            solicitud.path("idSolicitud").asText()
                    );
                    respuesta.put("exito", true);
                    respuesta.set("datos", respuestas.get(indice));

                    salida.write(JSON.writeValueAsString(respuesta));
                    salida.newLine();
                    salida.flush();
                }
            }
            return null;
        }

        private static CountDownLatch[] crearLatches(int cantidad) {
            CountDownLatch[] latches = new CountDownLatch[cantidad];
            for (int indice = 0; indice < cantidad; indice++) {
                latches[indice] = new CountDownLatch(1);
            }
            return latches;
        }

        private int getPuerto() {
            return servidor.getLocalPort();
        }

        private void esperarSolicitud(int indice) throws InterruptedException {
            assertTrue(
                    solicitudesRecibidas[indice].await(5, TimeUnit.SECONDS),
                    "No se recibió la solicitud TCP esperada."
            );
        }

        private JsonNode getSolicitud(int indice) {
            return solicitudes.get(indice);
        }

        private void responder(int indice) {
            respuestasPermitidas[indice].countDown();
        }

        private void esperarFinalizacion() throws Exception {
            tarea.get(5, TimeUnit.SECONDS);
        }

        @Override
        public void close() {
            for (CountDownLatch respuestaPermitida : respuestasPermitidas) {
                respuestaPermitida.countDown();
            }
            try {
                servidor.close();
            } catch (IOException ignorada) {
                // El servidor puede haberse cerrado al completar la prueba.
            }
            executor.shutdownNow();
        }
    }

    private record VistaCargada(FXMLLoader cargador, Parent raiz) {
    }
}
