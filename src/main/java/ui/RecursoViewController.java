package ui;

import Sistema.DTO.RecursoDTO;
import Sistema.DTO.CategoriaDTO;        // NOTA: DTO puede estar en esta capa, se encarga de viajar capa por capa
import Sistema.Service.RecursoService; // Controlador intermedio

import java.util.List;
import java.util.Optional;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.stage.Stage;
import javafx.scene.Parent;
import javafx.fxml.FXMLLoader;
import javafx.event.ActionEvent;

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

    // lista observable que alimenta el TableView
    private ObservableList<RecursoDTO> listaRecursos = FXCollections.observableArrayList();

    @FXML
    public void initialize(){
        this.recursoService = new RecursoService();

        // 1. deshabilitamos la edición manual del ID
        txtIdRecurso.setDisable(true);
        btnBorrarRecursos.setDisable(true);

        // 2. vinculamos columnas con atributos/getters de RecursoDTO
        colIdListadoRecursos.setCellValueFactory(new PropertyValueFactory<>("id"));
        colDescripcionListadoRecursos.setCellValueFactory(new PropertyValueFactory<>("descripcion"));

        colCategoriaListadoRecursos.setCellValueFactory(cellData -> {
            RecursoDTO recurso = cellData.getValue();
            if(recurso != null && recurso.getCategoria() != null)
                return new SimpleStringProperty(recurso.getCategoria().getDescripcion());
            return new SimpleStringProperty("");
        });

        tvwRecursos.setItems(listaRecursos);

        // 3. evento de seleccion: al hacer clic en una fila, llena el formulario
        tvwRecursos.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                mostrarDetalleRecurso(newValue);
            }
        });

        // 4. asignacion de acciones (eventos) a los botones
        btnGuardarRecursos.setOnAction(evento -> guardarRecurso());
        btnBorrarRecursos.setOnAction(evento -> borrarRecurso());
        btnLimpiarRecursos.setOnAction(evento -> limpiarFormulario()); // Corregido el typo
        btnFiltroBuscarRecursos.setOnAction(evento -> buscarRecursos());

        // dejamos la vista lista con categorias, ID pregenerado y la tabla cargada
        cargarCategorias();
        limpiarFormulario();
        cargarDatosDesdeSistema();
    }

    // TODO: llena el combobox de categoria
    private void cargarCategorias() {
        try {
            // Asumiendo que el controller tiene o tendrá el acceso a categorías
            // List<CategoriaDTO> categorias = gestionRecursoController.listarCategorias();
            // cbCategoriaRecurso.setItems(FXCollections.observableArrayList(categorias));
            // cbFiltroCategoriaRecursos.setItems(FXCollections.observableArrayList(categorias));
        } catch (Exception e) {
            // Manejo silencioso en caso de no estar listo el módulo de categorías
        }
    }

    // rellena la tabla desde el servicio/repositorio
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
        try{
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

        try {
            if (categoriaSeleccionada != null) {
                List<RecursoDTO> filtrados = recursoService.filtrarPorCategoria(categoriaSeleccionada.getId());
                cargarTabla(filtrados);
            } else {
                // si no hay filtro, cargamos toda la lista
                cargarDatosDesdeSistema();
            }
        } catch (Exception ex) {
            mostrarAlerta(AlertType.INFORMATION, "Búsqueda", ex.getMessage());
        }
    }

    // Método auxiliar para construir alertas estándar
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
}