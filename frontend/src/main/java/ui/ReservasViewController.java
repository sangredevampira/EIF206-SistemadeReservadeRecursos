package ui;

import Sistema.DTO.RecursoDTO;
import Sistema.DTO.ReservaDTO;
import Sistema.DTO.Funcionario;
import Sistema.DTO.CategoriaDTO;
import Sistema.util.SessionManager;
import Sistema.Service.IReservaService;
import Sistema.Service.ICategoriaService;
import Sistema.Service.Red.ProxyReservaService;
import Sistema.Service.Red.ProxyCategoriaService;
import Sistema.Red.TareaRed;
import Sistema.util.HorarioSistema;

import java.net.URL;
import java.util.List;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ResourceBundle;
import java.util.Objects;
import java.util.stream.Collectors;

import Sistema.DTO.ReportConfigDTO;
import Sistema.Logica.Exception.ReportExceptionLogica;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListView;
import javafx.scene.control.ListCell;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TableColumn;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Alert.AlertType;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.cell.PropertyValueFactory;

// paquetes necesarios para el servicio de IA
import Sistema.Service.IAService.ReservaExtraida;
import Sistema.Service.IAService.ExtractorIAReserva;

// paquete necesario para el uso de threads
import javafx.concurrent.Task;

public class ReservasViewController implements Initializable {

    private static final String MENSAJE_ERROR_RED = "No se pudo comunicar con el servidor. Intente de nuevo.";
    private static final String MENSAJE_SIN_RESERVAS = "El usuario no tiene reservas registradas";

    private final IReservaService reservaService;
    private final ICategoriaService categoriaService;
    private final PdfController pdfController = new PdfController();
    private long numeroCargaCategorias;
    private long numeroCargaReservas;
    private long numeroOperacion;
    private boolean cargaCategoriasEnCurso;
    private boolean cargaReservasEnCurso;
    private boolean operacionEnCurso;
    private boolean iaEnCurso;
    private ReservaDTO reservaSeleccionada;

    public ReservasViewController() {
        this(new ProxyReservaService(), new ProxyCategoriaService());
    }

    ReservasViewController(
            IReservaService reservaService,
            ICategoriaService categoriaService
    ) {
        this.reservaService = Objects.requireNonNull(reservaService);
        this.categoriaService = Objects.requireNonNull(categoriaService);
    }

    @FXML private TextField txtFrase;
    @FXML private Button btnExtraerAI;
    @FXML private TextField txtActividad;
    @FXML private DatePicker dpFecha;
    @FXML private ComboBox<LocalTime> cbHoraInicio;
    @FXML private ComboBox<LocalTime> cbHoraFin;
    @FXML private ListView<CategoriaDTO> lvCategorias;


    @FXML private Button btnReservar;
    @FXML private Button btnCancelarReserva;
    @FXML private Button btnLimpiar;
    @FXML private Button btnImprimirReservas;


    @FXML private TableView<ReservaDTO> tvMisReservas;
    @FXML private TableColumn<ReservaDTO, String> colId;
    @FXML private TableColumn<ReservaDTO, String> colActividad;
    @FXML private TableColumn<ReservaDTO, LocalDate> colFecha;
    @FXML private TableColumn<ReservaDTO, String> colHorario;
    @FXML private TableColumn<ReservaDTO, String> colRecursos;
    @FXML private TableColumn<ReservaDTO, String> colEstado;

    // lista observable que alimenta el TableView
    private ObservableList<ReservaDTO> listaReservas = FXCollections.observableArrayList();

    @FXML
    public void initialize(URL location, ResourceBundle resources){
        // 1. deshabilitamos inicialmente la acción de cancelar si no hay nada seleccionado
        btnCancelarReserva.setDisable(true);

        // 2. vinculamos columnas con atributos/getters de ReservaDTO
        colId.setCellValueFactory(new PropertyValueFactory<>("idReserva"));
        colActividad.setCellValueFactory(new PropertyValueFactory<>("nombreActividad"));
        colFecha.setCellValueFactory(new PropertyValueFactory<>("fecha"));

        colHorario.setCellValueFactory(cellData -> {
            ReservaDTO reserva = cellData.getValue();
            if (reserva != null && reserva.getHoraInicio() != null && reserva.getHoraFin() != null) {
                return new SimpleStringProperty(reserva.getHoraInicio() + " - " + reserva.getHoraFin());
            }
            return new SimpleStringProperty("");
        });

        colRecursos.setCellValueFactory(cellData -> {
            ReservaDTO reserva = cellData.getValue();
            if (reserva != null && reserva.getRecursosReservados() != null && !reserva.getRecursosReservados().isEmpty()) {
                String nombres = reserva.getRecursosReservados().stream()
                        .map(RecursoDTO::getId)
                        .collect(Collectors.joining(", "));
                return new SimpleStringProperty(nombres);
            }
            return new SimpleStringProperty("Sin recursos");
        });

        colEstado.setCellValueFactory(new PropertyValueFactory<>("estado"));

        tvMisReservas.setItems(listaReservas);

        // 3. evento de seleccion: al hacer clic en una fila, activa el boton de cancelar
        tvMisReservas.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            reservaSeleccionada = newValue;
            actualizarEstadoControles();
        });

        // 4. configuracion de controles y eventos a los botones
        configurarHorarios();
        configurarListViewCategorias();

        btnReservar.setOnAction(evento -> guardarReserva());
        btnCancelarReserva.setOnAction(evento -> cancelarReserva());
        btnLimpiar.setOnAction(evento -> {
            if (!operacionEnCurso && !iaEnCurso) limpiarFormulario();
        });
        btnExtraerAI.setOnAction(evento -> onAutocompletarIA());
        btnImprimirReservas.setOnAction(evento -> generarPdf());

        ContextoCarga contextoInicial = new ContextoCarga();
        cargarCategorias(contextoInicial);
        limpiarFormulario();
        cargarDatosDesdeSistema(contextoInicial);
    }

    private void configurarHorarios() {
        ObservableList<LocalTime> horasInicio = FXCollections.observableArrayList();
        ObservableList<LocalTime> horasFin = FXCollections.observableArrayList();

        LocalTime hora = HorarioSistema.INICIO_SERVICIO;
        while (hora.isBefore(HorarioSistema.FIN_SERVICIO)) {
            horasInicio.add(hora);
            hora = hora.plusMinutes(HorarioSistema.MINUTOS_POR_BLOQUE);
        }
        hora = HorarioSistema.INICIO_SERVICIO.plusMinutes(HorarioSistema.MINUTOS_POR_BLOQUE);
        while (!hora.isAfter(HorarioSistema.FIN_SERVICIO)) {
            horasFin.add(hora);
            hora = hora.plusMinutes(HorarioSistema.MINUTOS_POR_BLOQUE);
        }
        cbHoraInicio.setItems(horasInicio);
        cbHoraFin.setItems(horasFin);
    }

    private void configurarListViewCategorias() {
        lvCategorias.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        lvCategorias.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(CategoriaDTO item, boolean empty) {
                super.updateItem(item, empty);
                setText((empty || item == null) ? null : item.getDescripcion());
            }
        });
    }

    private void cargarCategorias(ContextoCarga contexto) {
        long cargaActual = ++numeroCargaCategorias;
        cargaCategoriasEnCurso = true;
        actualizarEstadoControles();
        TareaRed.ejecutar(
                () -> categoriaService.listarTodos(),
                categorias -> {
                    if (cargaActual != numeroCargaCategorias || !cargaCategoriasEnCurso) return;
                    cargaCategoriasEnCurso = false;
                    actualizarEstadoControles();
                    if (categorias == null) {
                        mostrarErrorCarga(
                                contexto,
                                "No se pudieron cargar las categorías",
                                new IllegalStateException("El servidor no devolvió una lista válida de categorías.")
                        );
                        return;
                    }
                    lvCategorias.setItems(FXCollections.observableArrayList(categorias));
                },
                error -> {
                    if (cargaActual != numeroCargaCategorias || !cargaCategoriasEnCurso) return;
                    cargaCategoriasEnCurso = false;
                    actualizarEstadoControles();
                    mostrarErrorCarga(
                            contexto,
                            "No se pudieron cargar las categorías",
                            error
                    );
                }
        );
    }

    // rellenamos la tabla desde el servicio
    public void cargarTabla(List<ReservaDTO> reservas) {
        if (reservas != null) {
            listaReservas.setAll(reservas);
        } else {
            listaReservas.clear();
        }
    }

    void cargarDatosDesdeSistema() {
        if (operacionEnCurso) return;
        cargarDatosDesdeSistema(new ContextoCarga());
    }

    private void cargarDatosDesdeSistema(ContextoCarga contexto) {
        long cargaActual = ++numeroCargaReservas;
        cargaReservasEnCurso = true;
        actualizarEstadoControles();
        String idUsuario = SessionManager.getInstancia().getIdUsuario();
        if (idUsuario == null || idUsuario.isBlank()) {
            cargaReservasEnCurso = false;
            listaReservas.clear();
            actualizarEstadoControles();
            return;
        }

        TareaRed.ejecutar(
                () -> reservaService.buscarPorFuncionario(idUsuario),
                reservas -> {
                    if (cargaActual != numeroCargaReservas || !cargaReservasEnCurso) return;
                    cargaReservasEnCurso = false;
                    actualizarEstadoControles();
                    if (reservas == null) {
                        mostrarErrorCarga(
                                contexto,
                                "No se pudieron cargar las reservas",
                                new IllegalStateException("El servidor no devolvió una lista válida de reservas.")
                        );
                        return;
                    }
                    cargarTabla(reservas);
                },
                error -> {
                    if (cargaActual != numeroCargaReservas || !cargaReservasEnCurso) return;
                    cargaReservasEnCurso = false;
                    actualizarEstadoControles();
                    if (esResultadoSinReservas(error)) {
                        cargarTabla(List.of());
                        return;
                    }
                    mostrarErrorCarga(
                            contexto,
                            "No se pudieron cargar las reservas",
                            error
                    );
                }
        );
    }

    private static boolean esResultadoSinReservas(Throwable error) {
        return error instanceof IllegalArgumentException
                && MENSAJE_SIN_RESERVAS.equals(error.getMessage());
    }

    private void mostrarErrorCarga(
            ContextoCarga contexto,
            String tituloServidor,
            Throwable error
    ) {
        if (contexto.errorMostrado) return;
        contexto.errorMostrado = true;

        if (error instanceof IllegalArgumentException) {
            String mensaje = error.getMessage();
            mostrarAlerta(
                    AlertType.ERROR,
                    tituloServidor,
                    mensaje == null || mensaje.isBlank()
                            ? "El servidor rechazó la consulta."
                            : mensaje
            );
            return;
        }

        mostrarAlerta(AlertType.ERROR, "Error de conexión", MENSAJE_ERROR_RED);
    }

    private static final class ContextoCarga {
        private boolean errorMostrado;
    }

    @FXML
    private void limpiarFormulario() {
        txtFrase.clear();
        txtActividad.clear();
        dpFecha.setValue(null);
        cbHoraInicio.setValue(null);
        cbHoraFin.setValue(null);
        lvCategorias.getSelectionModel().clearSelection();
        tvMisReservas.getSelectionModel().clearSelection();
        btnCancelarReserva.setDisable(true);
    }

    @FXML
    private void guardarReserva() {
        if (operacionEnCurso || iaEnCurso || cargaCategoriasEnCurso) return;

        String idUsuario = SessionManager.getInstancia().getIdUsuario();
        Funcionario funcionario = SessionManager.getInstancia().getFuncionarioAsociado();

        if (idUsuario == null || idUsuario.isBlank()) {
            mostrarAlerta(AlertType.WARNING, "Atención", "No se encontró una sesión de usuario activa");
            return;
        }

        String nombreFuncionario = (funcionario != null) ? funcionario.getNombre() : idUsuario;
        String actividad = txtActividad.getText() != null ? txtActividad.getText().trim() : "";
        LocalDate fecha = dpFecha.getValue();
        LocalTime horaInicio = cbHoraInicio.getValue();
        LocalTime horaFin = cbHoraFin.getValue();

        List<String> idsCategorias = lvCategorias.getSelectionModel().getSelectedItems().stream()
                .map(CategoriaDTO::getId)
                .collect(Collectors.toUnmodifiableList());

        long operacionActual = prepararOperacion();
        TareaRed.ejecutar(
                () -> reservaService.registrarReserva(
                        idUsuario,
                        nombreFuncionario,
                        actividad,
                        fecha,
                        horaInicio,
                        horaFin,
                        idsCategorias
                ),
                reserva -> completarRegistro(operacionActual, idUsuario, reserva),
                error -> mostrarErrorOperacion(
                        operacionActual,
                        error,
                        "Atención",
                        "Error"
                )
        );
    }

    @FXML
    private void cancelarReserva() {
        if (operacionEnCurso) return;

        ReservaDTO seleccionada = reservaSeleccionada;

        if (seleccionada == null) {
            mostrarAlerta(AlertType.WARNING, "Selección requerida", "Seleccione una reserva de la tabla para cancelar");
            return;
        }

        String idReserva = seleccionada.getIdReserva();
        String idUsuario = SessionManager.getInstancia().getIdUsuario();
        long operacionActual = prepararOperacion();
        TareaRed.ejecutar(
                () -> reservaService.cancelarReserva(idReserva),
                cancelada -> completarCancelacion(operacionActual, idUsuario, cancelada),
                error -> mostrarErrorOperacion(
                        operacionActual,
                        error,
                        "No se puede cancelar",
                        "Error al cancelar"
                )
        );
    }

    private long prepararOperacion() {
        long operacionActual = ++numeroOperacion;
        numeroCargaReservas++;
        cargaReservasEnCurso = false;
        operacionEnCurso = true;
        actualizarEstadoControles();
        return operacionActual;
    }

    private boolean esOperacionVigente(long operacion) {
        return operacion == numeroOperacion && operacionEnCurso;
    }

    private void completarRegistro(
            long operacion,
            String idUsuario,
            ReservaDTO reserva
    ) {
        if (!esOperacionVigente(operacion)) return;
        if (reserva == null) {
            finalizarOperacion();
            mostrarAlerta(
                    AlertType.ERROR,
                    "Error",
                    "El servidor no devolvió la reserva registrada."
            );
            return;
        }

        mostrarAlerta(AlertType.INFORMATION, "Éxito", "La reserva fue registrada correctamente");
        limpiarFormulario();
        actualizarEstadoControles();
        recargarDespuesOperacion(operacion, idUsuario);
    }

    private void completarCancelacion(
            long operacion,
            String idUsuario,
            Boolean cancelada
    ) {
        if (!esOperacionVigente(operacion)) return;
        if (!Boolean.TRUE.equals(cancelada)) {
            finalizarOperacion();
            mostrarAlerta(
                    AlertType.WARNING,
                    "No se puede cancelar",
                    "El servidor no confirmó la cancelación de la reserva."
            );
            return;
        }

        mostrarAlerta(AlertType.INFORMATION, "Éxito", "Reserva cancelada correctamente");
        limpiarFormulario();
        actualizarEstadoControles();
        recargarDespuesOperacion(operacion, idUsuario);
    }

    private void recargarDespuesOperacion(long operacion, String idUsuario) {
        TareaRed.ejecutar(
                () -> reservaService.buscarPorFuncionario(idUsuario),
                reservas -> {
                    if (!esOperacionVigente(operacion)) return;
                    if (reservas == null) {
                        finalizarOperacion();
                        mostrarErrorCarga(
                                new ContextoCarga(),
                                "No se pudieron cargar las reservas",
                                new IllegalStateException("El servidor no devolvió una lista válida de reservas.")
                        );
                        return;
                    }
                    cargarTabla(reservas);
                    finalizarOperacion();
                },
                error -> {
                    if (!esOperacionVigente(operacion)) return;
                    if (esResultadoSinReservas(error)) {
                        cargarTabla(List.of());
                        finalizarOperacion();
                        return;
                    }
                    finalizarOperacion();
                    mostrarErrorCarga(
                            new ContextoCarga(),
                            "No se pudieron cargar las reservas",
                            error
                    );
                }
        );
    }

    private void mostrarErrorOperacion(
            long operacion,
            Throwable error,
            String tituloValidacion,
            String tituloError
    ) {
        if (!esOperacionVigente(operacion)) return;
        finalizarOperacion();
        if (error instanceof IllegalArgumentException) {
            mostrarAlerta(AlertType.WARNING, tituloValidacion, error.getMessage());
        } else if (error instanceof IllegalStateException) {
            mostrarAlerta(AlertType.ERROR, tituloError, MENSAJE_ERROR_RED);
        } else {
            mostrarAlerta(
                    AlertType.ERROR,
                    tituloError,
                    "Ocurrió un error no esperado: " + error.getMessage()
            );
        }
    }

    private void finalizarOperacion() {
        operacionEnCurso = false;
        actualizarEstadoControles();
    }

    private void actualizarEstadoControles() {
        boolean bloquearFormulario = operacionEnCurso || iaEnCurso;
        txtFrase.setDisable(operacionEnCurso);
        btnExtraerAI.setDisable(operacionEnCurso || iaEnCurso || cargaCategoriasEnCurso);
        txtActividad.setDisable(bloquearFormulario);
        dpFecha.setDisable(bloquearFormulario);
        cbHoraInicio.setDisable(bloquearFormulario);
        cbHoraFin.setDisable(bloquearFormulario);
        lvCategorias.setDisable(bloquearFormulario || cargaCategoriasEnCurso);
        btnReservar.setDisable(bloquearFormulario || cargaCategoriasEnCurso);
        btnLimpiar.setDisable(bloquearFormulario);
        tvMisReservas.setDisable(operacionEnCurso || cargaReservasEnCurso);
        btnCancelarReserva.setDisable(
                operacionEnCurso || cargaReservasEnCurso || reservaSeleccionada == null
        );
        btnImprimirReservas.setDisable(operacionEnCurso || cargaReservasEnCurso);
    }

    // metodo auxiliar para construir alertas estándar
    void mostrarAlerta(AlertType tipo, String titulo, String mensaje) {
        Alert alert = new Alert(tipo);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        alert.showAndWait();
    }

    // metodos necesarios para el autocompletado con IA
    private void onAutocompletarIA() {
        if (operacionEnCurso || iaEnCurso || cargaCategoriasEnCurso) return;

        String frase = txtFrase.getText();

        if (frase == null || frase.isBlank()) {
            mostrarAlerta(AlertType.WARNING, "Atención", "Escriba una frase describiendo la reserva antes de usar la IA.");
            return;
        }

        List<String> categoriasDisponibles = lvCategorias.getItems().stream()
                .map(CategoriaDTO::getDescripcion)
                .collect(Collectors.toList());

        Task<ReservaExtraida> tarea = new Task<>() {
            @Override
            protected ReservaExtraida call() throws Exception {
                return ExtractorIAReserva.extraer(frase, categoriasDisponibles);
            }
        };

        tarea.setOnSucceeded(event -> {
            llenarFormularioConDatosIA(tarea.getValue());
            iaEnCurso = false;
            actualizarEstadoControles();
        });

        tarea.setOnFailed(event -> {
            iaEnCurso = false;
            actualizarEstadoControles();
            mostrarAlerta(AlertType.ERROR, "Error de IA",
                    "No se pudo interpretar la frase: " + tarea.getException().getMessage());
        });

        iaEnCurso = true;
        actualizarEstadoControles();
        Thread hilo = new Thread(tarea);
        hilo.setDaemon(true);
        hilo.start();
    }

    private void llenarFormularioConDatosIA(ReservaExtraida datos) {
        if (datos.getActividad() != null && !datos.getActividad().isBlank()) txtActividad.setText(datos.getActividad());
        if (datos.getFecha() != null) dpFecha.setValue(datos.getFecha());
        if (datos.getHoraInicio() != null) cbHoraInicio.setValue(horaMasCercana(cbHoraInicio.getItems(), datos.getHoraInicio()));
        if (datos.getHoraFin() != null) cbHoraFin.setValue(horaMasCercana(cbHoraFin.getItems(), datos.getHoraFin()));

        lvCategorias.getSelectionModel().clearSelection();
        for (CategoriaDTO categoria : lvCategorias.getItems()) {
            boolean sugerida = datos.getCategoriasSugeridas().stream()
                    .anyMatch(txt -> txt.trim().equalsIgnoreCase(categoria.getDescripcion()));
            if (sugerida) lvCategorias.getSelectionModel().select(categoria);
        }
    }

    private LocalTime horaMasCercana(List<LocalTime> opciones, LocalTime horaIA) {
        return opciones.stream()
                .min((a, b) -> {
                    long difA = Math.abs(java.time.Duration.between(a, horaIA).toMinutes());
                    long difB = Math.abs(java.time.Duration.between(b, horaIA).toMinutes());
                    return Long.compare(difA, difB);
                })
                .orElse(null);
    }

    // metodos necesarios para generar el PDF
    private boolean validarDatosParaPdf() {
        if (tvMisReservas.getItems().isEmpty()) {
            mostrarAlerta(AlertType.WARNING, "Atención", "No hay reservas registradas en la tabla para exportar.");
            return false;
        }
        return true;
    }

    private ReportConfigDTO crearConfiguracionPdf() {
        ReportConfigDTO configuracion = new ReportConfigDTO(
                "Reporte de Reservas de Recursos",
                "Mis Reservas Registradas",
                "Reporte_Mis_Reservas.pdf"
        );

        configuracion.setTituloVentana("Guardar reporte de mis reservas");
        configuracion.setHorizontal(true); // Se recomienda horizontal por la cantidad de columnas (Id, Actividad, Fecha, Horario, Recursos, Estado)

        String idUsuario = SessionManager.getInstancia().getIdUsuario();
        configuracion.agregarFiltro("Usuario", (idUsuario != null && !idUsuario.isBlank()) ? idUsuario : "No identificado");

        return configuracion;
    }

    private void generarPdf() {
        if (!validarDatosParaPdf()) return;

        try {
            boolean generado = pdfController.generar(
                    crearConfiguracionPdf(),
                    btnImprimirReservas.getScene().getWindow(),
                    tvMisReservas
            );

            if (generado) mostrarAlerta(AlertType.INFORMATION, "Éxito", "El reporte PDF de reservas fue generado correctamente.");

        } catch (ReportExceptionLogica excepcion) {
            mostrarAlerta(AlertType.ERROR, "Error al generar PDF", excepcion.getMessage());
            excepcion.printStackTrace();
        }
    }
}
