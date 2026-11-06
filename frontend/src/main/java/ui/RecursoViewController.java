package ui;

import Sistema.DTO.RecursoDTO;
import Sistema.DTO.CategoriaDTO;
import Sistema.DTO.ResultadoOperacionDTO;
import Sistema.Service.IRecursoService;
import Sistema.Service.ICategoriaService;
import Sistema.Service.Red.ProxyRecursoService;
import Sistema.Service.Red.ProxyCategoriaService;
import Sistema.Red.TareaRed;
import Sistema.util.SessionManager;

// imports necesarios para el reporte de PDF
import Sistema.DTO.ReportConfigDTO;
import Sistema.Logica.Exception.ReportExceptionLogica;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;

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
    static final String MENSAJE_SIN_CONEXION_ID =
            "No hay comunicación con el servidor; no se pudo obtener un nuevo ID para el recurso. "
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
    private RecursoDTO recursoSeleccionado;
    private long numeroConsultaRecursos;
    private long numeroCargaCategorias;
    private long numeroGeneracionId;
    private long numeroOperacion;
    private boolean consultaRecursosEnCurso;
    private boolean cargaCategoriasEnCurso;
    private boolean generacionIdEnCurso;
    private boolean operacionEnCurso;
    private boolean actualizandoCategorias;

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

        tvwRecursos.getSelectionModel().selectedItemProperty().addListener(
                (observable, anterior, actual) -> cambiarSeleccion(actual));

        btnGuardarRecursos.setOnAction(evento -> guardarRecurso());
        btnBorrarRecursos.setOnAction(evento -> borrarRecurso());
        btnLimpiarRecursos.setOnAction(evento -> {
            if (!operacionEnCurso && !generacionIdEnCurso) limpiarFormulario();
        });
        btnFiltroBuscarRecursos.setOnAction(evento -> buscarRecursos());
        btnImprimirRecursos.setOnAction(evento -> generarPdf());
        cbFiltroCategoriaRecursos.valueProperty().addListener((observable, anterior, actual) ->
                invalidarConsultaPorCambioFiltros());
        txtFiltroDescripcionRecursos.textProperty().addListener((observable, anterior, actual) ->
                invalidarConsultaPorCambioFiltros());

        cargarCategorias();
        limpiarFormulario();
        iniciarConsultaRecursos(recursoService::listarRecursos, false);
    }

    // La llaman initialize y MainController (cada vez que se entra a la pestana Recursos).
    // En segundo plano, para no congelar la ventana al cambiar de pestana.
    public void cargarCategorias() {
        long cargaActual = ++numeroCargaCategorias;
        cargaCategoriasEnCurso = true;
        actualizarEstadoControles();
        TareaRed.ejecutar(
                categoriaService::listarTodos,
                categorias -> {
                    if (cargaActual != numeroCargaCategorias || !cargaCategoriasEnCurso) return;
                    cargaCategoriasEnCurso = false;
                    if (categorias == null) {
                        actualizarEstadoControles();
                        mostrarAlerta(
                                AlertType.ERROR,
                                "Error",
                                "El servidor no devolvió una lista válida de categorías."
                        );
                        return;
                    }
                    mostrarCategorias(categorias);
                    actualizarEstadoControles();
                },
                error -> {
                    if (cargaActual != numeroCargaCategorias || !cargaCategoriasEnCurso) return;
                    cargaCategoriasEnCurso = false;
                    actualizarEstadoControles();
                    mostrarErrorCategorias(error);
                }
        );
    }

    private void mostrarCategorias(List<CategoriaDTO> categorias) {
        String idCategoriaFormulario = idCategoria(cbCategoriaRecurso.getValue());
        String idCategoriaFiltro = idCategoria(cbFiltroCategoriaRecursos.getValue());

        actualizandoCategorias = true;
        try {
            // Para el formulario de guardado
            cbCategoriaRecurso.setItems(FXCollections.observableArrayList(categorias));
            cbCategoriaRecurso.setValue(buscarCategoria(categorias, idCategoriaFormulario));

            // Para el filtro: Agregamos la opción "Todas"
            ObservableList<CategoriaDTO> opcionesFiltro = FXCollections.observableArrayList();

            CategoriaDTO opcionTodas = new CategoriaDTO();
            opcionTodas.setId(null);
            opcionTodas.setDescripcion("-- Todas las categorías --");

            opcionesFiltro.add(opcionTodas);
            opcionesFiltro.addAll(categorias);

            cbFiltroCategoriaRecursos.setItems(opcionesFiltro);
            cbFiltroCategoriaRecursos.setValue(idCategoriaFiltro == null
                    ? opcionTodas
                    : buscarCategoria(categorias, idCategoriaFiltro));
        } finally {
            actualizandoCategorias = false;
        }
        if (!Objects.equals(idCategoriaFiltro, idCategoria(cbFiltroCategoriaRecursos.getValue()))) {
            invalidarConsultaPorCambioFiltros();
        }
    }

    private static String idCategoria(CategoriaDTO categoria) {
        return categoria == null ? null : categoria.getId();
    }

    private static CategoriaDTO buscarCategoria(
            List<CategoriaDTO> categorias,
            String idCategoria
    ) {
        if (idCategoria == null) return null;
        return categorias.stream()
                .filter(categoria -> Objects.equals(idCategoria, categoria.getId()))
                .findFirst()
                .orElse(null);
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

    private void mostrarErrorRecursos(Throwable error) {
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
        recursoSeleccionado = null;
        tvwRecursos.getSelectionModel().clearSelection();
        txtIdRecurso.clear();
        txtDescripcionRecurso.clear();
        cbCategoriaRecurso.setValue(null);
        generarNuevoIdAsincrono();
    }

    private void generarNuevoIdAsincrono() {
        long generacionActual = ++numeroGeneracionId;
        txtIdRecurso.clear();

        if (!SessionManager.getInstancia().esAdministrador()) {
            generacionIdEnCurso = false;
            actualizarEstadoControles();
            return;
        }

        generacionIdEnCurso = true;
        actualizarEstadoControles();

        TareaRed.ejecutar(
                recursoService::generarNuevoId,
                id -> {
                    if (generacionActual != numeroGeneracionId || !generacionIdEnCurso) return;
                    generacionIdEnCurso = false;
                    if (id == null || id.isBlank()) {
                        txtIdRecurso.clear();
                        actualizarEstadoControles();
                        mostrarAlerta(
                                AlertType.ERROR,
                                "Error",
                                "El servidor no devolvió un ID válido para el recurso."
                        );
                        return;
                    }
                    txtIdRecurso.setText(id);
                    actualizarEstadoControles();
                },
                error -> {
                    if (generacionActual != numeroGeneracionId || !generacionIdEnCurso) return;
                    generacionIdEnCurso = false;
                    txtIdRecurso.clear();
                    actualizarEstadoControles();
                    mostrarErrorCarga(error, MENSAJE_SIN_CONEXION_ID);
                }
        );
    }

    @FXML
    private void guardarRecurso(){
        if (operacionEnCurso || btnGuardarRecursos.isDisabled()) return;

        String id = txtIdRecurso.getText().trim();
        String descripcion = txtDescripcionRecurso.getText().trim();
        CategoriaDTO categoria = cbCategoriaRecurso.getValue();
        CategoriaDTO categoriaCapturada = categoria == null ? null
                : new CategoriaDTO(categoria.getId(), categoria.getDescripcion());
        RecursoDTO recurso = new RecursoDTO(id, descripcion, categoriaCapturada);
        iniciarOperacionGuardado(recurso);
    }

    private void iniciarOperacionGuardado(RecursoDTO recurso) {
        long operacionActual = prepararOperacion();
        TareaRed.ejecutar(
                () -> {
                    boolean esModificacion = existeRecurso(recurso.getId());
                    ResultadoOperacionDTO resultado = esModificacion
                            ? recursoService.modificarRecurso(recurso)
                            : recursoService.registrarRecurso(recurso);
                    return new ResultadoGuardado(resultado, esModificacion);
                },
                resultado -> completarGuardado(operacionActual, recurso, resultado),
                error -> mostrarErrorOperacion(
                        operacionActual,
                        error,
                        "Atención",
                        MENSAJE_SIN_CONEXION_GUARDAR,
                        "Ocurrió un error no esperado: "
                )
        );
    }

    private void completarGuardado(
            long operacion,
            RecursoDTO recurso,
            ResultadoGuardado guardado
    ) {
        if (!esOperacionVigente(operacion)) return;
        if (guardado == null || guardado.resultado() == null) {
            finalizarOperacion();
            mostrarAlerta(AlertType.ERROR, "Error", "El servidor no devolvió un resultado válido.");
            return;
        }
        if (!guardado.resultado().isExito()) {
            finalizarOperacion();
            mostrarAlerta(AlertType.WARNING, "Atención", guardado.resultado().getMensaje());
            return;
        }

        mostrarAlerta(AlertType.INFORMATION, "Éxito", guardado.esModificacion()
                ? "El recurso fue modificado correctamente"
                : "El recurso fue registrado con el ID: " + recurso.getId());
        limpiarFormulario();
        actualizarEstadoControles();
        recargarDespuesOperacion(operacion);
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
            if ("El recurso solicitado no fue encontrado".equals(e.getMessage())) return false;
            throw e;
        }
    }

    @FXML
    private void borrarRecurso(){
        if (operacionEnCurso) return;
        if (recursoSeleccionado == null) {
            mostrarAlerta(AlertType.WARNING, "Selección requerida", "Seleccione un recurso de la tabla para eliminar");
            return;
        }

        RecursoDTO recursoABorrar = recursoSeleccionado;
        String idABorrar = recursoABorrar.getId();
        if (!confirmarBorrado(recursoABorrar)) return;

        long operacionActual = prepararOperacion();
        TareaRed.ejecutar(
                () -> recursoService.eliminarRecurso(idABorrar),
                resultado -> completarBorrado(operacionActual, resultado),
                error -> mostrarErrorOperacion(
                        operacionActual,
                        error,
                        "No se puede eliminar",
                        MENSAJE_SIN_CONEXION_BORRAR,
                        "Error al eliminar: "
                )
        );
    }

    boolean confirmarBorrado(RecursoDTO recurso) {
        Alert confirmacion = new Alert(
                AlertType.CONFIRMATION,
                "¿Está seguro de eliminar el recurso con ID " + recurso.getId() + "?",
                ButtonType.YES,
                ButtonType.NO
        );
        return confirmacion.showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
    }

    private void completarBorrado(long operacion, ResultadoOperacionDTO resultado) {
        if (!esOperacionVigente(operacion)) return;
        if (resultado == null) {
            finalizarOperacion();
            mostrarAlerta(AlertType.ERROR, "Error al eliminar", "El servidor no devolvió un resultado válido.");
            return;
        }
        if (!resultado.isExito()) {
            finalizarOperacion();
            mostrarAlerta(AlertType.WARNING, "No se puede eliminar", resultado.getMensaje());
            return;
        }

        mostrarAlerta(AlertType.INFORMATION, "Éxito", "Recurso eliminado correctamente");
        limpiarFormulario();
        actualizarEstadoControles();
        recargarDespuesOperacion(operacion);
    }

    @FXML
    private void buscarRecursos(){
        if (operacionEnCurso) return;

        CategoriaDTO categoriaSeleccionada = cbFiltroCategoriaRecursos.getValue();
        String textoDescripcion = txtFiltroDescripcionRecursos.getText();

        // Si la categoría seleccionada es la opción "Todas" (cuyo id es null) o no hay ninguna seleccionada
        String idCategoria = (categoriaSeleccionada != null) ? categoriaSeleccionada.getId() : null;

        iniciarConsultaRecursos(
                () -> recursoService.filtrarRecursos(idCategoria, textoDescripcion),
                true
        );
    }

    private void iniciarConsultaRecursos(
            Callable<List<RecursoDTO>> consulta,
            boolean informarVacio
    ) {
        long consultaActual = ++numeroConsultaRecursos;
        consultaRecursosEnCurso = true;
        actualizarEstadoControles();
        TareaRed.ejecutar(
                consulta,
                recursos -> {
                    if (consultaActual != numeroConsultaRecursos || !consultaRecursosEnCurso) return;
                    consultaRecursosEnCurso = false;
                    actualizarEstadoControles();
                    if (recursos == null) {
                        mostrarAlerta(AlertType.ERROR, "Error", "El servidor no devolvió una lista válida de recursos.");
                        return;
                    }
                    cargarTabla(recursos);
                    if (informarVacio && recursos.isEmpty()) {
                        mostrarAlerta(
                                AlertType.INFORMATION,
                                "Búsqueda",
                                "No se encontraron recursos con los criterios especificados."
                        );
                    }
                },
                error -> {
                    if (consultaActual != numeroConsultaRecursos || !consultaRecursosEnCurso) return;
                    consultaRecursosEnCurso = false;
                    actualizarEstadoControles();
                    mostrarErrorCarga(error, informarVacio
                            ? MENSAJE_SIN_CONEXION_BUSCAR
                            : MENSAJE_SIN_CONEXION_CARGAR);
                }
        );
    }

    private long prepararOperacion() {
        long operacionActual = ++numeroOperacion;
        numeroConsultaRecursos++;
        consultaRecursosEnCurso = false;
        operacionEnCurso = true;
        actualizarEstadoControles();
        return operacionActual;
    }

    private boolean esOperacionVigente(long operacion) {
        return operacion == numeroOperacion && operacionEnCurso;
    }

    private void recargarDespuesOperacion(long operacion) {
        TareaRed.ejecutar(
                recursoService::listarRecursos,
                recursos -> {
                    if (!esOperacionVigente(operacion)) return;
                    if (recursos == null) {
                        finalizarOperacion();
                        mostrarAlerta(AlertType.ERROR, "Error", "El servidor no devolvió una lista válida de recursos.");
                        return;
                    }
                    cargarTabla(recursos);
                    finalizarOperacion();
                },
                error -> {
                    if (!esOperacionVigente(operacion)) return;
                    finalizarOperacion();
                    mostrarErrorRecursos(error);
                }
        );
    }

    private void mostrarErrorOperacion(
            long operacion,
            Throwable error,
            String tituloValidacion,
            String mensajeConexion,
            String prefijoInesperado
    ) {
        if (!esOperacionVigente(operacion)) return;
        finalizarOperacion();
        if (error instanceof IllegalArgumentException) {
            mostrarAlerta(AlertType.WARNING, tituloValidacion, error.getMessage());
        } else if (error instanceof IllegalStateException) {
            mostrarAlerta(AlertType.ERROR, "Error de conexión", mensajeConexion);
        } else {
            mostrarAlerta(AlertType.ERROR, "Error", prefijoInesperado + error.getMessage());
        }
    }

    private void finalizarOperacion() {
        operacionEnCurso = false;
        actualizarEstadoControles();
    }

    private void invalidarConsultaPorCambioFiltros() {
        if (actualizandoCategorias) return;
        numeroConsultaRecursos++;
        if (!consultaRecursosEnCurso || operacionEnCurso) return;
        consultaRecursosEnCurso = false;
        actualizarEstadoControles();
    }

    private void actualizarEstadoControles() {
        boolean bloquearPorOperacion = operacionEnCurso;
        cbFiltroCategoriaRecursos.setDisable(bloquearPorOperacion || cargaCategoriasEnCurso);
        txtFiltroDescripcionRecursos.setDisable(bloquearPorOperacion);
        btnFiltroBuscarRecursos.setDisable(bloquearPorOperacion);
        btnFiltroBuscarRecursos.setText(consultaRecursosEnCurso ? "Buscando..." : "Buscar");
        btnImprimirRecursos.setDisable(bloquearPorOperacion || consultaRecursosEnCurso);
        txtIdRecurso.setDisable(true);
        txtIdRecurso.setPromptText(generacionIdEnCurso ? "Generando ID..." : "");
        cbCategoriaRecurso.setDisable(bloquearPorOperacion || cargaCategoriasEnCurso);
        txtDescripcionRecurso.setDisable(bloquearPorOperacion);
        btnGuardarRecursos.setDisable(
                bloquearPorOperacion
                        || cargaCategoriasEnCurso
                        || generacionIdEnCurso
                        || cbCategoriaRecurso.getItems() == null
                        || cbCategoriaRecurso.getItems().isEmpty()
                        || txtIdRecurso.getText() == null
                        || txtIdRecurso.getText().isBlank()
        );
        btnGuardarRecursos.setText(bloquearPorOperacion ? "Procesando..." : "Guardar");
        btnBorrarRecursos.setDisable(bloquearPorOperacion || recursoSeleccionado == null);
        btnLimpiarRecursos.setDisable(bloquearPorOperacion || generacionIdEnCurso);
        tvwRecursos.setDisable(bloquearPorOperacion || consultaRecursosEnCurso);
    }

    void mostrarAlerta(AlertType tipo, String titulo, String mensaje) {
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
            recursoSeleccionado = recurso;
            numeroGeneracionId++;
            generacionIdEnCurso = false;
            txtIdRecurso.setText(recurso.getId());
            txtIdRecurso.setDisable(true);

            txtDescripcionRecurso.setText(recurso.getDescripcion());
            cbCategoriaRecurso.setValue(recurso.getCategoria());
            actualizarEstadoControles();
        }
    }

    private void cambiarSeleccion(RecursoDTO recurso) {
        if (recurso == null) {
            recursoSeleccionado = null;
            actualizarEstadoControles();
            return;
        }
        mostrarDetalleRecurso(recurso);
    }

    private record ResultadoGuardado(
            ResultadoOperacionDTO resultado,
            boolean esModificacion
    ) {
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
