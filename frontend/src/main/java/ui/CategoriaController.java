package ui;

import Sistema.DTO.CategoriaDTO;
import Sistema.DTO.ResultadoOperacionDTO;
import Sistema.Service.ICategoriaService;
import Sistema.Service.Red.ProxyCategoriaService;
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

// Usa ProxyCategoriaService (CAT_*). La carga inicial va en TareaRed; buscar,
// guardar y borrar son directos (decision 13 del equipo).
//
// A diferencia de FuncionarioController (que se conecta por codigo desde
// MainController), este usa fx:controller directo en CategoriasView.fxml
// + <fx:include> desde MainView.fxml -- el patron que se acordo usar de
// aca en adelante para toda vista nueva embebida en un tab (igual que
// Recursos, de Kris).
public class CategoriaController {

    static final String MENSAJE_SIN_CONEXION_CARGAR =
            "No hay comunicación con el servidor; no se pudo cargar el listado de categorías. "
                    + "Intente de nuevo y, si sigue, reinicie la aplicación.";
    static final String MENSAJE_SIN_CONEXION_BUSCAR =
            "No hay comunicación con el servidor; no se pudo realizar la búsqueda. "
                    + "Intente de nuevo y, si sigue, reinicie la aplicación.";
    static final String MENSAJE_SIN_CONEXION_GUARDAR =
            "No hay comunicación con el servidor; no se pudo confirmar si se guardó la categoría. "
                    + "Intente de nuevo y, si sigue, reinicie la aplicación.";
    static final String MENSAJE_SIN_CONEXION_BORRAR =
            "No hay comunicación con el servidor; no se pudo confirmar si se borró la categoría. "
                    + "Intente de nuevo y, si sigue, reinicie la aplicación.";

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

    private final ICategoriaService categoriaService;
    private final PdfController pdfController = new PdfController();

    private CategoriaDTO categoriaSeleccionada;

    // Lo usa FXMLLoader por el fx:controller de CategoriasView.fxml
    public CategoriaController() {
        this(new ProxyCategoriaService());
    }

    // Para las pruebas: permite usar un ICategoriaService falso sin red
    CategoriaController(ICategoriaService categoriaService) {
        this.categoriaService = Objects.requireNonNull(categoriaService);
    }

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

        // carga inicial en segundo plano; los callbacks vuelven al hilo de JavaFX
        TareaRed.ejecutar(categoriaService::listarTodos, this::mostrarListado, this::mostrarErrorCarga);
    }

    // Recarga directa, despues de guardar o borrar
    private void cargarListadoCompleto() {
        try {
            mostrarListado(categoriaService.listarTodos());
        } catch (RuntimeException e) {
            mostrarErrorCarga(e);
        }
    }

    private void mostrarListado(List<CategoriaDTO> categorias) {
        tablaListado.setItems(FXCollections.observableArrayList(categorias));
    }

    // Recibe un Throwable porque TareaRed entrega asi el error de la carga inicial
    private void mostrarErrorCarga(Throwable error) {
        if (error instanceof IllegalArgumentException) {
            mostrarErrorValidacion(error.getMessage());
        } else if (error instanceof IllegalStateException) {
            mostrarErrorConexion(MENSAJE_SIN_CONEXION_CARGAR);
        } else {
            mostrarErrorSistema("No se pudo cargar el listado de categorias.\n" + error.getMessage());
        }
    }

    private void buscar() {
        try {
            List<CategoriaDTO> resultado = categoriaService.buscar(campoBusquedaDescripcion.getText());
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
            ResultadoOperacionDTO resultado = esNueva
                    ? categoriaService.registrar(categoria)
                    : categoriaService.modificar(categoria);

            if (resultado.isExito()) {
                limpiar();
                cargarListadoCompleto();
                mostrarMensajeExito(esNueva ? "Categoria registrada correctamente."
                        : "Categoria modificada correctamente.");
            } else {
                mostrarErrorValidacion(resultado.getMensaje());
            }
        } catch (IllegalArgumentException e) {
            mostrarErrorValidacion(e.getMessage());
        } catch (IllegalStateException e) {
            mostrarErrorConexion(MENSAJE_SIN_CONEXION_GUARDAR);
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
                ResultadoOperacionDTO resultado =
                        categoriaService.eliminar(categoriaSeleccionada.getId());

                if (resultado.isExito()) {
                    limpiar();
                    cargarListadoCompleto();
                    mostrarMensajeExito("Categoria eliminada correctamente.");
                } else {
                    // Aca cae, por ejemplo, "tiene recursos asociados" --
                    // error de regla de negocio, no de sistema.
                    mostrarErrorValidacion(resultado.getMensaje());
                }
            } catch (IllegalArgumentException e) {
                mostrarErrorValidacion(e.getMessage());
            } catch (IllegalStateException e) {
                mostrarErrorConexion(MENSAJE_SIN_CONEXION_BORRAR);
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
        if (tablaListado.getItems().isEmpty()) {
            mostrarErrorValidacion("No hay categorías para incluir en el reporte.");
            return;
        }

        ReportConfigDTO configuracion = new ReportConfigDTO(
                "Listado de categorías", "Categorías", "categorias");
        if (!campoBusquedaDescripcion.getText().isBlank()) {
            configuracion.agregarFiltro("Descripción", campoBusquedaDescripcion.getText());
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
