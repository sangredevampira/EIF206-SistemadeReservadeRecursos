package ui;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;

import java.awt.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

//imports para los PDF
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

public class CalendarizacionRecursosController {

    @FXML private DatePicker dpFecha;
    @FXML private ComboBox<String> cbCategoria;
    @FXML private Button btnCargar;
    @FXML private Button btnLimpiar;
    @FXML private Button btnGenerarPdf;
    @FXML private TableView<String> tvCalendarizacion;
    @FXML private TableColumn<String, String> colHora;
    @FXML private Label lblEstado;

    private LocalDate fechaCargada;
    private String categoriaCargada;

    @FXML
    private void initialize() {
        // Configurar la columna que mostrará las horas
        colHora.setCellValueFactory(fila -> new ReadOnlyStringWrapper(fila.getValue()));
        // Carga información temporal en el ComboBox
        cargarCategoriasTemporales();
        // Preparar los eventos de los botones
        btnCargar.setOnAction(evento -> cargarCalendarizacion());
        btnLimpiar.setOnAction(evento -> limpiarCalendarizacion());
        btnGenerarPdf.setOnAction(evento -> generarPdf());
        lblEstado.setText("Seleccione una fecha y una categoría para cargar la calendarización.");
    }

    private void cargarCategoriasTemporales() {
        cbCategoria.getItems().setAll("Sala para 10 personas", "Laptop Windows 11", "Proyector multimedia");
    }

    private void cargarHorasDelDia() {
        // Elimina las horas anteriores para evitar duplicados
        tvCalendarizacion.getItems().clear();
        // Agrega una fila por cada hora del día
        for (int hora = 0; hora < 24; hora++) {
            tvCalendarizacion.getItems().add(String.format("%02d:00", hora));
        }
    }

    private List<String> obtenerRecursosTemporales(String categoria) {
        if ("Sala para 10 personas".equals(categoria))
            return List.of("Sala 1 - Primer piso", "Sala 2 - Segundo piso");
        if ("Laptop Windows 11".equals(categoria))
            return List.of("Laptop #238715", "Laptop #238716", "Laptop #238717");
        if ("Proyector multimedia".equals(categoria))
            return List.of("Proyector #01", "Proyector #02");
        return List.of();
    }

    private String obtenerEstadoTemporal(String nombreRecurso, String hora, LocalDate fecha) {
        if (fecha == null) return "Disponible";

        int numeroHora = Integer.parseInt(hora.substring(0, 2));
        // Horario temporal de funcionamiento
        if (numeroHora < 6 || numeroHora >= 22) return "Fuera de horario";

        DayOfWeek diaSemana = fecha.getDayOfWeek();

        // Reservaciones temporales de los lunes
        if (diaSemana == DayOfWeek.MONDAY && "Sala 1 - Primer piso".equals(nombreRecurso)
                && ("08:00".equals(hora) || "09:00".equals(hora)))
            return "Reservado - Reunión de equipo - María Pérez";

        // Reservaciones temporales de los martes
        if (diaSemana == DayOfWeek.TUESDAY && "Sala 2 - Segundo piso".equals(nombreRecurso)
                && "14:00".equals(hora))
            return "Reservado - Charla técnica - Carlos Rojas";

        // Reservaciones temporales de los miércoles
        if (diaSemana == DayOfWeek.WEDNESDAY && "Laptop #238716".equals(nombreRecurso)
                && ("10:00".equals(hora) || "11:00".equals(hora)))
            return "Reservado - Capacitación - Ana Mora";

        // Reservaciones temporales de los jueves
        if (diaSemana == DayOfWeek.THURSDAY && "Proyector #01".equals(nombreRecurso)
                && "15:00".equals(hora))
            return "Reservado - Presentación institucional - Luis Vega";

        return "Disponible";
    }

    private void aplicarEstiloColumna(TableColumn<String, String> columnaRecurso) {
        columnaRecurso.setCellFactory(columna -> new TableCell<>() {
            @Override
            protected void updateItem(String estado, boolean celdaVacia) {
                super.updateItem(estado, celdaVacia);
                // Limpia el contenido y el estilo anterior de la celda
                setText(null);
                setStyle("");
                setWrapText(true);
                if (celdaVacia || estado == null) return;
                setText(estado);
                if (estado.startsWith("Disponible")) {
                    setStyle("-fx-background-color: #DFF5E1;" + "-fx-text-fill: #1B5E20;" + "-fx-alignment: CENTER-LEFT;" + "-fx-padding: 6px;");
                } else if (estado.startsWith("Reservado")) {
                    setStyle("-fx-background-color: #FFF3CD;" + "-fx-text-fill: #8A5A00;" + "-fx-alignment: CENTER-LEFT;" + "-fx-padding: 6px;");
                } else if (estado.startsWith("Fuera de horario")) {
                    setStyle("-fx-background-color: #E9ECEF;" + "-fx-text-fill: #495057;" + "-fx-alignment: CENTER-LEFT;" + "-fx-padding: 6px;");
                }
            }
        });
    }

    private void crearColumnasRecursos() {
        // Elimina las columnas creadas en una carga anterior
        // y conserva solamente la columna Hora
        tvCalendarizacion.getColumns().setAll(colHora);
        for (String nombreRecurso : obtenerRecursosTemporales(categoriaCargada)) {
            TableColumn<String, String> columnaRecurso = new TableColumn<>(nombreRecurso);
            columnaRecurso.setPrefWidth(280);
            columnaRecurso.setCellValueFactory(fila -> new ReadOnlyStringWrapper(
                    obtenerEstadoTemporal(nombreRecurso, fila.getValue(), fechaCargada)));
            aplicarEstiloColumna(columnaRecurso);
            tvCalendarizacion.getColumns().add(columnaRecurso);
        }
    }

    private void cargarCalendarizacion() {
        if (dpFecha.getValue() == null) {
            lblEstado.setText("Debe seleccionar una fecha.");
            return;
        }
        if (cbCategoria.getValue() == null) {
            lblEstado.setText("Debe seleccionar una categoría.");
            return;
        }
        fechaCargada = dpFecha.getValue();
        categoriaCargada = cbCategoria.getValue();
        // Crea una columna por cada recurso de la categoría
        crearColumnasRecursos();
        // Crea las 24 filas correspondientes a las horas
        cargarHorasDelDia();
        lblEstado.setText("Calendarización cargada para la fecha " + dpFecha.getValue()
                + " y la categoría " + cbCategoria.getValue() + ".");
    }

    private void limpiarCalendarizacion() {
        dpFecha.setValue(null);
        cbCategoria.getSelectionModel().clearSelection();
        fechaCargada = null;
        categoriaCargada = null;
        tvCalendarizacion.getItems().clear();
        tvCalendarizacion.getColumns().setAll(colHora);
        lblEstado.setText("Seleccione una fecha y una categoría para cargar la calendarización.");
    }

    private String obtenerValorCelda(TableColumn<String, ?> columna, String hora) {
        var valorObservable = columna.getCellObservableValue(hora);
        if (valorObservable == null || valorObservable.getValue() == null) return "";
        return valorObservable.getValue().toString();
    }

    //Pendiente de terminar, por ahora solo valida informacion
    private void generarPdf() {
        if (tvCalendarizacion.getItems().isEmpty()) {
            lblEstado.setText("Debe cargar la calendarización antes de generar el PDF.");
            return;
        }
        if (!Objects.equals(fechaCargada, dpFecha.getValue())
                || !Objects.equals(categoriaCargada, cbCategoria.getValue())) {
            lblEstado.setText("Los filtros cambiaron. Presione Cargar antes de generar el PDF.");
            return;
        }

        FileChooser selectorArchivo = new FileChooser();
        selectorArchivo.setTitle("Guardar reporte de calendarización");
        selectorArchivo.getExtensionFilters().add(new FileChooser.ExtensionFilter("Documento PDF", "*.pdf"));
        selectorArchivo.setInitialFileName("Calendarizacion_" + fechaCargada + ".pdf");

        File archivoSeleccionado = selectorArchivo.showSaveDialog(btnGenerarPdf.getScene().getWindow());
        // El usuario cerró la ventana sin seleccionar un archivo
        if (archivoSeleccionado == null) {
            lblEstado.setText("Generación del PDF cancelada.");
            return;
        }
        if (!archivoSeleccionado.getName().toLowerCase().endsWith(".pdf"))
            archivoSeleccionado = new File(archivoSeleccionado.getAbsolutePath() + ".pdf");

        Document documento = new Document(PageSize.A4.rotate(), 25, 25, 25, 25);
        try {
            PdfWriter.getInstance(documento, new FileOutputStream(archivoSeleccionado));
            documento.open();

            Font fuenteTitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
            Font fuenteEncabezado = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
            Font fuenteContenido = FontFactory.getFont(FontFactory.HELVETICA, 8);
            DateTimeFormatter formatoFecha = DateTimeFormatter.ofPattern("dd/MM/yyyy");

            documento.add(new Paragraph("Calendarización de recursos", fuenteTitulo));
            documento.add(new Paragraph("Fecha: " + fechaCargada.format(formatoFecha)));
            documento.add(new Paragraph("Categoría: " + categoriaCargada));
            documento.add(new Paragraph(" "));

            PdfPTable tablaPdf = new PdfPTable(tvCalendarizacion.getColumns().size());
            tablaPdf.setWidthPercentage(100);
            tablaPdf.setHeaderRows(1);

            // Encabezados de la tabla
            for (TableColumn<String, ?> columna : tvCalendarizacion.getColumns()) {
                PdfPCell encabezado = new PdfPCell(new Phrase(columna.getText(), fuenteEncabezado));
                encabezado.setBackgroundColor(new Color(220, 220, 220));
                encabezado.setPadding(5);
                tablaPdf.addCell(encabezado);
            }

            // Contenido de la tabla
            for (String hora : tvCalendarizacion.getItems()) {
                for (TableColumn<String, ?> columna : tvCalendarizacion.getColumns()) {
                    String valor = obtenerValorCelda(columna, hora);
                    PdfPCell celda = new PdfPCell(new Phrase(valor, fuenteContenido));
                    celda.setPadding(4);
                    if (valor.startsWith("Disponible"))
                        celda.setBackgroundColor(new Color(223, 245, 225));
                    else if (valor.startsWith("Reservado"))
                        celda.setBackgroundColor(new Color(255, 243, 205));
                    else if (valor.startsWith("Fuera de horario"))
                        celda.setBackgroundColor(new Color(233, 236, 239));
                    tablaPdf.addCell(celda);
                }
            }

            documento.add(tablaPdf);
            documento.close();
            lblEstado.setText("El reporte PDF fue generado correctamente.");

        } catch (Exception excepcion) {
            if (documento.isOpen()) documento.close();
            lblEstado.setText("No se pudo generar el PDF: " + excepcion.getMessage());
            excepcion.printStackTrace();
        }
    }
}
