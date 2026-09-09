package ui;

import Sistema.DTO.EstadoReserva;
import Sistema.DTO.ReportConfigDTO;
import Sistema.DTO.ReservaDTO;
import Sistema.Exception.ReportExceptionLogica;
import Sistema.Service.ReservaService;
import Sistema.util.HorarioSistema;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ProgramacionActividadesController {
    private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm");
    @FXML private DatePicker dpFechaReferencia;
    @FXML private Button btnCargar;
    @FXML private Button btnLimpiar;
    @FXML private Button btnGenerarPdf;
    @FXML private Label lblRangoSemana;
    @FXML private Label lblEstado;
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
    private final PdfController pdfController = new PdfController();
    private final ReservaService reservaService = new ReservaService();

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
    private void mostrarActividadesEnTabla(List<ReservaDTO> reservaciones) {
        int cantidadBloques = (24 * 60) / HorarioSistema.MINUTOS_POR_BLOQUE;
        for (ReservaDTO reserva : reservaciones) {
            if (reserva.getEstado() != EstadoReserva.ACTIVA
                    || reserva.getFecha() == null
                    || reserva.getHoraInicio() == null
                    || reserva.getHoraFin() == null) {
                continue;
            }
            LocalTime inicioBloque = LocalTime.MIDNIGHT;
            for (int bloque = 0; bloque < cantidadBloques; bloque++) {
                LocalTime finBloque = bloque == cantidadBloques - 1
                        ? LocalTime.MAX
                        : inicioBloque.plusMinutes(HorarioSistema.MINUTOS_POR_BLOQUE);

                boolean ocupaBloque = reserva.getHoraInicio().isBefore(finBloque)
                        && reserva.getHoraFin().isAfter(inicioBloque);

                if (ocupaBloque) {
                    registrarActividadEnTabla(
                            reserva.getFecha(),
                            inicioBloque.format(FORMATO_HORA),
                            reserva.getNombreActividad(),
                            reserva.getNombreFuncionario()
                    );
                }
                inicioBloque = inicioBloque.plusMinutes(HorarioSistema.MINUTOS_POR_BLOQUE);
            }
        }
    }
    private void cargarActividadesDesdeJson() {
        List<ReservaDTO> reservaciones = reservaService.buscarPorPeriodo(fechaInicioSemana, fechaFinSemana);
        mostrarActividadesEnTabla(reservaciones);
    }
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
        try {
            cargarActividadesDesdeJson();
            cargarHorasDelDia();
            tvActividades.refresh();
            lblEstado.setText("Programación semanal cargada correctamente.");
        } catch (RuntimeException excepcion) {
            actividadesPorFecha.clear();
            tvActividades.getItems().clear();
            lblEstado.setText("No se pudo cargar la programación: " + excepcion.getMessage());
        }
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
        tvActividades.getItems().clear();
        LocalTime hora = LocalTime.MIDNIGHT;
        int cantidadBloques = (24 * 60) / HorarioSistema.MINUTOS_POR_BLOQUE;
        for (int bloque = 0; bloque < cantidadBloques; bloque++) {
            tvActividades.getItems().add(hora.format(FORMATO_HORA));
            hora = hora.plusMinutes(HorarioSistema.MINUTOS_POR_BLOQUE);
        }
    }
    private boolean validarDatosParaPdf() {
        if (tvActividades.getItems().isEmpty() || fechaInicioSemana == null || fechaFinSemana == null) {
            lblEstado.setText("Debe cargar una semana antes de generar el PDF.");
            return false;
        }
        if (dpFechaReferencia.getValue() == null) {
            lblEstado.setText("Debe seleccionar y cargar una semana.");
            return false;
        }
        LocalDate inicioSeleccionado = dpFechaReferencia.getValue().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        if (!Objects.equals(fechaInicioSemana, inicioSeleccionado)) {
            lblEstado.setText("La fecha cambió. Presione Cargar antes de generar el PDF.");
            return false;
        }
        return true;
    }
    private ReportConfigDTO crearConfiguracionPdf() {
        DateTimeFormatter formato = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        ReportConfigDTO configuracion = new ReportConfigDTO(
                "Programación semanal de actividades", "Actividades programadas por día y hora",
                "Programacion_semanal_" + fechaInicioSemana + "_al_" + fechaFinSemana + ".pdf"
        );
        configuracion.setTituloVentana("Guardar programación semanal");
        configuracion.setHorizontal(true);
        configuracion.agregarFiltro("Período", fechaInicioSemana.format(formato) + " al " + fechaFinSemana.format(formato));
        return configuracion;
    }
    private void generarPdf() {
        if (!validarDatosParaPdf()) return;
        try {
            boolean generado = pdfController.generar(crearConfiguracionPdf(), btnGenerarPdf.getScene().getWindow(), tvActividades);
            lblEstado.setText(generado
                    ? "El reporte semanal fue generado correctamente."
                    : "Generación del PDF cancelada.");
        } catch (ReportExceptionLogica excepcion) {
            lblEstado.setText("No se pudo generar el PDF: " + excepcion.getMessage());
            excepcion.printStackTrace();
        }
    }
}
