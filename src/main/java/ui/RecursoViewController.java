    package ui;

    import Sistema.DTO.RecursoDTO;
    import Sistema.DTO.CategoriaDTO;
    import Sistema.Service.RecursoService;
    import Sistema.Service.CategoriaService;

    // imports necesarios para el reporte de PDF
    import Sistema.DTO.ReportConfigDTO;
    import Sistema.Logica.Exception.ReportExceptionLogica;

    import java.util.List;
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

    public class RecursoViewController {

        private RecursoService recursoService;
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

        @FXML
        public void initialize(){
            this.recursoService = new RecursoService();

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
            cargarDatosDesdeSistema();
        }

        private void cargarCategorias() {
            try {
                List<CategoriaDTO> categorias = new CategoriaService().listarTodos();

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
            } catch (Exception e) {
                // Manejo silencioso en caso de no estar listo el módulo de categorías
            }
        }

        public void cargarTabla(List<RecursoDTO> recursos){
            if (recursos != null) {
                listaRecursos.setAll(recursos);
            } else {
                listaRecursos.clear();
            }
        }

        private void cargarDatosDesdeSistema(){
            try{
                List<RecursoDTO> recursos = recursoService.listarRecursos();
                cargarTabla(recursos);
            } catch (Exception error){
                listaRecursos.clear();
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
                RecursoDTO existente = null;
                try {
                    existente = recursoService.buscarRecursoID(id);
                } catch (Exception e) {
                    existente = null;
                }

                if (existente != null) {
                    boolean modificado = recursoService.modificarRecurso(recurso);
                    if (modificado)
                        mostrarAlerta(AlertType.INFORMATION, "Éxito", "El recurso fue modificado correctamente");
                } else {
                    int resultado = recursoService.registrarRecurso(recurso);
                    if (resultado == 1)
                        mostrarAlerta(AlertType.INFORMATION, "Éxito", "El recurso fue registrado con el ID: " + id);
                }

                limpiarFormulario();
                cargarDatosDesdeSistema();

            } catch (IllegalArgumentException ex) {
                mostrarAlerta(AlertType.WARNING, "Atención", ex.getMessage());
            } catch (Exception ex) {
                mostrarAlerta(AlertType.ERROR, "Error", "Ocurrió un error no esperado: " + ex.getMessage());
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
                    boolean eliminado = recursoService.eliminarRecurso(id);
                    if (eliminado) {
                        mostrarAlerta(AlertType.INFORMATION, "Éxito", "Recurso eliminado correctamente");
                        limpiarFormulario();
                        cargarDatosDesdeSistema();
                    }
                } catch (IllegalArgumentException ex) {
                    mostrarAlerta(AlertType.WARNING, "No se puede eliminar", ex.getMessage());
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

            } catch (Exception ex) { mostrarAlerta(AlertType.ERROR, "Error en Búsqueda", ex.getMessage()); }
        }

        private void mostrarAlerta(AlertType tipo, String titulo, String mensaje) {
            Alert alert = new Alert(tipo);
            alert.setTitle(titulo);
            alert.setHeaderText(null);
            alert.setContentText(mensaje);
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