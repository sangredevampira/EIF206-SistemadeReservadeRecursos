package ui;

import Sistema.DTO.ReportHeaderDTO;
import Sistema.DTO.ReportTableDTO;
import Sistema.Logica.ReportExceptionLogica;
import Sistema.Service.PdfReportService;
import Sistema.util.SelectorArchivoPdfUtil;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.io.File;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

//imports para JSON
// Se habilitarán cuando el Integrante 2 termine estas clases.
// import Sistema.DTO.ReservaDTO;
// import Sistema.Service.ReservaService;

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
    private final PdfReportService pdfReportService = new PdfReportService();

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
        actividadesPorFecha.clear();
        cargarHorasDelDia();
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
        LocalDate inicioSeleccionado = dpFechaReferencia.getValue()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        if (!Objects.equals(fechaInicioSemana, inicioSeleccionado)) {
            lblEstado.setText("La fecha cambió. Presione Cargar antes de generar el PDF.");
            return;
        }
        File archivo = SelectorArchivoPdfUtil.seleccionar(btnGenerarPdf.getScene().getWindow(), "Guardar programación semanal",
                "Programacion_semanal_" + fechaInicioSemana + "_al_" + fechaFinSemana + ".pdf");
        if (archivo == null) {
            lblEstado.setText("Generación del PDF cancelada.");
            return;
        }
        DateTimeFormatter formatoFecha = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        ReportHeaderDTO encabezado = new ReportHeaderDTO("Sistema de Gestión de Recursos", "Programación semanal de actividades", System.getProperty("user.name", "Usuario no identificado"));
        encabezado.agregarFiltro("Período", fechaInicioSemana.format(formatoFecha) + " al " + fechaFinSemana.format(formatoFecha));
        List<String> nombresColumnas = tvActividades.getColumns().stream().map(TableColumn::getText).toList();

        ReportTableDTO tabla = new ReportTableDTO("Actividades programadas por día y hora", nombresColumnas);

        for (String hora : tvActividades.getItems()) {
            List<String> fila = new ArrayList<>();

            for (TableColumn<String, ?> columna : tvActividades.getColumns()) {
                fila.add(obtenerValorCelda(columna, hora));
            }
            tabla.agregarFila(fila);
        }
        try {
            pdfReportService.generarReporteHorizontal(archivo, encabezado, tabla);
            lblEstado.setText("El reporte semanal fue generado correctamente.");
        } catch (ReportExceptionLogica excepcion) {
            lblEstado.setText("No se pudo generar el PDF: " + excepcion.getMessage());
            excepcion.printStackTrace();
        }
    }
}
