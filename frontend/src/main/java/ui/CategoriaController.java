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
import java.util.concurrent.Callable;

// Usa ProxyCategoriaService (CAT_*).
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
    private long numeroConsulta;
    private long numeroOperacion;
    private boolean consultaEnCurso;
    private boolean operacionEnCurso;

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
        campoBusquedaDescripcion.textProperty().addListener((observable, anterior, actual) ->
                invalidarConsultaPorCambioFiltro());

        campoId.setDisable(true); // el ID siempre es autogenerado, nunca editable

        iniciarConsulta(
                categoriaService::listarTodos,
                MENSAJE_SIN_CONEXION_CARGAR,
                "No se pudo cargar el listado de categorias.",
                "Cargando categorías..."
        );
    }

    private void iniciarConsulta(
            Callable<List<CategoriaDTO>> consulta,
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
                categorias -> completarConsulta(consultaActual, categorias),
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
            List<CategoriaDTO> categorias
    ) {
        if (consulta != numeroConsulta || !consultaEnCurso) return;

        consultaEnCurso = false;
        actualizarEstadoControles();
        if (categorias == null) {
            mostrarErrorSistema("El servidor no devolvió un listado válido de categorías.");
            return;
        }

        mostrarListado(categorias);
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

    private void mostrarListado(List<CategoriaDTO> categorias) {
        tablaListado.setItems(FXCollections.observableArrayList(categorias));
    }

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
        if (operacionEnCurso) return;

        String descripcion = campoBusquedaDescripcion.getText();
        iniciarConsulta(
                () -> categoriaService.buscar(descripcion),
                MENSAJE_SIN_CONEXION_BUSCAR,
                "No se pudo realizar la busqueda.",
                "Buscando categorías..."
        );
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
        if (operacionEnCurso) return;

        boolean esNueva = (categoriaSeleccionada == null);

        CategoriaDTO categoria = esNueva
                ? new CategoriaDTO(null, campoDescripcion.getText())
                : new CategoriaDTO(categoriaSeleccionada.getId(), campoDescripcion.getText());
        iniciarOperacion(
                () -> esNueva
                        ? categoriaService.registrar(categoria)
                        : categoriaService.modificar(categoria),
                esNueva ? "Registrando categoría..." : "Modificando categoría...",
                esNueva ? "Categoria registrada correctamente."
                        : "Categoria modificada correctamente.",
                MENSAJE_SIN_CONEXION_GUARDAR,
                "No se pudo guardar la categoria."
        );
    }

    private void borrar() {
        if (operacionEnCurso) return;

        if (categoriaSeleccionada == null) {
            mostrarErrorValidacion("Seleccione una categoria del listado para borrar.");
            return;
        }

        CategoriaDTO categoriaABorrar = categoriaSeleccionada;
        if (!confirmarBorrado(categoriaABorrar)) return;

        iniciarOperacion(
                () -> categoriaService.eliminar(categoriaABorrar.getId()),
                "Eliminando categoría...",
                "Categoria eliminada correctamente.",
                MENSAJE_SIN_CONEXION_BORRAR,
                "No se pudo borrar la categoria."
        );
    }

    boolean confirmarBorrado(CategoriaDTO categoria) {
        Alert confirmacion = new Alert(
                Alert.AlertType.CONFIRMATION,
                "¿Está seguro de que desea borrar la categoria \""
                        + categoria.getDescripcion() + "\"?",
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
                categoriaService::listarTodos,
                categorias -> {
                    if (operacion != numeroOperacion || !operacionEnCurso) return;
                    if (categorias == null) {
                        finalizarOperacion();
                        mostrarErrorSistema("El servidor no devolvió un listado válido de categorías.");
                        return;
                    }
                    mostrarListado(categorias);
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

    private void invalidarConsultaPorCambioFiltro() {
        numeroConsulta++;
        if (!consultaEnCurso || operacionEnCurso) return;

        consultaEnCurso = false;
        actualizarEstadoControles();
        mostrarErrorValidacion("El filtro cambió. Presione Buscar nuevamente.");
    }

    private void actualizarEstadoControles() {
        boolean bloquearPorOperacion = operacionEnCurso;
        campoBusquedaDescripcion.setDisable(bloquearPorOperacion);
        btnBuscar.setDisable(bloquearPorOperacion);
        campoId.setDisable(true);
        campoDescripcion.setDisable(bloquearPorOperacion);
        btnGuardar.setDisable(bloquearPorOperacion);
        btnBorrar.setDisable(bloquearPorOperacion);
        btnLimpiar.setDisable(bloquearPorOperacion);
        tablaListado.setDisable(bloquearPorOperacion || consultaEnCurso);
        btnImprimir.setDisable(bloquearPorOperacion || consultaEnCurso);
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

    void mostrarAlertaError(String titulo, String mensaje) {
        Alert alerta = new Alert(Alert.AlertType.ERROR, mensaje);
        alerta.setTitle(titulo);
        alerta.setHeaderText(null);
        // sin esto el dialogo no crece con el texto y un mensaje largo se corta con "..."
        alerta.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        alerta.showAndWait();
    }
}
