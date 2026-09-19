package ui;

import Sistema.DTO.EstadisticaCategoriaDTO;
import Sistema.DTO.ReportConfigDTO;
import Sistema.Logica.Exception.ReportExceptionLogica;
import Sistema.Logica.EstadisticasRecursosLogica.ResultadoEstadisticas;
import Sistema.Service.EstadisticasRecursosService;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.paint.Color;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class EstadisticasRecursosController {

    @FXML private DatePicker dpDesdeRecursos;
    @FXML private DatePicker dpHastaRecursos;
    @FXML private Button btnCargarRecursos;
    @FXML private Button btnGenerarPdfRecursos;

    @FXML private TableView<EstadisticaCategoriaDTO> tvEstadisticasRecursos;
    @FXML private TableColumn<EstadisticaCategoriaDTO, String> colCategoria;
    @FXML private TableColumn<EstadisticaCategoriaDTO, Integer> colCantidadRecursos;

    @FXML private BarChart<Number, String> barChartRecursos;
    @FXML private CategoryAxis ejeRecurso;
    @FXML private NumberAxis ejeCantidadRecursos;

    @FXML private Label lblEstadoRecursos;

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final EstadisticasRecursosService estadisticasRecursosService = new EstadisticasRecursosService();
    private final PdfController pdfController = new PdfController();

    // Distingue "todavia no se cargo nada" de "se cargo y no habia datos" --
    // ambos casos dejan la tabla vacia, pero solo el primero debe bloquear
    // el boton de generar PDF.
    private boolean datosCalculados = false;

    // Fechas con las que se cargaron los datos actualmente mostrados en la
    // tabla/grafico. Si el usuario cambia las fechas en los DatePicker sin
    // volver a presionar "Cargar", estas quedan desactualizadas respecto a
    // lo que hay seleccionado -- eso es lo que generarPdf() valida antes de
    // usar el grafico/tabla actuales (que de lo contrario corresponderian
    // al periodo anterior, no al que se ve seleccionado en pantalla).
    private LocalDate desdeCargado;
    private LocalDate hastaCargado;

    @FXML
    private void initialize() {
        colCategoria.setCellValueFactory(new PropertyValueFactory<>("categoria"));
        colCantidadRecursos.setCellValueFactory(new PropertyValueFactory<>("cantidad"));

        ejeCantidadRecursos.setForceZeroInRange(true);
        ejeCantidadRecursos.setMinorTickVisible(false);

        barChartRecursos.setBarGap(3);
        barChartRecursos.setCategoryGap(8);

        btnCargarRecursos.setOnAction(evento -> cargarEstadisticas());
        btnGenerarPdfRecursos.setOnAction(evento -> generarPdf());
    }

    private void cargarEstadisticas() {
        LocalDate desde = dpDesdeRecursos.getValue();
        LocalDate hasta = dpHastaRecursos.getValue();

        ResultadoEstadisticas resultado = estadisticasRecursosService.calcular(desde, hasta);

        if (!resultado.isExitoso()) {
            datosCalculados = false;
            desdeCargado = null;
            hastaCargado = null;
            mostrarEstado(resultado.getMensajeError(), true);
            limpiarTablaYGrafico();
            return;
        }

        List<EstadisticaCategoriaDTO> datos = resultado.getDatos();
        datosCalculados = true;
        desdeCargado = desde;
        hastaCargado = hasta;
        tvEstadisticasRecursos.setItems(FXCollections.observableArrayList(datos));
        actualizarGrafico(datos);

        if (datos.isEmpty()) {
            mostrarEstado("No hay recursos reservados en el período seleccionado.", false);
        } else {
            mostrarEstado("Estadísticas cargadas correctamente.", false);
        }
    }

    private void actualizarGrafico(List<EstadisticaCategoriaDTO> datos) {
        int cantidadMaxima = datos.stream()
                .mapToInt(EstadisticaCategoriaDTO::getCantidad)
                .max()
                .orElse(0);

        ejeCantidadRecursos.setAutoRanging(false);
        ejeCantidadRecursos.setLowerBound(0);
        ejeCantidadRecursos.setUpperBound(Math.max(1, cantidadMaxima + 1));
        ejeCantidadRecursos.setTickUnit(1);
        ejeCantidadRecursos.setMinorTickVisible(false);

        XYChart.Series<Number, String> serie = new XYChart.Series<>();

        serie.setName("Recursos reservados");

        for (EstadisticaCategoriaDTO dato : datos) {
            serie.getData().add(new XYChart.Data<>(dato.getCantidad(), dato.getCategoria()));
        }

        barChartRecursos.getData().setAll(serie);
    }

    private void limpiarTablaYGrafico() {
        tvEstadisticasRecursos.setItems(FXCollections.observableArrayList());
        barChartRecursos.getData().clear();
    }

    private void generarPdf() {
        if (!datosCalculados) {
            mostrarEstado("Cargue primero las estadísticas antes de generar el PDF.", true);
            return;
        }

        LocalDate desde = dpDesdeRecursos.getValue();
        LocalDate hasta = dpHastaRecursos.getValue();

        if (!java.util.Objects.equals(desde, desdeCargado) || !java.util.Objects.equals(hasta, hastaCargado)) {
            mostrarEstado("Las fechas seleccionadas cambiaron. Presione \"Cargar\" antes de generar el PDF.", true);
            return;
        }

        ReportConfigDTO configuracion = new ReportConfigDTO(
                "Estadísticas de recursos", "Recursos reservados por categoría", "estadisticas_recursos");
        configuracion.setTituloGrafico("Recursos reservados por categoría");
        if (desde != null && hasta != null) {
            configuracion.agregarFiltro("Desde", desde.format(FORMATO_FECHA));
            configuracion.agregarFiltro("Hasta", hasta.format(FORMATO_FECHA));
        }

        try {
            boolean generado = pdfController.generarConGrafico(
                    configuracion,
                    tvEstadisticasRecursos.getScene().getWindow(),
                    tvEstadisticasRecursos,
                    barChartRecursos);
            if (generado) {
                mostrarEstado("Reporte PDF generado correctamente.", false);
            }
        } catch (ReportExceptionLogica e) {
            mostrarErrorSistema("No se pudo generar el reporte PDF.\n" + e.getMessage());
        }
    }

    private void mostrarEstado(String mensaje, boolean esError) {
        lblEstadoRecursos.setTextFill(esError ? Color.web("#C0392B") : Color.web("#555555"));
        lblEstadoRecursos.setText(mensaje);
    }

    private void mostrarErrorSistema(String mensaje) {
        Alert alerta = new Alert(Alert.AlertType.ERROR, mensaje);
        alerta.setTitle("Error inesperado");
        alerta.setHeaderText(null);
        alerta.showAndWait();
    }
}