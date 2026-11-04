package ui;

import Sistema.DTO.RecursoDTO;
import Sistema.DTO.CategoriaDTO;
import Sistema.DTO.ResultadoOperacionDTO;
import Sistema.Service.IRecursoService;
import Sistema.Service.ICategoriaService;
import Sistema.Service.Red.ProxyRecursoService;
import Sistema.Service.Red.ProxyCategoriaService;
import Sistema.Red.TareaRed;

// imports necesarios para el reporte de PDF
import Sistema.DTO.ReportConfigDTO;
import Sistema.Logica.Exception.ReportExceptionLogica;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javafx.fxml.FXML;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.beans.property.SimpleStringProperty;

import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.control.TableView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.Region;

// Usa ProxyRecursoService (REC_*) y ProxyCategoriaService (CAT_LISTAR, para los combos).
// La carga inicial y cargarCategorias() van en TareaRed; buscar, guardar, borrar y
// generarNuevoId son directos (decision 13 del equipo).
public class RecursoViewController {

    static final String MENSAJE_SIN_CONEXION_CARGAR =
            "No hay comunicación con el servidor; no se pudo cargar el listado de recursos. "
                    + "Intente de nuevo y, si sigue, reinicie la aplicación.";
    static final String MENSAJE_SIN_CONEXION_CATEGORIAS =
            "No hay comunicación con el servidor; no se pudieron cargar las categorías. "
                    + "Intente de nuevo y, si sigue, reinicie la aplicación.";
    static final String MENSAJE_SIN_CONEXION_BUSCAR =
            "No hay comunicación con el servidor; no se pudo realizar la búsqueda. "
                    + "Intente de nuevo y, si sigue, reinicie la aplicación.";
    static final String MENSAJE_SIN_CONEXION_GUARDAR =
            "No hay comunicación con el servidor; no se pudo confirmar si se guardó el recurso. "
                    + "Intente de nuevo y, si sigue, reinicie la aplicación.";
    static final String MENSAJE_SIN_CONEXION_BORRAR =
            "No hay comunicación con el servidor; no se pudo confirmar si se borró el recurso. "
                    + "Intente de nuevo y, si sigue, reinicie la aplicación.";

    private final IRecursoService recursoService;
    private final ICategoriaService categoriaService;
    private final PdfController pdfController = new PdfController();

    @FXML private ComboBox<CategoriaDTO> cbFiltroCategoriaRecursos;
    @FXML private TextField txtFiltroDescripcionRecursos;
    @FXML private Button btnFiltroBuscarRecursos;
    @FXML private Button btnImprimirRecursos;

    @FXML private TextField txtIdRecurso;
    @FXML private ComboBox<CategoriaDTO> cbCategoriaRecurso;
    @FXML private TextField txtDescripcionRecurso;
    @FXML private Button btnGuardarRecursos;
    @FXML private Button btnBorrarRecursos;
    @FXML private Button btnLimpiarRecursos;

    @FXML private TableView<RecursoDTO> tvwRecursos;
    @FXML private TableColumn<RecursoDTO, String> colIdListadoRecursos;
    @FXML private TableColumn<RecursoDTO, String> colCategoriaListadoRecursos;
    @FXML private TableColumn<RecursoDTO, String> colDescripcionListadoRecursos;

    private ObservableList<RecursoDTO> listaRecursos = FXCollections.observableArrayList();

    // Lo usa FXMLLoader por el fx:controller de RecursosView.fxml
    public RecursoViewController() {
        this(new ProxyRecursoService(), new ProxyCategoriaService());
    }

    // Para las pruebas: permite usar servicios falsos sin red
    RecursoViewController(IRecursoService recursoService, ICategoriaService categoriaService) {
        this.recursoService = Objects.requireNonNull(recursoService);
        this.categoriaService = Objects.requireNonNull(categoriaService);
    }

    @FXML
    public void initialize(){
        txtIdRecurso.setDisable(true);
        btnBorrarRecursos.setDisable(true);

        colIdListadoRecursos.setCellValueFactory(new PropertyValueFactory<>("id"));
        colDescripcionListadoRecursos.setCellValueFactory(new PropertyValueFactory<>("descripcion"));

        colCategoriaListadoRecursos.setCellValueFactory(cellData -> {
            RecursoDTO recurso = cellData.getValue();
            if(recurso != null && recurso.getCategoria() != null)
                return new SimpleStringProperty(recurso.getCategoria().getDescripcion());
            return new SimpleStringProperty("");
        });

        tvwRecursos.setItems(listaRecursos);

        tvwRecursos.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) mostrarDetalleRecurso(newValue);
        });

        btnGuardarRecursos.setOnAction(evento -> guardarRecurso());
        btnBorrarRecursos.setOnAction(evento -> borrarRecurso());
        btnLimpiarRecursos.setOnAction(evento -> limpiarFormulario());
        btnFiltroBuscarRecursos.setOnAction(evento -> buscarRecursos());
        btnImprimirRecursos.setOnAction(evento -> generarPdf());

        cargarCategorias();
        limpiarFormulario();
        // carga inicial de la tabla en segundo plano; los callbacks vuelven al hilo de JavaFX
        TareaRed.ejecutar(recursoService::listarRecursos, this::cargarTabla, this::mostrarErrorRecursos);
    }

    // La llaman initialize y MainController (cada vez que se entra a la pestana Recursos).
    // En segundo plano, para no congelar la ventana al cambiar de pestana.
    public void cargarCategorias() {
        TareaRed.ejecutar(categoriaService::listarTodos, this::mostrarCategorias, this::mostrarErrorCategorias);
    }

    private void mostrarCategorias(List<CategoriaDTO> categorias) {
        // Para el formulario de guardado
        cbCategoriaRecurso.setItems(FXCollections.observableArrayList(categorias));

        // Para el filtro: Agregamos la opción "Todas"
        ObservableList<CategoriaDTO> opcionesFiltro = FXCollections.observableArrayList();

        CategoriaDTO opcionTodas = new CategoriaDTO();
        opcionTodas.setId(null);
        opcionTodas.setDescripcion("-- Todas las categorías --");

        opcionesFiltro.add(opcionTodas);
        opcionesFiltro.addAll(categorias);

        cbFiltroCategoriaRecursos.setItems(opcionesFiltro);
    }

    // Antes era silencioso; con red un combo vacio sin explicacion confunde
    private void mostrarErrorCategorias(Throwable error) {
        mostrarErrorCarga(error, MENSAJE_SIN_CONEXION_CATEGORIAS);
    }

    public void cargarTabla(List<RecursoDTO> recursos){
        if (recursos != null) {
            listaRecursos.setAll(recursos);
        } else {
            listaRecursos.clear();
        }
    }

    // Recarga directa, despues de guardar o borrar
    private void cargarDatosDesdeSistema(){
        try{
            List<RecursoDTO> recursos = recursoService.listarRecursos();
            cargarTabla(recursos);
        } catch (RuntimeException error){
            mostrarErrorRecursos(error);
        }
    }

    private void mostrarErrorRecursos(Throwable error) {
        listaRecursos.clear();
        mostrarErrorCarga(error, MENSAJE_SIN_CONEXION_CARGAR);
    }

    // Recibe un Throwable porque TareaRed entrega asi el error de la carga inicial
    private void mostrarErrorCarga(Throwable error, String mensajeSinConexion) {
        if (error instanceof IllegalArgumentException) {
            mostrarAlerta(AlertType.WARNING, "Atención", error.getMessage());
        } else if (error instanceof IllegalStateException) {
            mostrarAlerta(AlertType.ERROR, "Error de conexión", mensajeSinConexion);
        } else {
            mostrarAlerta(AlertType.ERROR, "Error", "Ocurrió un error no esperado: " + error.getMessage());
        }
    }

    @FXML
    private void limpiarFormulario(){
        try {
            String nuevoID = recursoService.generarNuevoId();
            txtIdRecurso.setText(nuevoID);
        } catch (Exception error) {
            txtIdRecurso.clear();
        }

        txtDescripcionRecurso.clear();
        cbCategoriaRecurso.setValue(null);

        tvwRecursos.getSelectionModel().clearSelection();
        btnBorrarRecursos.setDisable(true);
    }

    @FXML
    private void guardarRecurso(){
        String id = txtIdRecurso.getText().trim();
        String descripcion = txtDescripcionRecurso.getText().trim();
        CategoriaDTO categoria = cbCategoriaRecurso.getValue();

        try {
            RecursoDTO recurso = new RecursoDTO(id, descripcion, categoria);
            boolean esModificacion = existeRecurso(id);
            ResultadoOperacionDTO resultado = esModificacion
                    ? recursoService.modificarRecurso(recurso)
                    : recursoService.registrarRecurso(recurso);

            // un fallo se muestra igual que antes la IllegalArgumentException
            if (!resultado.isExito()) {
                mostrarAlerta(AlertType.WARNING, "Atención", resultado.getMensaje());
                return;
            }
            mostrarAlerta(AlertType.INFORMATION, "Éxito", esModificacion
                    ? "El recurso fue modificado correctamente"
                    : "El recurso fue registrado con el ID: " + id);

            limpiarFormulario();
            cargarDatosDesdeSistema();

        } catch (IllegalArgumentException ex) {
            mostrarAlerta(AlertType.WARNING, "Atención", ex.getMessage());
        } catch (IllegalStateException ex) {
            mostrarAlerta(AlertType.ERROR, "Error de conexión", MENSAJE_SIN_CONEXION_GUARDAR);
        } catch (Exception ex) {
            mostrarAlerta(AlertType.ERROR, "Error", "Ocurrió un error no esperado: " + ex.getMessage());
        }
    }

    /**
     * Decide si guardarRecurso modifica o registra. Solo la IllegalArgumentException
     * del servidor ("El recurso solicitado no fue encontrado") cuenta como "no existe".
     * Una IllegalStateException (red) se deja pasar: guardarRecurso avisa y no intenta registrar.
     * De paquete para las pruebas; no usa controles de JavaFX.
     */
    boolean existeRecurso(String id) {
        try {
            return recursoService.buscarRecursoID(id) != null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @FXML
    private void borrarRecurso(){
        String id = txtIdRecurso.getText().trim();

        if (id.isEmpty()) {
            mostrarAlerta(AlertType.WARNING, "Selección requerida", "Seleccione un recurso de la tabla para eliminar");
            return;
        }

        Alert confirmacion = new Alert(AlertType.CONFIRMATION, "¿Está seguro de eliminar el recurso con ID " + id + "?", ButtonType.YES, ButtonType.NO);
        Optional<ButtonType> respuesta = confirmacion.showAndWait();

        if (respuesta.isPresent() && respuesta.get() == ButtonType.YES) {
            try {
                ResultadoOperacionDTO resultado = recursoService.eliminarRecurso(id);
                if (resultado.isExito()) {
                    mostrarAlerta(AlertType.INFORMATION, "Éxito", "Recurso eliminado correctamente");
                    limpiarFormulario();
                    cargarDatosDesdeSistema();
                } else {
                    // igual que antes la IllegalArgumentException
                    mostrarAlerta(AlertType.WARNING, "No se puede eliminar", resultado.getMensaje());
                }
            } catch (IllegalArgumentException ex) {
                mostrarAlerta(AlertType.WARNING, "No se puede eliminar", ex.getMessage());
            } catch (IllegalStateException ex) {
                mostrarAlerta(AlertType.ERROR, "Error de conexión", MENSAJE_SIN_CONEXION_BORRAR);
            } catch (Exception ex) {
                mostrarAlerta(AlertType.ERROR, "Error al eliminar", ex.getMessage());
            }
        }
    }

    @FXML
    private void buscarRecursos(){
        CategoriaDTO categoriaSeleccionada = cbFiltroCategoriaRecursos.getValue();
        String textoDescripcion = txtFiltroDescripcionRecursos.getText();

        // Si la categoría seleccionada es la opción "Todas" (cuyo id es null) o no hay ninguna seleccionada
        String idCategoria = (categoriaSeleccionada != null) ? categoriaSeleccionada.getId() : null;

        try {
            List<RecursoDTO> resultados = recursoService.filtrarRecursos(idCategoria, textoDescripcion);
            cargarTabla(resultados);

            if (resultados.isEmpty())
                mostrarAlerta(AlertType.INFORMATION, "Búsqueda", "No se encontraron recursos con los criterios especificados.");

        } catch (IllegalArgumentException ex) {
            mostrarAlerta(AlertType.WARNING, "Atención", ex.getMessage());
        } catch (IllegalStateException ex) {
            mostrarAlerta(AlertType.ERROR, "Error de conexión", MENSAJE_SIN_CONEXION_BUSCAR);
        } catch (Exception ex) { mostrarAlerta(AlertType.ERROR, "Error en Búsqueda", ex.getMessage()); }
    }

    private void mostrarAlerta(AlertType tipo, String titulo, String mensaje) {
        Alert alert = new Alert(tipo);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        // sin esto el dialogo no crece con el texto y un mensaje largo se corta con "..."
        alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        alert.showAndWait();
    }

    public void mostrarDetalleRecurso(RecursoDTO recurso){
        if (recurso != null) {
            txtIdRecurso.setText(recurso.getId());
            txtIdRecurso.setDisable(true);

            txtDescripcionRecurso.setText(recurso.getDescripcion());
            cbCategoriaRecurso.setValue(recurso.getCategoria());
            btnBorrarRecursos.setDisable(false);
        }
    }

    // metodos para generar el reporte de PDF
    private boolean validarDatosParaPdf() {
        if (tvwRecursos.getItems().isEmpty()) {
            mostrarAlerta(AlertType.WARNING, "Atención", "No hay recursos registrados en la tabla para exportar.");
            return false;
        }
        return true;
    }

    private ReportConfigDTO crearConfiguracionPdf() {
        ReportConfigDTO configuracion = new ReportConfigDTO(
                "Reporte de Recursos del Sistema",
                "Listado general y filtrado de recursos",
                "Reporte_Recursos.pdf"
        );

        configuracion.setTituloVentana("Guardar reporte de recursos");
        configuracion.setHorizontal(false);

        CategoriaDTO categoriaSeleccionada = cbFiltroCategoriaRecursos.getValue();
        String textoDescripcion = txtFiltroDescripcionRecursos.getText();

        if (categoriaSeleccionada != null && categoriaSeleccionada.getId() != null)
            configuracion.agregarFiltro("Categoría", categoriaSeleccionada.getDescripcion());
        else configuracion.agregarFiltro("Categoría", "Todas");

        if (textoDescripcion != null && !textoDescripcion.trim().isEmpty())
            configuracion.agregarFiltro("Descripción", textoDescripcion.trim());
        else configuracion.agregarFiltro("Descripción", "Todas");

        return configuracion;
    }

    private void generarPdf() {
        if (!validarDatosParaPdf()) return;

        try {
            boolean generado = pdfController.generar(
                    crearConfiguracionPdf(),
                    btnImprimirRecursos.getScene().getWindow(),
                    tvwRecursos
            );

            if (generado) mostrarAlerta(AlertType.INFORMATION, "Éxito", "El reporte PDF fue generado correctamente.");

        } catch (ReportExceptionLogica excepcion) {
            mostrarAlerta(AlertType.ERROR, "Error al generar PDF", excepcion.getMessage());
            excepcion.printStackTrace();
        }
    }


}