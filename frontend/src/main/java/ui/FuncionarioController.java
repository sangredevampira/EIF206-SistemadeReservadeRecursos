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

// Conecta la pantalla de funcionarios con el servidor por medio de ProxyFuncionarioService (FUNC_*).
// La carga inicial va en TareaRed; buscar, guardar y borrar son directos (decision 13 del equipo).
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

        // carga inicial en segundo plano; los callbacks vuelven al hilo de JavaFX
        TareaRed.ejecutar(funcionarioService::listarTodos, this::mostrarListado, this::mostrarErrorCarga);
    }

    // Recarga directa, despues de guardar o borrar
    private void cargarListadoCompleto() {
        try {
            mostrarListado(funcionarioService.listarTodos());
        } catch (RuntimeException e) {
            mostrarErrorCarga(e);
        }
    }

    private void mostrarListado(List<Funcionario> funcionarios) {
        tablaListado.setItems(FXCollections.observableArrayList(funcionarios));
    }

    // Recibe un Throwable porque TareaRed entrega asi el error de la carga inicial
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
        try {
            String idParcial = campoBusquedaId.getText();
            String nombreParcial = campoBusquedaNombre.getText();
            List<Funcionario> resultado = funcionarioService.buscar(idParcial, nombreParcial);
            tablaListado.setItems(FXCollections.observableArrayList(resultado));
            limpiarMensaje();
        } catch (IllegalArgumentException e) {
            // el servidor respondio exito=false (por ejemplo, "No tiene permiso para esta operación.")
            mostrarErrorValidacion(e.getMessage());
        } catch (IllegalStateException e) {
            mostrarErrorConexion(MENSAJE_SIN_CONEXION_BUSCAR);
        } catch (RuntimeException e) {
            mostrarErrorSistema("No se pudo realizar la busqueda.\n" + e.getMessage());
        }
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
        Funcionario funcionario = new Funcionario(
                campoId.getText(), campoNombre.getText(), campoTelefono.getText());

        boolean esNuevo = (funcionarioSeleccionado == null);

        try {
            ResultadoOperacionDTO resultado = esNuevo
                    ? funcionarioService.registrar(funcionario)
                    : funcionarioService.modificar(funcionario);

            if (resultado.isExito()) {
                limpiar();
                cargarListadoCompleto();
                mostrarMensajeExito(esNuevo ? "Funcionario registrado correctamente."
                        : "Funcionario modificado correctamente.");
            } else {
                mostrarErrorValidacion(resultado.getMensaje());
            }
        } catch (IllegalArgumentException e) {
            mostrarErrorValidacion(e.getMessage());
        } catch (IllegalStateException e) {
            mostrarErrorConexion(MENSAJE_SIN_CONEXION_GUARDAR);
        } catch (RuntimeException e) {
            mostrarErrorSistema("No se pudo guardar el funcionario.\n" + e.getMessage());
        }
    }

    private void borrar() {
        if (funcionarioSeleccionado == null) {
            mostrarErrorValidacion("Seleccione un funcionario del listado para borrar.");
            return;
        }

        Alert confirmacion = new Alert(
                Alert.AlertType.CONFIRMATION,
                "¿Está seguro de que desea borrar al funcionario \""
                        + funcionarioSeleccionado.getNombre() + "\" (ID " + funcionarioSeleccionado.getId() + ")?",
                ButtonType.YES, ButtonType.NO);
        confirmacion.setTitle("Confirmar borrado");
        confirmacion.setHeaderText(null);

        confirmacion.showAndWait().ifPresent(respuesta -> {
            if (respuesta != ButtonType.YES) {
                return;
            }
            try {
                ResultadoOperacionDTO resultado =
                        funcionarioService.eliminar(funcionarioSeleccionado.getId());

                if (resultado.isExito()) {
                    limpiar();
                    cargarListadoCompleto();
                    mostrarMensajeExito("Funcionario eliminado correctamente.");
                } else {
                    mostrarErrorValidacion(resultado.getMensaje());
                }
            } catch (IllegalArgumentException e) {
                mostrarErrorValidacion(e.getMessage());
            } catch (IllegalStateException e) {
                mostrarErrorConexion(MENSAJE_SIN_CONEXION_BORRAR);
            } catch (RuntimeException e) {
                mostrarErrorSistema("No se pudo borrar el funcionario.\n" + e.getMessage());
            }
        });
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

    private void mostrarAlertaError(String titulo, String mensaje) {
        Alert alerta = new Alert(Alert.AlertType.ERROR, mensaje);
        alerta.setTitle(titulo);
        alerta.setHeaderText(null);
        // sin esto el dialogo no crece con el texto y un mensaje largo se corta con "..."
        alerta.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        alerta.showAndWait();
    }
}
