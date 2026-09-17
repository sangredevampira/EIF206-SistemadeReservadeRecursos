package ui;

import Sistema.DTO.CategoriaDTO;
import Sistema.DTO.RecursoDTO;
import Sistema.DTO.ReportConfigDTO;
import Sistema.DTO.ReservaDTO;
import Sistema.Logica.Exception.ReportExceptionLogica;
import Sistema.Service.CalendarizacionService;
import Sistema.Service.CategoriaService;
import Sistema.Service.RecursoService;
import Sistema.Service.ReservaService;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

public class CalendarizacionController {
    @FXML private DatePicker dpFecha;
    @FXML private ComboBox<CategoriaDTO> cbCategoria;
    @FXML private Button btnCargar;
    @FXML private Button btnLimpiar;
    @FXML private Button btnGenerarPdf;
    @FXML private TableView<String> tvCalendarizacion;
    @FXML private TableColumn<String, String> colHora;
    @FXML private Label lblEstado;
    private LocalDate fechaCargada;
    private CategoriaDTO categoriaCargada;
    private List<RecursoDTO> recursosCargados = List.of();
    private List<ReservaDTO> reservasCargadas = List.of();
    private final CategoriaService categoriaService = new CategoriaService();
    private final RecursoService recursoService = new RecursoService();
    private final ReservaService reservaService = new ReservaService();
    private final PdfController pdfController = new PdfController();
    private final CalendarizacionService calendarizacionRecursosService = new CalendarizacionService();

    @FXML
    private void initialize() {
        colHora.setCellValueFactory(fila -> new ReadOnlyStringWrapper(fila.getValue()));
        lblEstado.setText("Seleccione una fecha y una categoría para cargar la calendarización.");
        btnCargar.setOnAction(evento -> cargarCalendarizacion());
        btnLimpiar.setOnAction(evento -> limpiarCalendarizacion());
        btnGenerarPdf.setOnAction(evento -> generarPdf());
        cargarCategorias();
    }
    private void cargarCategorias() {
        try {
            cbCategoria.getItems().setAll(categoriaService.listarTodos());
        } catch (RuntimeException excepcion) {
            lblEstado.setText("No se pudieron cargar las categorías: " + excepcion.getMessage());
        }
    }
    private void cargarHorasDelDia() {
        tvCalendarizacion.getItems().setAll(calendarizacionRecursosService.generarHorasDelDia());
    }
    private String obtenerEstadoRecurso(RecursoDTO recurso, String hora) {
        return calendarizacionRecursosService.obtenerEstadoRecurso(
                recurso, fechaCargada, hora, reservasCargadas);
    }
    private void aplicarEstiloColumna(TableColumn<String, String> columnaRecurso) {
        columnaRecurso.setCellFactory(columna -> new TableCell<>() {
            @Override
            protected void updateItem(String estado, boolean celdaVacia) {
                super.updateItem(estado, celdaVacia);
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
        tvCalendarizacion.getColumns().setAll(colHora);
        for (RecursoDTO recurso : recursosCargados) {
            TableColumn<String, String> columnaRecurso = new TableColumn<>(recurso.getDescripcion());
            columnaRecurso.setPrefWidth(280);
            columnaRecurso.setCellValueFactory(fila ->
                    new ReadOnlyStringWrapper(obtenerEstadoRecurso(recurso, fila.getValue())));
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
        try {
            recursosCargados = recursoService.filtrarRecursos(categoriaCargada.getId());
            if (recursosCargados.isEmpty()) {
                tvCalendarizacion.getItems().clear();
                tvCalendarizacion.getColumns().setAll(colHora);
                reservasCargadas = List.of();
                fechaCargada = null;
                categoriaCargada = null;
                lblEstado.setText("No existen recursos registrados para la categoría seleccionada.");
                return;
            }

            reservasCargadas = reservaService.buscarPorPeriodo(fechaCargada, fechaCargada);
            crearColumnasRecursos();
            cargarHorasDelDia();
            tvCalendarizacion.refresh();
            lblEstado.setText("Calendarización cargada correctamente.");
        } catch (RuntimeException excepcion) {
            recursosCargados = List.of();
            reservasCargadas = List.of();
            fechaCargada = null;
            categoriaCargada = null;
            tvCalendarizacion.getItems().clear();
            tvCalendarizacion.getColumns().setAll(colHora);
            lblEstado.setText("No se pudo cargar la calendarización: " + excepcion.getMessage());
        }
    }
    private void limpiarCalendarizacion() {
        dpFecha.setValue(null);
        cbCategoria.getSelectionModel().clearSelection();
        fechaCargada = null;
        recursosCargados = List.of();
        reservasCargadas = List.of();
        categoriaCargada = null;
        tvCalendarizacion.getItems().clear();
        tvCalendarizacion.getColumns().setAll(colHora);
        lblEstado.setText("Seleccione una fecha y una categoría para cargar la calendarización.");
    }
    private boolean validarDatosParaPdf() {
        if (tvCalendarizacion.getItems().isEmpty()) {
            lblEstado.setText("Debe cargar la calendarización antes de generar el PDF.");
            return false;
        }
        CategoriaDTO categoriaSeleccionada = cbCategoria.getValue();

        if (fechaCargada == null || categoriaCargada == null
                || !Objects.equals(fechaCargada, dpFecha.getValue())
                || categoriaSeleccionada == null
                || !Objects.equals(categoriaCargada.getId(), categoriaSeleccionada.getId())) {
            lblEstado.setText("Los filtros cambiaron. Presione Cargar antes de generar el PDF.");
            return false;
        }
        return true;
    }
    private ReportConfigDTO crearConfiguracionPdf() {
        DateTimeFormatter formato = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        ReportConfigDTO configuracion = new ReportConfigDTO(
                "Calendarización de recursos",
                "Disponibilidad de recursos por hora",
                "Calendarizacion_" + fechaCargada + ".pdf"
        );
        configuracion.setTituloVentana("Guardar reporte de calendarización");
        configuracion.setHorizontal(true);
        configuracion.agregarFiltro("Fecha", fechaCargada.format(formato));
        configuracion.agregarFiltro("Categoría", categoriaCargada.getDescripcion());
        return configuracion;
    }
    private void generarPdf() {
        if (!validarDatosParaPdf()) return;
        try {
            boolean generado = pdfController.generar(crearConfiguracionPdf(), btnGenerarPdf.getScene().getWindow(), tvCalendarizacion);
            lblEstado.setText(generado
                    ? "El reporte PDF fue generado correctamente."
                    : "Generación del PDF cancelada.");
        } catch (ReportExceptionLogica excepcion) {
            lblEstado.setText("No se pudo generar el PDF: " + excepcion.getMessage());
            excepcion.printStackTrace();
        }
    }
}
