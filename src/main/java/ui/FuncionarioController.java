package ui;

import Sistema.DTO.Funcionario;
import Sistema.Logica.FuncionarioLogica;
import Sistema.Service.FuncionarioService;
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
import javafx.scene.paint.Color;

import java.util.List;
public class FuncionarioController {

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

    private final FuncionarioService funcionarioService = new FuncionarioService();
    private final PdfController pdfController = new PdfController();

    // Funcionario actualmente seleccionado en el listado.
    // null = el formulario esta en modo "nuevo funcionario".
    private Funcionario funcionarioSeleccionado;

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

        cargarListadoCompleto();
    }

    private void cargarListadoCompleto() {
        try {
            List<Funcionario> funcionarios = funcionarioService.listarTodos();
            tablaListado.setItems(FXCollections.observableArrayList(funcionarios));
        } catch (RuntimeException e) {
            mostrarErrorSistema("No se pudo cargar el listado de funcionarios.\n" + e.getMessage());
        }
    }

    private void buscar() {
        try {
            String idParcial = campoBusquedaId.getText();
            String nombreParcial = campoBusquedaNombre.getText();
            List<Funcionario> resultado = funcionarioService.buscar(idParcial, nombreParcial);
            tablaListado.setItems(FXCollections.observableArrayList(resultado));
            limpiarMensaje();
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
            FuncionarioLogica.ResultadoOperacion resultado = esNuevo
                    ? funcionarioService.registrar(funcionario)
                    : funcionarioService.modificar(funcionario);

            if (resultado.isExitoso()) {
                mostrarMensajeExito(esNuevo ? "Funcionario registrado correctamente."
                        : "Funcionario modificado correctamente.");
                limpiar();
                cargarListadoCompleto();
            } else {
                mostrarErrorValidacion(resultado.getMensajeError());
            }
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
                FuncionarioLogica.ResultadoOperacion resultado =
                        funcionarioService.eliminar(funcionarioSeleccionado.getId());

                if (resultado.isExitoso()) {
                    mostrarMensajeExito("Funcionario eliminado correctamente.");
                    limpiar();
                    cargarListadoCompleto();
                } else {
                    mostrarErrorValidacion(resultado.getMensajeError());
                }
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
        Alert alerta = new Alert(Alert.AlertType.ERROR, mensaje);
        alerta.setTitle("Error inesperado");
        alerta.setHeaderText(null);
        alerta.showAndWait();
    }
}
