package ui;

import Sistema.DTO.CategoriaDTO;
import Sistema.DTO.CalendarizacionDTO;
import Sistema.DTO.DisponibilidadRecursoDTO;
import Sistema.DTO.ReportConfigDTO;
import Sistema.Logica.Exception.ReportExceptionLogica;
import Sistema.Red.TareaRed;
import Sistema.Service.ICalendarizacionService;
import Sistema.Service.ICategoriaService;
import Sistema.Service.Red.ProxyCalendarizacionService;
import Sistema.Service.Red.ProxyCategoriaService;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
    private CalendarizacionDTO calendarizacionCargada;
    private final ICategoriaService categoriaService = new ProxyCategoriaService();
    private final ICalendarizacionService calendarizacionService = new ProxyCalendarizacionService();
    private final PdfController pdfController = new PdfController();

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
        TareaRed.ejecutar(
                () -> categoriaService.listarTodos(),
                categorias -> cbCategoria.getItems().setAll(categorias),
                error -> lblEstado.setText("No se pudieron cargar las categorías: " + mensajeDeError(error))
        );
    }
    private static String mensajeDeError(Throwable error) {
        if (error instanceof IllegalArgumentException) return error.getMessage();
        return "No se pudo comunicar con el servidor. Intente de nuevo.";
    }
    private void cargarHorasDelDia() {
        tvCalendarizacion.getItems().setAll(calendarizacionCargada.getHoras());
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
        for (DisponibilidadRecursoDTO disponibilidad : calendarizacionCargada.getRecursos()) {
            TableColumn<String, String> columnaRecurso =
                    new TableColumn<>(disponibilidad.getRecurso().getDescripcion());
            columnaRecurso.setPrefWidth(280);
            columnaRecurso.setCellValueFactory(fila -> {
                int indice = calendarizacionCargada.getHoras().indexOf(fila.getValue());
                return new ReadOnlyStringWrapper(indice < 0 ? "" : disponibilidad.getEstados().get(indice));
            });
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

        LocalDate fecha = dpFecha.getValue();
        CategoriaDTO categoria = cbCategoria.getValue();
        TareaRed.ejecutar(
                () -> calendarizacionService.consultar(categoria.getId(), fecha),
                dto -> {
                    if (dto.getRecursos() == null || dto.getRecursos().isEmpty()) {
                        reiniciarTabla();
                        lblEstado.setText("No existen recursos registrados para la categoría seleccionada.");
                        return;
                    }
                    calendarizacionCargada = dto;
                    fechaCargada = fecha;
                    categoriaCargada = categoria;
                    crearColumnasRecursos();
                    cargarHorasDelDia();
                    tvCalendarizacion.refresh();
                    lblEstado.setText("Calendarización cargada correctamente.");
                },
                error -> {
                    reiniciarTabla();
                    lblEstado.setText(error instanceof IllegalArgumentException
                            ? "No se pudo cargar la calendarización: " + error.getMessage()
                            : mensajeDeError(error));
                }
        );
    }
    private void reiniciarTabla() {
        calendarizacionCargada = null;
        fechaCargada = null;
        categoriaCargada = null;
        tvCalendarizacion.getItems().clear();
        tvCalendarizacion.getColumns().setAll(colHora);
    }
    private void limpiarCalendarizacion() {
        dpFecha.setValue(null);
        cbCategoria.getSelectionModel().clearSelection();
        reiniciarTabla();
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
