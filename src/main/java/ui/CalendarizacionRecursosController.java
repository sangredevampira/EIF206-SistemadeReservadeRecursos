package ui;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import Sistema.util.SelectorArchivoPdfUtil;
import java.io.File;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

//Imports para los PDF CLASES
import Sistema.DTO.ReportHeaderDTO;
import Sistema.DTO.ReportTableDTO;
import Sistema.Logica.ReportExceptionLogica;
import java.util.ArrayList;
import Sistema.Service.PdfReportService;

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

    private final PdfReportService pdfReportService =
            new PdfReportService();

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

    private void generarPdf() {

        if (tvCalendarizacion.getItems().isEmpty()) {
            lblEstado.setText("Debe cargar la calendarización " + "antes de generar el PDF.");
            return;
        }
        if (!Objects.equals(fechaCargada, dpFecha.getValue()) || !Objects.equals(categoriaCargada, cbCategoria.getValue())) {
            lblEstado.setText("Los filtros cambiaron. " + "Presione Cargar antes de generar el PDF.");
            return;
        }
        File archivoSeleccionado = SelectorArchivoPdfUtil.seleccionar(btnGenerarPdf.getScene().getWindow(),
                "Guardar reporte de calendarización", "Calendarizacion_" + fechaCargada + ".pdf");
        if (archivoSeleccionado == null) {
            lblEstado.setText("Generación del PDF cancelada.");
            return;
        }
        DateTimeFormatter formatoFecha = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        String usuarioGenerador = System.getProperty("user.name", "Usuario no identificado");
        ReportHeaderDTO encabezado = new ReportHeaderDTO("Sistema de Gestión de Recursos", "Calendarización de recursos", usuarioGenerador);
        encabezado.agregarFiltro("Fecha", fechaCargada.format(formatoFecha));
        encabezado.agregarFiltro("Categoría", categoriaCargada);
        List<String> nombresColumnas = tvCalendarizacion.getColumns().stream().map(TableColumn::getText).toList();
        ReportTableDTO tabla = new ReportTableDTO("Disponibilidad de recursos por hora", nombresColumnas);

        for (String hora : tvCalendarizacion.getItems()) {
            List<String> valoresFila = new ArrayList<>();

            for (TableColumn<String, ?> columna : tvCalendarizacion.getColumns()) {
                valoresFila.add(obtenerValorCelda(columna, hora));
            }
            tabla.agregarFila(valoresFila);
        }
        try {
            pdfReportService.generarReporteHorizontal(archivoSeleccionado, encabezado, tabla);
            lblEstado.setText("El reporte PDF fue generado correctamente.");
        } catch (ReportExceptionLogica excepcion) {
            lblEstado.setText("No se pudo generar el PDF: " + excepcion.getMessage());
            excepcion.printStackTrace();
        }
    }
}
