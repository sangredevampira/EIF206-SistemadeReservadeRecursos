package ui;

import Sistema.DTO.EstadoReserva;
import Sistema.DTO.ReportConfigDTO;
import Sistema.DTO.ReservaDTO;
import Sistema.Exception.ReportExceptionLogica;
import Sistema.Service.ReservaService;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public class EstadisticasActividadesController {
    @FXML private DatePicker dpDesdeActividades;
    @FXML private DatePicker dpHastaActividades;
    @FXML private Button btnCargarActividades;
    @FXML private Button btnGenerarPdfActividades;
    @FXML private TableView<EstadisticaSemana> tvEstadisticasActividades;
    @FXML private TableColumn<EstadisticaSemana, String> colSemana;
    @FXML private TableColumn<EstadisticaSemana, Number> colCantidad;
    @FXML private BarChart<String, Number> barChartActividades;
    @FXML private CategoryAxis ejeSemanas;
    @FXML private NumberAxis ejeCantidad;
    @FXML private Label lblEstadoActividades;
    private LocalDate fechaDesdeCargada;
    private LocalDate fechaHastaCargada;
    private final ReservaService reservaService = new ReservaService();
    private final PdfController pdfController = new PdfController();

    @FXML
    private void initialize() {
        colSemana.setCellValueFactory(fila -> new ReadOnlyStringWrapper(fila.getValue().getSemana()));
        colCantidad.setCellValueFactory(fila -> new ReadOnlyIntegerWrapper(fila.getValue().getCantidad()));
        ejeCantidad.setForceZeroInRange(true);
        btnCargarActividades.setOnAction(evento -> cargarEstadisticas());
        btnGenerarPdfActividades.setOnAction(evento -> generarPdf());
        lblEstadoActividades.setText("Seleccione las fechas para consultar las actividades programadas.");
    }
    private Map<LocalDate, Integer> contarActividadesPorSemana(LocalDate fechaDesde, LocalDate fechaHasta, List<LocalDate> fechasActividades) {
        Map<LocalDate, Integer> conteoSemanal = new TreeMap<>();
        LocalDate primeraSemana = fechaDesde.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate ultimaSemana = fechaHasta.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate semanaActual = primeraSemana;
        while (!semanaActual.isAfter(ultimaSemana)) {
            conteoSemanal.put(semanaActual, 0);
            semanaActual = semanaActual.plusWeeks(1);
        }
        for (LocalDate fechaActividad : fechasActividades) {
            if (fechaActividad.isBefore(fechaDesde) || fechaActividad.isAfter(fechaHasta)) continue;
            LocalDate lunesDeLaSemana = fechaActividad.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            conteoSemanal.computeIfPresent(lunesDeLaSemana, (semana, cantidad) -> cantidad + 1);
        }
        return conteoSemanal;
    }
    private void mostrarEstadisticas(Map<LocalDate, Integer> conteoSemanal) {
        tvEstadisticasActividades.getItems().clear();
        barChartActividades.getData().clear();
        DateTimeFormatter formatoFecha = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        XYChart.Series<String, Number> serieActividades = new XYChart.Series<>();
        serieActividades.setName("Actividades");
        for (Map.Entry<LocalDate, Integer> resultado : conteoSemanal.entrySet()) {
            LocalDate inicioSemana = resultado.getKey();
            LocalDate finSemana = inicioSemana.plusDays(6);
            int cantidad = resultado.getValue();
            String textoSemana = inicioSemana.format(formatoFecha) + " al " + finSemana.format(formatoFecha);
            tvEstadisticasActividades.getItems().add(new EstadisticaSemana(textoSemana, cantidad));
            serieActividades.getData().add(new XYChart.Data<>(textoSemana, cantidad));
        }
        barChartActividades.getData().add(serieActividades);
    }
    private void cargarEstadisticasDesdeJson() {
        List<LocalDate> fechasActividades = reservaService.buscarPorPeriodo(fechaDesdeCargada, fechaHastaCargada).stream()
                .filter(reserva -> reserva.getEstado() == EstadoReserva.ACTIVA)
                .map(ReservaDTO::getFecha)
                .toList();

        Map<LocalDate, Integer> conteoSemanal = contarActividadesPorSemana(
                fechaDesdeCargada, fechaHastaCargada, fechasActividades
        );
        mostrarEstadisticas(conteoSemanal);
    }
    private void cargarEstadisticas() {
        if (dpDesdeActividades.getValue() == null || dpHastaActividades.getValue() == null) {
            lblEstadoActividades.setText("Debe seleccionar la fecha inicial y la fecha final.");
            return;
        }

        if (dpDesdeActividades.getValue().isAfter(dpHastaActividades.getValue())) {
            lblEstadoActividades.setText("La fecha inicial no puede ser posterior a la fecha final.");
            return;
        }

        fechaDesdeCargada = dpDesdeActividades.getValue();
        fechaHastaCargada = dpHastaActividades.getValue();
        try {
            cargarEstadisticasDesdeJson();
            lblEstadoActividades.setText("Estadísticas cargadas correctamente.");
        } catch (RuntimeException excepcion) {
            fechaDesdeCargada = null;
            fechaHastaCargada = null;
            tvEstadisticasActividades.getItems().clear();
            barChartActividades.getData().clear();
            lblEstadoActividades.setText("No se pudieron cargar las estadísticas: " + excepcion.getMessage());
        }
    }
    private boolean filtrosCoincidenConConsulta() {
        return Objects.equals(fechaDesdeCargada, dpDesdeActividades.getValue())
                && Objects.equals(fechaHastaCargada, dpHastaActividades.getValue());
    }
    private boolean validarDatosParaPdf() {
        if (tvEstadisticasActividades.getItems().isEmpty()) {
            lblEstadoActividades.setText("Debe cargar las estadísticas antes de generar el PDF.");
            return false;
        }
        if (!filtrosCoincidenConConsulta()) {
            lblEstadoActividades.setText("Las fechas cambiaron. Presione Cargar antes de generar el PDF.");
            return false;
        }
        return true;
    }
    private ReportConfigDTO crearConfiguracionPdf() {
        DateTimeFormatter formato = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        ReportConfigDTO configuracion = new ReportConfigDTO(
                "Estadísticas de actividades",
                "Actividades registradas por semana",
                "Estadisticas_actividades_" + fechaDesdeCargada + "_al_" + fechaHastaCargada + ".pdf"
        );
        configuracion.setTituloVentana("Guardar estadísticas de actividades");
        configuracion.setTituloGrafico("Gráfico de actividades por semana");
        configuracion.agregarFiltro("Período", fechaDesdeCargada.format(formato) + " al " + fechaHastaCargada.format(formato));
        return configuracion;
    }
    private void generarPdf() {
        if (!validarDatosParaPdf()) return;
        try {
            boolean generado = pdfController.generarConGrafico(crearConfiguracionPdf(), btnGenerarPdfActividades.getScene().getWindow(), tvEstadisticasActividades, barChartActividades);
            lblEstadoActividades.setText(generado
                    ? "El reporte PDF fue generado correctamente."
                    : "Generación del PDF cancelada.");
        } catch (ReportExceptionLogica excepcion) {
            lblEstadoActividades.setText("No se pudo generar el PDF: " + excepcion.getMessage());
            excepcion.printStackTrace();
        }
    }
    private static class EstadisticaSemana {
        private final String semana;
        private final int cantidad;

        public EstadisticaSemana(String semana, int cantidad) {
            this.semana = semana;
            this.cantidad = cantidad;
        }
        public String getSemana() { return semana; }
        public int getCantidad() { return cantidad; }
    }
}
