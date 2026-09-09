package ui;

import Sistema.DTO.RecursoDTO;
import Sistema.DTO.ReservaDTO;
import Sistema.DTO.Funcionario;
import Sistema.DTO.CategoriaDTO;
import Sistema.util.SessionManager;
import Sistema.Service.ReservaService;
import Sistema.Service.CategoriaService;
import Sistema.util.HorarioSistema;

import java.net.URL;
import java.util.List;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListView;
import javafx.scene.control.ListCell;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TableColumn;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Alert.AlertType;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.cell.PropertyValueFactory;

// paquetes necesarios para el servicio de IA
import Sistema.Service.IAService.ReservaExtraida;
import Sistema.Service.IAService.ExtractorIAReserva;

// paquete necesario para el uso de threads
import javafx.concurrent.Task;

public class ReservasViewController implements Initializable {

    private ReservaService reservaService;
    private CategoriaService categoriaService;

    @FXML private TextField txtFrase;
    @FXML private Button btnExtraerAI;
    @FXML private TextField txtActividad;
    @FXML private DatePicker dpFecha;
    @FXML private ComboBox<LocalTime> cbHoraInicio;
    @FXML private ComboBox<LocalTime> cbHoraFin;
    @FXML private ListView<CategoriaDTO> lvCategorias;


    @FXML private Button btnReservar;
    @FXML private Button btnCancelarReserva;
    @FXML private Button btnLimpiar;
    @FXML private Button btnImprimirReservas;


    @FXML private TableView<ReservaDTO> tvMisReservas;
    @FXML private TableColumn<ReservaDTO, String> colId;
    @FXML private TableColumn<ReservaDTO, String> colActividad;
    @FXML private TableColumn<ReservaDTO, LocalDate> colFecha;
    @FXML private TableColumn<ReservaDTO, String> colHorario;
    @FXML private TableColumn<ReservaDTO, String> colRecursos;
    @FXML private TableColumn<ReservaDTO, String> colEstado;

    // lista observable que alimenta el TableView
    private ObservableList<ReservaDTO> listaReservas = FXCollections.observableArrayList();

    @FXML
    public void initialize(URL location, ResourceBundle resources){

        this.reservaService = new ReservaService();
        this.categoriaService = new CategoriaService();

        // 1. deshabilitamos inicialmente la acción de cancelar si no hay nada seleccionado
        btnCancelarReserva.setDisable(true);

        // 2. vinculamos columnas con atributos/getters de ReservaDTO
        colId.setCellValueFactory(new PropertyValueFactory<>("idReserva"));
        colActividad.setCellValueFactory(new PropertyValueFactory<>("nombreActividad"));
        colFecha.setCellValueFactory(new PropertyValueFactory<>("fecha"));

        colHorario.setCellValueFactory(cellData -> {
            ReservaDTO reserva = cellData.getValue();
            if (reserva != null && reserva.getHoraInicio() != null && reserva.getHoraFin() != null) {
                return new SimpleStringProperty(reserva.getHoraInicio() + " - " + reserva.getHoraFin());
            }
            return new SimpleStringProperty("");
        });

        colRecursos.setCellValueFactory(cellData -> {
            ReservaDTO reserva = cellData.getValue();
            if (reserva != null && reserva.getRecursosReservados() != null && !reserva.getRecursosReservados().isEmpty()) {
                String nombres = reserva.getRecursosReservados().stream()
                        .map(RecursoDTO::getId)
                        .collect(Collectors.joining(", "));
                return new SimpleStringProperty(nombres);
            }
            return new SimpleStringProperty("Sin recursos");
        });

        colEstado.setCellValueFactory(new PropertyValueFactory<>("estado"));

        tvMisReservas.setItems(listaReservas);

        // 3. evento de seleccion: al hacer clic en una fila, activa el boton de cancelar
        tvMisReservas.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            btnCancelarReserva.setDisable(newValue == null);
        });

        // 4. configuracion de controles y eventos a los botones
        configurarHorarios();
        configurarListViewCategorias();

        btnReservar.setOnAction(evento -> guardarReserva());
        btnCancelarReserva.setOnAction(evento -> cancelarReserva());
        btnLimpiar.setOnAction(evento -> limpiarFormulario());
        btnExtraerAI.setOnAction(evento -> onAutocompletarIA());

        cargarCategorias();
        limpiarFormulario();
        cargarDatosDesdeSistema();
    }

    private void configurarHorarios() {
        ObservableList<LocalTime> horasInicio = FXCollections.observableArrayList();
        ObservableList<LocalTime> horasFin = FXCollections.observableArrayList();

        LocalTime hora = HorarioSistema.INICIO_SERVICIO;
        while (hora.isBefore(HorarioSistema.FIN_SERVICIO)) {
            horasInicio.add(hora);
            hora = hora.plusMinutes(HorarioSistema.MINUTOS_POR_BLOQUE);
        }
        hora = HorarioSistema.INICIO_SERVICIO.plusMinutes(HorarioSistema.MINUTOS_POR_BLOQUE);
        while (!hora.isAfter(HorarioSistema.FIN_SERVICIO)) {
            horasFin.add(hora);
            hora = hora.plusMinutes(HorarioSistema.MINUTOS_POR_BLOQUE);
        }
        cbHoraInicio.setItems(horasInicio);
        cbHoraFin.setItems(horasFin);
    }

    private void configurarListViewCategorias() {
        lvCategorias.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        lvCategorias.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(CategoriaDTO item, boolean empty) {
                super.updateItem(item, empty);
                setText((empty || item == null) ? null : item.getDescripcion());
            }
        });
    }

    private void cargarCategorias() {
        try {
            List<CategoriaDTO> categorias = categoriaService.listarTodos();
            lvCategorias.setItems(FXCollections.observableArrayList(categorias));
        } catch (Exception e) {
            // Manejo silencioso en caso de no estar listo el módulo de categorías
        }
    }

    // rellenamos la tabla desde el servicio
    public void cargarTabla(List<ReservaDTO> reservas) {
        if (reservas != null) {
            listaReservas.setAll(reservas);
        } else {
            listaReservas.clear();
        }
    }

    private void cargarDatosDesdeSistema() {
        String idUsuario = SessionManager.getInstancia().getIdUsuario();
        if (idUsuario == null || idUsuario.isBlank()) {
            listaReservas.clear();
            return;
        }

        try {
            List<ReservaDTO> reservas = reservaService.buscarPorFuncionario(idUsuario);
            cargarTabla(reservas);
        } catch (Exception error) {
            listaReservas.clear();
        }
    }

    @FXML
    private void limpiarFormulario() {
        txtFrase.clear();
        txtActividad.clear();
        dpFecha.setValue(null);
        cbHoraInicio.setValue(null);
        cbHoraFin.setValue(null);
        lvCategorias.getSelectionModel().clearSelection();
        tvMisReservas.getSelectionModel().clearSelection();
        btnCancelarReserva.setDisable(true);
    }

    @FXML
    private void guardarReserva() {
        String idUsuario = SessionManager.getInstancia().getIdUsuario();
        Funcionario funcionario = SessionManager.getInstancia().getFuncionarioAsociado();

        if (idUsuario == null || idUsuario.isBlank()) {
            mostrarAlerta(AlertType.WARNING, "Atención", "No se encontró una sesión de usuario activa");
            return;
        }

        String nombreFuncionario = (funcionario != null) ? funcionario.getNombre() : idUsuario;
        String actividad = txtActividad.getText() != null ? txtActividad.getText().trim() : "";
        LocalDate fecha = dpFecha.getValue();
        LocalTime horaInicio = cbHoraInicio.getValue();
        LocalTime horaFin = cbHoraFin.getValue();

        List<CategoriaDTO> seleccionadas = lvCategorias.getSelectionModel().getSelectedItems();
        List<String> idsCategorias = seleccionadas.stream()
                .map(CategoriaDTO::getId)
                .collect(Collectors.toList());

        try {
            int resultado = reservaService.registrarReserva(
                    idUsuario,
                    nombreFuncionario,
                    actividad,
                    fecha,
                    horaInicio,
                    horaFin,
                    idsCategorias
            );

            if (resultado == 1) {
                mostrarAlerta(AlertType.INFORMATION, "Éxito", "La reserva fue registrada correctamente");
                limpiarFormulario();
                cargarDatosDesdeSistema();
            } else {
                mostrarAlerta(AlertType.ERROR, "Sin disponibilidad", "No hay recursos disponibles en una o más de las categorías solicitadas");
            }

        } catch (IllegalArgumentException ex) {
            mostrarAlerta(AlertType.WARNING, "Atención", ex.getMessage());
        } catch (Exception ex) {
            mostrarAlerta(AlertType.ERROR, "Error", "Ocurrió un error no esperado: " + ex.getMessage());
        }
    }

    @FXML
    private void cancelarReserva() {
        ReservaDTO seleccionada = tvMisReservas.getSelectionModel().getSelectedItem();

        if (seleccionada == null) {
            mostrarAlerta(AlertType.WARNING, "Selección requerida", "Seleccione una reserva de la tabla para cancelar");
            return;
        }

        try {
            boolean cancelada = reservaService.cancelarReserva(seleccionada.getIdReserva());
            if (cancelada) {
                mostrarAlerta(AlertType.INFORMATION, "Éxito", "Reserva cancelada correctamente");
                limpiarFormulario();
                cargarDatosDesdeSistema();
            }
        } catch (IllegalArgumentException ex) {
            mostrarAlerta(AlertType.WARNING, "No se puede cancelar", ex.getMessage());
        } catch (Exception ex) {
            mostrarAlerta(AlertType.ERROR, "Error al cancelar", ex.getMessage());
        }
    }

    // metodo auxiliar para construir alertas estándar
    private void mostrarAlerta(AlertType tipo, String titulo, String mensaje) {
        Alert alert = new Alert(tipo);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        alert.showAndWait();
    }

    // metodos necesarios para el autocompletado con IA
    private void onAutocompletarIA() {
        String frase = txtFrase.getText();

        if (frase == null || frase.isBlank()) {
            mostrarAlerta(AlertType.WARNING, "Atención", "Escriba una frase describiendo la reserva antes de usar la IA.");
            return;
        }

        // Reutilizamos las categorías que ya cargaste en el ListView
        List<String> categoriasDisponibles = lvCategorias.getItems().stream()
                .map(CategoriaDTO::getDescripcion)
                .collect(Collectors.toList());

        Task<ReservaExtraida> tarea = new Task<>() {
            @Override
            protected ReservaExtraida call() throws Exception {
                return ExtractorIAReserva.extraer(frase, categoriasDisponibles);
            }
        };

        tarea.setOnSucceeded(event -> {
            llenarFormularioConDatosIA(tarea.getValue());
            btnExtraerAI.setDisable(false);
        });

        tarea.setOnFailed(event -> {
            btnExtraerAI.setDisable(false);
            mostrarAlerta(AlertType.ERROR, "Error de IA",
                    "No se pudo interpretar la frase: " + tarea.getException().getMessage());
        });

        btnExtraerAI.setDisable(true);
        Thread hilo = new Thread(tarea);
        hilo.setDaemon(true);
        hilo.start();
    }

    private void llenarFormularioConDatosIA(ReservaExtraida datos) {
        if (datos.getActividad() != null && !datos.getActividad().isBlank()) {
            txtActividad.setText(datos.getActividad());
        }
        if (datos.getFecha() != null) {
            dpFecha.setValue(datos.getFecha());
        }
        if (datos.getHoraInicio() != null) {
            cbHoraInicio.setValue(horaMasCercana(cbHoraInicio.getItems(), datos.getHoraInicio()));
        }
        if (datos.getHoraFin() != null) {
            cbHoraFin.setValue(horaMasCercana(cbHoraFin.getItems(), datos.getHoraFin()));
        }

        // Seleccionamos (no reemplazamos) las categorías del ListView que coincidan
        lvCategorias.getSelectionModel().clearSelection();
        for (CategoriaDTO categoria : lvCategorias.getItems()) {
            boolean sugerida = datos.getCategoriasSugeridas().stream()
                    .anyMatch(txt -> txt.trim().equalsIgnoreCase(categoria.getDescripcion()));
            if (sugerida) {
                lvCategorias.getSelectionModel().select(categoria);
            }
        }
    }

    // como el combobox solo tiene horas cada 30 min, buscamos la más cercana a lo que devolvio la IA
    private LocalTime horaMasCercana(List<LocalTime> opciones, LocalTime horaIA) {
        return opciones.stream()
                .min((a, b) -> {
                    long difA = Math.abs(java.time.Duration.between(a, horaIA).toMinutes());
                    long difB = Math.abs(java.time.Duration.between(b, horaIA).toMinutes());
                    return Long.compare(difA, difB);
                })
                .orElse(null);
    }
}