package ui;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import javafx.beans.property.ReadOnlyStringWrapper;
import java.util.HashMap;
import java.util.Map;
import javafx.scene.control.TableCell;

//imports para JSON
// Se habilitarán cuando el Integrante 2 termine estas clases.
// import Sistema.DTO.ReservaDTO;
// import Sistema.Service.ReservaService;
// import java.util.List;

//imports para pdf
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
import java.util.Objects;

public class ProgramacionActividadesController {

    // Controles para seleccionar y cargar la semana
    @FXML private DatePicker dpFechaReferencia;
    @FXML private Button btnCargar;
    @FXML private Button btnLimpiar;
    @FXML private Button btnGenerarPdf;

    // Información mostrada en la pantalla
    @FXML private Label lblRangoSemana;
    @FXML private Label lblEstado;

    // Tabla semanal
    @FXML private TableView<String> tvActividades;
    @FXML private TableColumn<String, String> colHora;
    @FXML private TableColumn<String, String> colLunes;
    @FXML private TableColumn<String, String> colMartes;
    @FXML private TableColumn<String, String> colMiercoles;
    @FXML private TableColumn<String, String> colJueves;
    @FXML private TableColumn<String, String> colViernes;
    @FXML private TableColumn<String, String> colSabado;
    @FXML private TableColumn<String, String> colDomingo;

    private LocalDate fechaInicioSemana;
    private LocalDate fechaFinSemana;
    private final Map<LocalDate, Map<String, String>> actividadesPorFecha = new HashMap<>();

    @FXML
    private void initialize() {
        colHora.setCellValueFactory(fila -> new ReadOnlyStringWrapper(fila.getValue()));
        configurarColumnasActividades();
        colHora.setMinWidth(75);
        colHora.setMaxWidth(75);
        btnCargar.setOnAction(evento -> cargarProgramacion());
        btnLimpiar.setOnAction(evento -> limpiarProgramacion());
        btnGenerarPdf.setOnAction(evento -> generarPdf());
        lblRangoSemana.setText("Semana no seleccionada");
        lblEstado.setText("Seleccione una fecha para cargar la programación semanal.");
    }

    //private void cargarActividadesDesdeJson() {
        /*
         * Este código se habilitará cuando exista ReservaService.
         *
         * ReservaService reservaService = new ReservaService();
         *
         * List<ReservaDTO> reservaciones =
         *         reservaService.buscarPorPeriodo(
         *                 fechaInicioSemana,
         *                 fechaFinSemana
         *         );
         *
         * mostrarActividadesEnTabla(reservaciones);
         */
    //}

    private void registrarActividadEnTabla(LocalDate fecha, String hora, String nombreActividad, String nombreFuncionario) {
        Map<String, String> actividadesDelDia = actividadesPorFecha.computeIfAbsent(fecha, clave -> new HashMap<>());
        String textoActividad = nombreActividad + "\n" + nombreFuncionario;
        actividadesDelDia.merge(hora, textoActividad, (actividadAnterior, actividadNueva) -> actividadAnterior + "\n\n" + actividadNueva);
    }

    private void aplicarEstiloActividad(TableColumn<String, String> columna) {
        columna.setCellFactory(columnaActual -> new TableCell<>() {
            @Override
            protected void updateItem(String actividad, boolean celdaVacia) {
                super.updateItem(actividad, celdaVacia);
                setText(null);
                setStyle("");
                setWrapText(true);
                if (celdaVacia || actividad == null || actividad.isBlank()) return;
                setText(actividad);
                setStyle("-fx-background-color: #FFF0C7;" + "-fx-text-fill: #8A5A00;" + "-fx-alignment: CENTER-LEFT;" + "-fx-padding: 6px;");
            }
        });
    }

    private void calcularRangoSemana(LocalDate fechaReferencia) {
        fechaInicioSemana = fechaReferencia.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        fechaFinSemana = fechaInicioSemana.plusDays(6);
        DateTimeFormatter formatoFecha = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        lblRangoSemana.setText("Del " + fechaInicioSemana.format(formatoFecha) + " al " + fechaFinSemana.format(formatoFecha));
        actualizarEncabezadosSemana();
    }

    private void actualizarEncabezadosSemana() {
        DateTimeFormatter formatoCorto = DateTimeFormatter.ofPattern("dd/MM");
        colLunes.setText("Lunes\n" + fechaInicioSemana.format(formatoCorto));
        colMartes.setText("Martes\n" + fechaInicioSemana.plusDays(1).format(formatoCorto));
        colMiercoles.setText("Miércoles\n" + fechaInicioSemana.plusDays(2).format(formatoCorto));
        colJueves.setText("Jueves\n" + fechaInicioSemana.plusDays(3).format(formatoCorto));
        colViernes.setText("Viernes\n" + fechaInicioSemana.plusDays(4).format(formatoCorto));
        colSabado.setText("Sábado\n" + fechaInicioSemana.plusDays(5).format(formatoCorto));
        colDomingo.setText("Domingo\n" + fechaInicioSemana.plusDays(6).format(formatoCorto));
    }

    private void cargarProgramacion() {
        if (dpFechaReferencia.getValue() == null) {
            lblEstado.setText("Debe seleccionar una fecha de referencia.");
            return;
        }
        calcularRangoSemana(dpFechaReferencia.getValue());
        cargarHorasDelDia();
        actividadesPorFecha.clear();
        // Pendiente hasta que exista ReservaService.
        // cargarActividadesDesdeJson();
        tvActividades.refresh();
        lblEstado.setText("Estructura semanal preparada. Pendiente cargar las reservaciones desde JSON.");
    }

    private void limpiarProgramacion() {
        dpFechaReferencia.setValue(null);
        fechaInicioSemana = null;
        fechaFinSemana = null;
        actividadesPorFecha.clear();
        tvActividades.getItems().clear();
        restablecerEncabezadosSemana();
        lblRangoSemana.setText("Semana no seleccionada");
        lblEstado.setText("Seleccione una fecha para cargar la programación semanal.");
    }

    private void restablecerEncabezadosSemana() {
        colLunes.setText("Lunes");
        colMartes.setText("Martes");
        colMiercoles.setText("Miércoles");
        colJueves.setText("Jueves");
        colViernes.setText("Viernes");
        colSabado.setText("Sábado");
        colDomingo.setText("Domingo");
    }

    private String obtenerTextoActividad(LocalDate fecha, String hora) {
        Map<String, String> actividadesDelDia = actividadesPorFecha.get(fecha);
        if (actividadesDelDia == null) return "";
        return actividadesDelDia.getOrDefault(hora, "");
    }

    private void configurarColumnaDia(TableColumn<String, String> columna, int diasDespuesDelLunes) {
        columna.setCellValueFactory(fila -> {
            if (fechaInicioSemana == null) return new ReadOnlyStringWrapper("");
            LocalDate fechaColumna = fechaInicioSemana.plusDays(diasDespuesDelLunes);
            String hora = fila.getValue();
            return new ReadOnlyStringWrapper(obtenerTextoActividad(fechaColumna, hora));
        });
        aplicarEstiloActividad(columna);
    }

    private void configurarColumnasActividades() {
        configurarColumnaDia(colLunes, 0);
        configurarColumnaDia(colMartes, 1);
        configurarColumnaDia(colMiercoles, 2);
        configurarColumnaDia(colJueves, 3);
        configurarColumnaDia(colViernes, 4);
        configurarColumnaDia(colSabado, 5);
        configurarColumnaDia(colDomingo, 6);
    }

    private void cargarHorasDelDia() {
        // Evita duplicar las horas al presionar Cargar varias veces
        tvActividades.getItems().clear();
        for (int hora = 0; hora < 24; hora++) {
            tvActividades.getItems().add(String.format("%02d:00", hora));
        }
    }

    private String obtenerValorCelda(TableColumn<String, ?> columna, String hora) {
        var valorObservable = columna.getCellObservableValue(hora);
        if (valorObservable == null || valorObservable.getValue() == null) return "";
        return valorObservable.getValue().toString();
    }

    private void generarPdf() {
        if (tvActividades.getItems().isEmpty() || fechaInicioSemana == null || fechaFinSemana == null) {
            lblEstado.setText("Debe cargar una semana antes de generar el PDF.");
            return;
        }
        if (dpFechaReferencia.getValue() == null) {
            lblEstado.setText("Debe seleccionar y cargar una semana.");
            return;
        }
        LocalDate inicioSemanaSeleccionada = dpFechaReferencia.getValue().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        if (!Objects.equals(fechaInicioSemana, inicioSemanaSeleccionada)) {
            lblEstado.setText("La fecha cambió. Presione Cargar antes de generar el PDF.");
            return;
        }
        FileChooser selectorArchivo = new FileChooser();
        selectorArchivo.setTitle("Guardar programación semanal");
        selectorArchivo.getExtensionFilters().add(new FileChooser.ExtensionFilter("Documento PDF", "*.pdf"));
        selectorArchivo.setInitialFileName("Programacion_semanal_" + fechaInicioSemana + "_al_" + fechaFinSemana + ".pdf");
        File archivoSeleccionado = selectorArchivo.showSaveDialog(btnGenerarPdf.getScene().getWindow());
        if (archivoSeleccionado == null) {
            lblEstado.setText("Generación del PDF cancelada.");
            return;
        }
        if (!archivoSeleccionado.getName().toLowerCase().endsWith(".pdf")) {
            archivoSeleccionado = new File(archivoSeleccionado.getAbsolutePath() + ".pdf");
        }
        Document documento = new Document(PageSize.A4.rotate(), 25, 25, 25, 25);
        try {
            PdfWriter.getInstance(documento, new FileOutputStream(archivoSeleccionado));
            documento.open();
            Font fuenteTitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
            Font fuenteEncabezado = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8);
            Font fuenteContenido = FontFactory.getFont(FontFactory.HELVETICA, 7);
            DateTimeFormatter formatoFecha = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            documento.add(new Paragraph("Programación semanal de actividades", fuenteTitulo));
            documento.add(new Paragraph("Semana del " + fechaInicioSemana.format(formatoFecha) + " al " + fechaFinSemana.format(formatoFecha)));
            documento.add(new Paragraph(" "));
            PdfPTable tablaPdf = new PdfPTable(tvActividades.getColumns().size());
            tablaPdf.setWidthPercentage(100);
            tablaPdf.setHeaderRows(1);
            for (TableColumn<String, ?> columna : tvActividades.getColumns()) {
                PdfPCell encabezado = new PdfPCell(new Phrase(columna.getText(), fuenteEncabezado));
                encabezado.setBackgroundColor(new Color(220, 220, 220));
                encabezado.setPadding(4);
                tablaPdf.addCell(encabezado);
            }
            for (String hora : tvActividades.getItems()) {
                for (TableColumn<String, ?> columna : tvActividades.getColumns()) {
                    String valor = obtenerValorCelda(columna, hora);
                    PdfPCell celda = new PdfPCell(new Phrase(valor, fuenteContenido));
                    celda.setPadding(3);
                    if (!valor.isBlank() && !valor.equals(hora)) {
                        celda.setBackgroundColor(new Color(255, 240, 199));
                    }
                    tablaPdf.addCell(celda);
                }
            }
            documento.add(tablaPdf);
            documento.close();
            lblEstado.setText("El reporte semanal fue generado correctamente.");
        } catch (Exception excepcion) {
            if (documento.isOpen()) documento.close();
            lblEstado.setText("No se pudo generar el PDF: " + excepcion.getMessage());
            excepcion.printStackTrace();
        }
    }
}
