package ui;

import Sistema.DTO.CategoriaDTO;
import Sistema.Logica.CategoriaLogica;
import Sistema.Service.CategoriaService;

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

// A diferencia de FuncionarioController (que se conecta por codigo desde
// MainController), este usa fx:controller directo en CategoriasView.fxml
// + <fx:include> desde MainView.fxml -- el patron que se acordo usar de
// aca en adelante para toda vista nueva embebida en un tab (igual que
// Recursos, de Kris).
public class CategoriaController {

    @FXML private TextField campoBusquedaDescripcion;
    @FXML private Button btnBuscar;
    @FXML private Button btnImprimir;

    @FXML private TextField campoId;
    @FXML private TextField campoDescripcion;
    @FXML private Button btnGuardar;
    @FXML private Button btnBorrar;
    @FXML private Button btnLimpiar;

    @FXML private TableView<CategoriaDTO> tablaListado;
    @FXML private TableColumn<CategoriaDTO, String> colId;
    @FXML private TableColumn<CategoriaDTO, String> colDescripcion;

    @FXML private Label lblMensaje;

    private final CategoriaService categoriaService = new CategoriaService();

    private CategoriaDTO categoriaSeleccionada;

    @FXML
    private void initialize() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colDescripcion.setCellValueFactory(new PropertyValueFactory<>("descripcion"));

        tablaListado.getSelectionModel().selectedItemProperty()
                .addListener((observable, anterior, nueva) -> cargarEnFormulario(nueva));

        btnBuscar.setOnAction(evento -> buscar());
        btnGuardar.setOnAction(evento -> guardar());
        btnBorrar.setOnAction(evento -> borrar());
        btnLimpiar.setOnAction(evento -> limpiar());
        btnImprimir.setOnAction(evento -> imprimir());

        campoId.setDisable(true); // el ID siempre es autogenerado, nunca editable

        cargarListadoCompleto();
    }

    private void cargarListadoCompleto() {
        try {
            List<CategoriaDTO> categorias = categoriaService.listarTodos();
            tablaListado.setItems(FXCollections.observableArrayList(categorias));
        } catch (RuntimeException e) {
            mostrarErrorSistema("No se pudo cargar el listado de categorias.\n" + e.getMessage());
        }
    }

    private void buscar() {
        try {
            List<CategoriaDTO> resultado = categoriaService.buscar(campoBusquedaDescripcion.getText());
            tablaListado.setItems(FXCollections.observableArrayList(resultado));
            limpiarMensaje();
        } catch (RuntimeException e) {
            mostrarErrorSistema("No se pudo realizar la busqueda.\n" + e.getMessage());
        }
    }

    private void cargarEnFormulario(CategoriaDTO categoria) {
        categoriaSeleccionada = categoria;
        limpiarMensaje();

        if (categoria == null) {
            return;
        }
        campoId.setText(categoria.getId());
        campoDescripcion.setText(categoria.getDescripcion());
    }

    private void guardar() {
        boolean esNueva = (categoriaSeleccionada == null);

        CategoriaDTO categoria = esNueva
                ? new CategoriaDTO(null, campoDescripcion.getText())
                : new CategoriaDTO(categoriaSeleccionada.getId(), campoDescripcion.getText());

        try {
            CategoriaLogica.ResultadoOperacion resultado = esNueva
                    ? categoriaService.registrar(categoria)
                    : categoriaService.modificar(categoria);

            if (resultado.isExitoso()) {
                mostrarMensajeExito(esNueva ? "Categoria registrada correctamente."
                        : "Categoria modificada correctamente.");
                limpiar();
                cargarListadoCompleto();
            } else {
                mostrarErrorValidacion(resultado.getMensajeError());
            }
        } catch (RuntimeException e) {
            mostrarErrorSistema("No se pudo guardar la categoria.\n" + e.getMessage());
        }
    }

    private void borrar() {
        if (categoriaSeleccionada == null) {
            mostrarErrorValidacion("Seleccione una categoria del listado para borrar.");
            return;
        }

        Alert confirmacion = new Alert(
                Alert.AlertType.CONFIRMATION,
                "¿Está seguro de que desea borrar la categoria \""
                        + categoriaSeleccionada.getDescripcion() + "\"?",
                ButtonType.YES, ButtonType.NO);
        confirmacion.setTitle("Confirmar borrado");
        confirmacion.setHeaderText(null);

        confirmacion.showAndWait().ifPresent(respuesta -> {
            if (respuesta != ButtonType.YES) {
                return;
            }
            try {
                CategoriaLogica.ResultadoOperacion resultado =
                        categoriaService.eliminar(categoriaSeleccionada.getId());

                if (resultado.isExitoso()) {
                    mostrarMensajeExito("Categoria eliminada correctamente.");
                    limpiar();
                    cargarListadoCompleto();
                } else {
                    // Aca cae, por ejemplo, "tiene recursos asociados" --
                    // error de regla de negocio, no de sistema.
                    mostrarErrorValidacion(resultado.getMensajeError());
                }
            } catch (RuntimeException e) {
                mostrarErrorSistema("No se pudo borrar la categoria.\n" + e.getMessage());
            }
        });
    }

    private void limpiar() {
        categoriaSeleccionada = null;
        tablaListado.getSelectionModel().clearSelection();
        campoId.clear();
        campoDescripcion.clear();
        limpiarMensaje();
    }

    private void imprimir() {
        // Generacion de reporte PDF: pendiente, igual que en Funcionarios
        // y Recursos -- ControladorPdf/OpenPDF todavia no se implementa.
        Alert aviso = new Alert(Alert.AlertType.INFORMATION,
                "La generación de reporte en PDF para Categorías está pendiente de implementar.");
        aviso.setTitle("Reporte PDF");
        aviso.setHeaderText(null);
        aviso.showAndWait();
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

    private void mostrarErrorSistema(String mensaje) {
        Alert alerta = new Alert(Alert.AlertType.ERROR, mensaje);
        alerta.setTitle("Error inesperado");
        alerta.setHeaderText(null);
        alerta.showAndWait();
    }
}
