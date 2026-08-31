package ui;

import Sistema.DTO.ReportHeaderDTO;
import Sistema.DTO.ReportTableDTO;
import Sistema.Logica.ReportExceptionLogica;
import Sistema.Service.PdfReportService;
import Sistema.util.GraficoImagenUtil;
import Sistema.util.SelectorArchivoPdfUtil;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;

import java.io.File;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

// import Sistema.DTO.ReservaDTO;
// import Sistema.Service.ReservaService;

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
    private final PdfReportService pdfReportService = new PdfReportService();

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
        // Registra todas las semanas del período inicialmente en cero
        LocalDate semanaActual = primeraSemana;
        while (!semanaActual.isAfter(ultimaSemana)) {
            conteoSemanal.put(semanaActual, 0);
            semanaActual = semanaActual.plusWeeks(1);
        }
        // Cuenta las actividades correspondientes a cada semana
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

    //private void cargarEstadisticasDesdeJson() {
        /*
         * ReservaService reservaService =
         *         new ReservaService();
         *
         * List<ReservaDTO> reservaciones =
         *         reservaService.buscarPorPeriodo(
         *                 fechaDesdeCargada,
                           fechaHastaCargada
         *         );
         *
         * List<LocalDate> fechasActividades =
         *         reservaciones.stream()
         *                 .map(ReservaDTO::getFecha)
         *                 .toList();
         *
         * Map<LocalDate, Integer> conteoSemanal =
         *         contarActividadesPorSemana(
         *                 fechaDesdeCargada,
                           fechaHastaCargada,
         *                 fechasActividades
         *         );
         *
         * mostrarEstadisticas(conteoSemanal);
         */
    //}

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
        tvEstadisticasActividades.getItems().clear();
        barChartActividades.getData().clear();
        // Se habilitará cuando exista ReservaService.
        // cargarEstadisticasDesdeJson();
        lblEstadoActividades.setText("Período seleccionado correctamente. Pendiente consultar las reservaciones desde JSON.");
    }

    private boolean filtrosCoincidenConConsulta() {
        return Objects.equals(fechaDesdeCargada, dpDesdeActividades.getValue())
                && Objects.equals(fechaHastaCargada, dpHastaActividades.getValue());
    }

    private void generarPdf() {
        if (tvEstadisticasActividades.getItems().isEmpty()) {
            lblEstadoActividades.setText("Debe cargar las estadísticas antes de generar el PDF.");
            return;
        }
        if (!filtrosCoincidenConConsulta()) {
            lblEstadoActividades.setText("Las fechas cambiaron. Presione Cargar antes de generar el PDF.");
            return;
        }
        File archivo = SelectorArchivoPdfUtil.seleccionar(btnGenerarPdfActividades.getScene().getWindow(),
                "Guardar estadísticas de actividades", "Estadisticas_actividades_" + fechaDesdeCargada + "_al_" + fechaHastaCargada + ".pdf");
        if (archivo == null) {
            lblEstadoActividades.setText("Generación del PDF cancelada.");
            return;
        }
        DateTimeFormatter formatoFecha = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        ReportHeaderDTO encabezado = new ReportHeaderDTO("Sistema de Gestión de Recursos", "Estadísticas de actividades", System.getProperty("user.name", "Usuario no identificado"));
        encabezado.agregarFiltro("Período", fechaDesdeCargada.format(formatoFecha) + " al " + fechaHastaCargada.format(formatoFecha));
        ReportTableDTO tabla = new ReportTableDTO("Actividades registradas por semana", List.of("Semana", "Cantidad"));

        for (EstadisticaSemana estadistica : tvEstadisticasActividades.getItems()) {
            tabla.agregarFila(estadistica.getSemana(), String.valueOf(estadistica.getCantidad()));
        }
        try {
            byte[] imagenGrafico = GraficoImagenUtil.convertirAPng(barChartActividades);
            pdfReportService.generarReporteConGrafico(archivo, encabezado, tabla, imagenGrafico);
            lblEstadoActividades.setText("El reporte PDF fue generado correctamente.");

        } catch (ReportExceptionLogica excepcion) {
            lblEstadoActividades.setText("No se pudo generar el PDF: " + excepcion.getMessage());
            excepcion.printStackTrace();

        } catch (Exception excepcion) {
            lblEstadoActividades.setText("No se pudo convertir el gráfico: " + excepcion.getMessage());
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
