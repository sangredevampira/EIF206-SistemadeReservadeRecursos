package ui;

import Sistema.DTO.ReportConfigDTO;
import Sistema.Logica.Exception.ReportExceptionLogica;
import Sistema.Red.EstadisticasActividadesProxy;
import Sistema.Red.TareaRed;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.paint.Color;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Objects;

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
    private final PdfController pdfController = new PdfController();
    private final EstadisticasActividadesProxy estadisticasActividadesProxy = new EstadisticasActividadesProxy();
    private boolean consultaEnCurso;
    private long numeroConsulta;

    @FXML
    private void initialize() {
        colSemana.setCellValueFactory(fila -> new ReadOnlyStringWrapper(fila.getValue().getSemana()));
        colCantidad.setCellValueFactory(fila -> new ReadOnlyIntegerWrapper(fila.getValue().getCantidad()));
        ejeCantidad.setForceZeroInRange(true);
        ejeSemanas.setTickLabelRotation(0);
        barChartActividades.setCategoryGap(8);
        btnCargarActividades.setOnAction(evento -> cargarEstadisticas());
        btnGenerarPdfActividades.setOnAction(evento -> generarPdf());
        lblEstadoActividades.setText("Seleccione las fechas para consultar las actividades programadas.");
    }

    private void mostrarEstadisticas(Map<LocalDate, Integer> conteoSemanal) {
        tvEstadisticasActividades.getItems().clear();
        barChartActividades.getData().clear();

        int cantidadMaxima = conteoSemanal.values().stream()
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);

        ejeCantidad.setAutoRanging(false);
        ejeCantidad.setLowerBound(0);
        ejeCantidad.setUpperBound(Math.max(1, cantidadMaxima + 1));
        ejeCantidad.setTickUnit(1);
        ejeCantidad.setMinorTickVisible(false);

        DateTimeFormatter formatoFecha = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        DateTimeFormatter formatoGrafico = DateTimeFormatter.ofPattern("dd/MM");
        XYChart.Series<String, Number> serieActividades = new XYChart.Series<>();
        serieActividades.setName("Actividades");
        for (Map.Entry<LocalDate, Integer> resultado : conteoSemanal.entrySet()) {
            LocalDate inicioSemana = resultado.getKey();
            LocalDate finSemana = inicioSemana.plusDays(6);
            int cantidad = resultado.getValue();
            String textoSemana = inicioSemana.format(formatoFecha) + " al " + finSemana.format(formatoFecha);
            String textoSemanaGrafico = inicioSemana.format(formatoGrafico);
            tvEstadisticasActividades.getItems().add(new EstadisticaSemana(textoSemana, cantidad));
            serieActividades.getData().add(new XYChart.Data<>(textoSemanaGrafico, cantidad));
        }
        barChartActividades.getData().add(serieActividades);

        boolean sinActividades = conteoSemanal.isEmpty()
                || conteoSemanal.values().stream()
                .allMatch(cantidad -> cantidad == null || cantidad == 0);

        mostrarEstado(
                sinActividades
                        ? "No hay actividades programadas en el período seleccionado."
                        : "Estadísticas cargadas correctamente.",
                false
        );
    }
    private void cargarEstadisticas() {
        if (consultaEnCurso) {
            return;
        }

        LocalDate desde = dpDesdeActividades.getValue();
        LocalDate hasta = dpHastaActividades.getValue();

        if (desde == null || hasta == null) {
            invalidarDatosCargados();
            limpiarTablaYGrafico();
            mostrarEstado("Debe indicar ambas fechas del período.", true);
            return;
        }

        if (desde.isAfter(hasta)) {
            invalidarDatosCargados();
            limpiarTablaYGrafico();
            mostrarEstado("La fecha 'desde' no puede ser posterior a la fecha 'hasta'.", true);
            return;
        }

        long consultaActual = ++numeroConsulta;
        consultaEnCurso = true;
        btnCargarActividades.setDisable(true);
        invalidarDatosCargados();
        limpiarTablaYGrafico();
        mostrarEstado("Cargando estadísticas de actividades...", false);

        TareaRed.ejecutar(
                () -> estadisticasActividadesProxy.consultar(desde, hasta),
                conteo -> completarConsulta(
                        consultaActual,
                        desde,
                        hasta,
                        conteo
                ),
                error -> mostrarErrorConsulta(consultaActual, error)
        );
    }

    private void completarConsulta(
            long consulta,
            LocalDate desde,
            LocalDate hasta,
            Map<LocalDate, Integer> conteoSemanal
    ) {
        if (consulta != numeroConsulta) {
            return;
        }

        if (!Objects.equals(desde, dpDesdeActividades.getValue())
                || !Objects.equals(hasta, dpHastaActividades.getValue())) {
            finalizarCarga();
            invalidarDatosCargados();
            limpiarTablaYGrafico();
            mostrarEstado(
                    "Las fechas cambiaron. Presione \"Cargar\" nuevamente.",
                    true
            );
            return;
        }

        finalizarCarga();
        fechaDesdeCargada = desde;
        fechaHastaCargada = hasta;
        mostrarEstadisticas(conteoSemanal);
    }

    private void mostrarErrorConsulta(long consulta, Throwable error) {
        if (consulta != numeroConsulta) {
            return;
        }

        finalizarCarga();
        invalidarDatosCargados();
        limpiarTablaYGrafico();

        String detalle = error == null ? null : error.getMessage();
        mostrarEstado(
                detalle == null || detalle.isBlank()
                        ? "No se pudieron cargar las estadísticas. Verifique la conexión con el servidor."
                        : "No se pudieron cargar las estadísticas. Verifique la conexión con el servidor: " + detalle,
                true
        );
    }

    private void finalizarCarga() {
        consultaEnCurso = false;
        btnCargarActividades.setDisable(false);
    }

    private void invalidarDatosCargados() {
        fechaDesdeCargada = null;
        fechaHastaCargada = null;
    }

    private void limpiarTablaYGrafico() {
        tvEstadisticasActividades.getItems().clear();
        barChartActividades.getData().clear();
    }
    private boolean filtrosCoincidenConConsulta() {
        return Objects.equals(fechaDesdeCargada, dpDesdeActividades.getValue())
                && Objects.equals(fechaHastaCargada, dpHastaActividades.getValue());
    }
    private boolean validarDatosParaPdf() {
        if (tvEstadisticasActividades.getItems().isEmpty()) {
            mostrarEstado("Cargue primero las estadísticas antes de generar el PDF.", true);
            return false;
        }
        if (!filtrosCoincidenConConsulta()) {
            mostrarEstado("Las fechas cambiaron. Presione Cargar antes de generar el PDF.", true);
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
    private void mostrarEstado(String mensaje, boolean esError) {
        lblEstadoActividades.setTextFill(esError ? Color.web("#C0392B") : Color.web("#555555"));
        lblEstadoActividades.setText(mensaje);
    }
}
