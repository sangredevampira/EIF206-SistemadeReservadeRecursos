package ui;

import Sistema.DTO.Funcionario;
import Sistema.DTO.ResultadoOperacionDTO;
import Sistema.Service.IFuncionarioService;
import Sistema.Service.Red.ProxyFuncionarioService;
import Sistema.Red.TareaRed;
import Sistema.DTO.ReportConfigDTO;
import Sistema.Logica.Exception.ReportExceptionLogica;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;

// Conecta la pantalla de funcionarios con el servidor por medio de ProxyFuncionarioService (FUNC_*).
public class FuncionarioController {

    static final String MENSAJE_SIN_CONEXION_CARGAR =
            "No hay comunicación con el servidor; no se pudo cargar el listado de funcionarios. "
                    + "Intente de nuevo y, si sigue, reinicie la aplicación.";
    static final String MENSAJE_SIN_CONEXION_BUSCAR =
            "No hay comunicación con el servidor; no se pudo realizar la búsqueda. "
                    + "Intente de nuevo y, si sigue, reinicie la aplicación.";
    static final String MENSAJE_SIN_CONEXION_GUARDAR =
            "No hay comunicación con el servidor; no se pudo confirmar si se guardó el funcionario. "
                    + "Intente de nuevo y, si sigue, reinicie la aplicación.";
    static final String MENSAJE_SIN_CONEXION_BORRAR =
            "No hay comunicación con el servidor; no se pudo confirmar si se borró el funcionario. "
                    + "Intente de nuevo y, si sigue, reinicie la aplicación.";

    @FXML private TextField campoBusquedaId;
    @FXML private TextField campoBusquedaNombre;
    @FXML private Button btnBuscar;
    @FXML private Button btnImprimir;

    @FXML private TextField campoId;
    @FXML private TextField campoNombre;
    @FXML private TextField campoTelefono;
    @FXML private Button btnGuardar;
    @FXML private Button btnBorrar;
    @FXML private Button btnLimpiar;

    @FXML private TableView<Funcionario> tablaListado;
    @FXML private TableColumn<Funcionario, String> colId;
    @FXML private TableColumn<Funcionario, String> colNombre;
    @FXML private TableColumn<Funcionario, String> colTelefono;

    @FXML private Label lblMensaje;

    private final IFuncionarioService funcionarioService;
    private final PdfController pdfController = new PdfController();

    // Funcionario actualmente seleccionado en el listado.
    // null = el formulario esta en modo "nuevo funcionario".
    private Funcionario funcionarioSeleccionado;
    private long numeroConsulta;
    private long numeroOperacion;
    private boolean consultaEnCurso;
    private boolean operacionEnCurso;

    // Lo usa MainController (loader.setController(new FuncionarioController()))
    public FuncionarioController() {
        this(new ProxyFuncionarioService());
    }

    // Para las pruebas: permite usar un IFuncionarioService falso sin red
    FuncionarioController(IFuncionarioService funcionarioService) {
        this.funcionarioService = Objects.requireNonNull(funcionarioService);
    }

    @FXML
    private void initialize() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colNombre.setCellValueFactory(new PropertyValueFactory<>("nombre"));
        colTelefono.setCellValueFactory(new PropertyValueFactory<>("telefono"));

        tablaListado.getSelectionModel().selectedItemProperty()
                .addListener((observable, anterior, nuevo) -> cargarEnFormulario(nuevo));

        btnBuscar.setOnAction(evento -> buscar());
        btnGuardar.setOnAction(evento -> guardar());
        btnBorrar.setOnAction(evento -> borrar());
        btnLimpiar.setOnAction(evento -> limpiar());
        btnImprimir.setOnAction(evento -> imprimir());
        campoBusquedaId.textProperty().addListener((observable, anterior, actual) ->
                invalidarConsultaPorCambioFiltros());
        campoBusquedaNombre.textProperty().addListener((observable, anterior, actual) ->
                invalidarConsultaPorCambioFiltros());

        iniciarConsulta(
                funcionarioService::listarTodos,
                MENSAJE_SIN_CONEXION_CARGAR,
                "No se pudo cargar el listado de funcionarios.",
                "Cargando funcionarios..."
        );
    }

    private void iniciarConsulta(
            Callable<List<Funcionario>> consulta,
            String mensajeConexion,
            String mensajeSistema,
            String mensajeProcesando
    ) {
        long consultaActual = ++numeroConsulta;
        consultaEnCurso = true;
        actualizarEstadoControles();
        lblMensaje.setTextFill(Color.web("#495057"));
        lblMensaje.setText(mensajeProcesando);

        TareaRed.ejecutar(
                consulta,
                funcionarios -> completarConsulta(
                        consultaActual,
                        funcionarios
                ),
                error -> mostrarErrorConsulta(
                        consultaActual,
                        error,
                        mensajeConexion,
                        mensajeSistema
                )
        );
    }

    private void completarConsulta(
            long consulta,
            List<Funcionario> funcionarios
    ) {
        if (consulta != numeroConsulta || !consultaEnCurso) return;

        consultaEnCurso = false;
        actualizarEstadoControles();
        if (funcionarios == null) {
            mostrarErrorSistema("El servidor no devolvió un listado válido de funcionarios.");
            return;
        }

        mostrarListado(funcionarios);
        limpiarMensaje();
    }

    private void mostrarErrorConsulta(
            long consulta,
            Throwable error,
            String mensajeConexion,
            String mensajeSistema
    ) {
        if (consulta != numeroConsulta || !consultaEnCurso) return;

        consultaEnCurso = false;
        actualizarEstadoControles();
        if (error instanceof IllegalArgumentException) {
            mostrarErrorValidacion(error.getMessage());
        } else if (error instanceof IllegalStateException) {
            mostrarErrorConexion(mensajeConexion);
        } else {
            mostrarErrorSistema(mensajeSistema + "\n" + error.getMessage());
        }
    }

    private void mostrarListado(List<Funcionario> funcionarios) {
        tablaListado.setItems(FXCollections.observableArrayList(funcionarios));
    }

    private void mostrarErrorCarga(Throwable error) {
        if (error instanceof IllegalArgumentException) {
            mostrarErrorValidacion(error.getMessage());
        } else if (error instanceof IllegalStateException) {
            mostrarErrorConexion(MENSAJE_SIN_CONEXION_CARGAR);
        } else {
            mostrarErrorSistema("No se pudo cargar el listado de funcionarios.\n" + error.getMessage());
        }
    }

    private void buscar() {
        if (operacionEnCurso) return;

        String idParcial = campoBusquedaId.getText();
        String nombreParcial = campoBusquedaNombre.getText();
        iniciarConsulta(
                () -> funcionarioService.buscar(idParcial, nombreParcial),
                MENSAJE_SIN_CONEXION_BUSCAR,
                "No se pudo realizar la busqueda.",
                "Buscando funcionarios..."
        );
    }

    private void cargarEnFormulario(Funcionario funcionario) {
        funcionarioSeleccionado = funcionario;
        limpiarMensaje();

        if (funcionario == null) {
            return;
        }
        campoId.setText(funcionario.getId());
        campoId.setDisable(true); // el ID no se modifica una vez creado el funcionario
        campoNombre.setText(funcionario.getNombre());
        campoTelefono.setText(funcionario.getTelefono());
    }

    private void guardar() {
        if (operacionEnCurso) return;

        Funcionario funcionario = new Funcionario(
                campoId.getText(), campoNombre.getText(), campoTelefono.getText());

        boolean esNuevo = (funcionarioSeleccionado == null);
        iniciarOperacion(
                () -> esNuevo
                        ? funcionarioService.registrar(funcionario)
                        : funcionarioService.modificar(funcionario),
                esNuevo ? "Registrando funcionario..." : "Modificando funcionario...",
                esNuevo ? "Funcionario registrado correctamente."
                        : "Funcionario modificado correctamente.",
                MENSAJE_SIN_CONEXION_GUARDAR,
                "No se pudo guardar el funcionario."
        );
    }

    private void borrar() {
        if (operacionEnCurso) return;

        if (funcionarioSeleccionado == null) {
            mostrarErrorValidacion("Seleccione un funcionario del listado para borrar.");
            return;
        }

        Funcionario funcionarioABorrar = funcionarioSeleccionado;
        if (!confirmarBorrado(funcionarioABorrar)) return;

        iniciarOperacion(
                () -> funcionarioService.eliminar(funcionarioABorrar.getId()),
                "Eliminando funcionario...",
                "Funcionario eliminado correctamente.",
                MENSAJE_SIN_CONEXION_BORRAR,
                "No se pudo borrar el funcionario."
        );
    }

    boolean confirmarBorrado(Funcionario funcionario) {
        Alert confirmacion = new Alert(
                Alert.AlertType.CONFIRMATION,
                "¿Está seguro de que desea borrar al funcionario \""
                        + funcionario.getNombre() + "\" (ID " + funcionario.getId() + ")?",
                ButtonType.YES, ButtonType.NO);
        confirmacion.setTitle("Confirmar borrado");
        confirmacion.setHeaderText(null);
        return confirmacion.showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
    }

    private void iniciarOperacion(
            Callable<ResultadoOperacionDTO> operacion,
            String mensajeProcesando,
            String mensajeExito,
            String mensajeConexion,
            String mensajeSistema
    ) {
        long operacionActual = ++numeroOperacion;
        numeroConsulta++;
        consultaEnCurso = false;
        operacionEnCurso = true;
        actualizarEstadoControles();
        lblMensaje.setTextFill(Color.web("#495057"));
        lblMensaje.setText(mensajeProcesando);

        TareaRed.ejecutar(
                operacion,
                resultado -> completarOperacion(
                        operacionActual,
                        resultado,
                        mensajeExito
                ),
                error -> mostrarErrorOperacion(
                        operacionActual,
                        error,
                        mensajeConexion,
                        mensajeSistema
                )
        );
    }

    private void completarOperacion(
            long operacion,
            ResultadoOperacionDTO resultado,
            String mensajeExito
    ) {
        if (operacion != numeroOperacion || !operacionEnCurso) return;

        if (resultado == null) {
            finalizarOperacion();
            mostrarErrorSistema("El servidor no devolvió un resultado válido.");
            return;
        }
        if (!resultado.isExito()) {
            finalizarOperacion();
            mostrarErrorValidacion(resultado.getMensaje());
            return;
        }

        limpiar();
        mostrarMensajeExito(mensajeExito);
        actualizarEstadoControles();
        recargarDespuesOperacion(operacion);
    }

    private void recargarDespuesOperacion(long operacion) {
        TareaRed.ejecutar(
                funcionarioService::listarTodos,
                funcionarios -> {
                    if (operacion != numeroOperacion || !operacionEnCurso) return;
                    if (funcionarios == null) {
                        finalizarOperacion();
                        mostrarErrorSistema("El servidor no devolvió un listado válido de funcionarios.");
                        return;
                    }
                    mostrarListado(funcionarios);
                    finalizarOperacion();
                },
                error -> {
                    if (operacion != numeroOperacion || !operacionEnCurso) return;
                    finalizarOperacion();
                    mostrarErrorCarga(error);
                }
        );
    }

    private void mostrarErrorOperacion(
            long operacion,
            Throwable error,
            String mensajeConexion,
            String mensajeSistema
    ) {
        if (operacion != numeroOperacion || !operacionEnCurso) return;

        finalizarOperacion();
        if (error instanceof IllegalArgumentException) {
            mostrarErrorValidacion(error.getMessage());
        } else if (error instanceof IllegalStateException) {
            mostrarErrorConexion(mensajeConexion);
        } else {
            mostrarErrorSistema(mensajeSistema + "\n" + error.getMessage());
        }
    }

    private void finalizarOperacion() {
        operacionEnCurso = false;
        actualizarEstadoControles();
    }

    private void invalidarConsultaPorCambioFiltros() {
        numeroConsulta++;
        if (!consultaEnCurso || operacionEnCurso) return;

        consultaEnCurso = false;
        actualizarEstadoControles();
        mostrarErrorValidacion("Los filtros cambiaron. Presione Buscar nuevamente.");
    }

    private void actualizarEstadoControles() {
        boolean bloquearPorOperacion = operacionEnCurso;
        campoBusquedaId.setDisable(bloquearPorOperacion);
        campoBusquedaNombre.setDisable(bloquearPorOperacion);
        btnBuscar.setDisable(bloquearPorOperacion);
        campoId.setDisable(bloquearPorOperacion || funcionarioSeleccionado != null);
        campoNombre.setDisable(bloquearPorOperacion);
        campoTelefono.setDisable(bloquearPorOperacion);
        btnGuardar.setDisable(bloquearPorOperacion);
        btnBorrar.setDisable(bloquearPorOperacion);
        btnLimpiar.setDisable(bloquearPorOperacion);
        tablaListado.setDisable(bloquearPorOperacion || consultaEnCurso);
        btnImprimir.setDisable(bloquearPorOperacion || consultaEnCurso);
    }

    private void limpiar() {
        funcionarioSeleccionado = null;
        tablaListado.getSelectionModel().clearSelection();
        campoId.clear();
        campoId.setDisable(false);
        campoNombre.clear();
        campoTelefono.clear();
        limpiarMensaje();
    }

    private void imprimir() {
        if (tablaListado.getItems().isEmpty()) {
            mostrarErrorValidacion("No hay funcionarios para incluir en el reporte.");
            return;
        }

        ReportConfigDTO configuracion = new ReportConfigDTO(
                "Listado de funcionarios", "Funcionarios", "funcionarios");
        if (!campoBusquedaId.getText().isBlank()) {
            configuracion.agregarFiltro("ID", campoBusquedaId.getText());
        }
        if (!campoBusquedaNombre.getText().isBlank()) {
            configuracion.agregarFiltro("Nombre", campoBusquedaNombre.getText());
        }

        try {
            pdfController.generar(configuracion, tablaListado.getScene().getWindow(), tablaListado);
        } catch (ReportExceptionLogica e) {
            mostrarErrorSistema("No se pudo generar el reporte PDF.\n" + e.getMessage());
        }
    }

    private void mostrarErrorValidacion(String mensaje) {
        lblMensaje.setTextFill(Color.web("#C0392B"));
        lblMensaje.setText(mensaje);
    }

    private void mostrarMensajeExito(String mensaje) {
        lblMensaje.setTextFill(Color.web("#1E7E34"));
        lblMensaje.setText(mensaje);
    }

    private void limpiarMensaje() {
        lblMensaje.setText("");
    }

    // Incidentes inesperados de la capa Datos (RuntimeException, ver
    // HANDOFF seccion 4): se muestran en un Alert, no solo en la etiqueta,
    // porque representan una falla real de lectura/escritura del archivo.
    private void mostrarErrorSistema(String mensaje) {
        mostrarAlertaError("Error inesperado", mensaje);
    }

    // Para los MENSAJE_SIN_CONEXION_* (IllegalStateException de la red)
    private void mostrarErrorConexion(String mensaje) {
        mostrarAlertaError("Error de conexión", mensaje);
    }

    void mostrarAlertaError(String titulo, String mensaje) {
        Alert alerta = new Alert(Alert.AlertType.ERROR, mensaje);
        alerta.setTitle(titulo);
        alerta.setHeaderText(null);
        // sin esto el dialogo no crece con el texto y un mensaje largo se corta con "..."
        alerta.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        alerta.showAndWait();
    }
}
