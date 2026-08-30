package ui;

import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.chart.XYChart;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

// import Sistema.DTO.ReservaDTO;
// import Sistema.Service.ReservaService;

import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import javafx.stage.FileChooser;
import java.awt.Color;
import java.io.File;
import java.io.FileOutputStream;
import com.lowagie.text.Image;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.WritableImage;
import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
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

    private Image crearImagenGrafico() throws Exception {
        WritableImage imagenJavaFx = barChartActividades.snapshot(null, null);
        try (ByteArrayOutputStream salidaImagen = new ByteArrayOutputStream()) {
            ImageIO.write(SwingFXUtils.fromFXImage(imagenJavaFx, null), "png", salidaImagen);
            Image imagenPdf = Image.getInstance(salidaImagen.toByteArray());
            imagenPdf.scaleToFit(500, 300);
            imagenPdf.setAlignment(Image.ALIGN_CENTER);
            return imagenPdf;
        }
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
        FileChooser selectorArchivo = new FileChooser();
        selectorArchivo.setTitle("Guardar estadísticas de actividades");
        selectorArchivo.getExtensionFilters().add(new FileChooser.ExtensionFilter("Documento PDF", "*.pdf"));
        selectorArchivo.setInitialFileName("Estadisticas_actividades_" + fechaDesdeCargada + "_al_" + fechaHastaCargada + ".pdf");
        File archivoSeleccionado = selectorArchivo.showSaveDialog(btnGenerarPdfActividades.getScene().getWindow());
        if (archivoSeleccionado == null) {
            lblEstadoActividades.setText("Generación del PDF cancelada.");
            return;
        }
        if (!archivoSeleccionado.getName().toLowerCase().endsWith(".pdf")) {
            archivoSeleccionado = new File(archivoSeleccionado.getAbsolutePath() + ".pdf");
        }
        Document documento = new Document(PageSize.A4, 40, 40, 35, 35);
        try {
            PdfWriter.getInstance(documento, new FileOutputStream(archivoSeleccionado));
            documento.open();
            Font fuenteTitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
            Font fuenteEncabezado = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
            Font fuenteContenido = FontFactory.getFont(FontFactory.HELVETICA, 9);
            DateTimeFormatter formatoFecha = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            documento.add(new Paragraph("Estadísticas de actividades", fuenteTitulo));
            documento.add(new Paragraph("Período consultado: " + fechaDesdeCargada.format(formatoFecha) + " al " + fechaHastaCargada.format(formatoFecha)));
            documento.add(new Paragraph(" "));
            PdfPTable tablaPdf = new PdfPTable(2);
            tablaPdf.setWidthPercentage(100);
            tablaPdf.setWidths(new float[]{3, 1});
            PdfPCell encabezadoSemana = new PdfPCell(new Phrase("Semana", fuenteEncabezado));
            encabezadoSemana.setBackgroundColor(new Color(220, 220, 220));
            encabezadoSemana.setPadding(6);
            PdfPCell encabezadoCantidad = new PdfPCell(new Phrase("Cantidad", fuenteEncabezado));
            encabezadoCantidad.setBackgroundColor(new Color(220, 220, 220));
            encabezadoCantidad.setPadding(6);
            tablaPdf.addCell(encabezadoSemana);
            tablaPdf.addCell(encabezadoCantidad);
            for (EstadisticaSemana estadistica : tvEstadisticasActividades.getItems()) {
                PdfPCell celdaSemana = new PdfPCell(new Phrase(estadistica.getSemana(), fuenteContenido));
                celdaSemana.setPadding(6);
                PdfPCell celdaCantidad = new PdfPCell(new Phrase(String.valueOf(estadistica.getCantidad()), fuenteContenido));
                celdaCantidad.setPadding(6);
                tablaPdf.addCell(celdaSemana);
                tablaPdf.addCell(celdaCantidad);
            }
            documento.add(tablaPdf);
            documento.close();
            lblEstadoActividades.setText("El reporte PDF fue generado correctamente.");
        } catch (Exception excepcion) {
            if (documento.isOpen()) documento.close();
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
