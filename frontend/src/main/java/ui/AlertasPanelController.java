
package ui;

import Sistema.DTO.ReservaDTO;
import Sistema.DTO.RecursoDTO;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

import java.util.stream.Collectors;

public class AlertasPanelController {

    @FXML private TableView<ReservaDTO> tablaAlertas;
    @FXML private TableColumn<ReservaDTO, String> colActividad;
    @FXML private TableColumn<ReservaDTO, Void> colVer;
    @FXML private TableColumn<ReservaDTO, Void> colDescartar;

    private final ObservableList<ReservaDTO> alertas =
            FXCollections.observableArrayList();

    @FXML
    private void initialize() {

        tablaAlertas.setItems(alertas);

        colActividad.setCellValueFactory(datos ->
                new ReadOnlyStringWrapper(
                        datos.getValue().getNombreActividad()
                )
        );

        configurarColumnaVer();
        configurarColumnaDescartar();
    }

    private void configurarColumnaVer() {

        colVer.setCellFactory(columna -> new TableCell<>() {

            private final Button boton = crearBoton(
                    "📂", "#2563EB", "Ver detalle"
            );

            {
                boton.setOnAction(evento -> {
                    ReservaDTO reserva = getTableRow().getItem();

                    if (reserva != null) {
                        mostrarDetalle(reserva);
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean vacia) {
                super.updateItem(item, vacia);
                setGraphic(vacia ? null : boton);
            }
        });
    }

    private void configurarColumnaDescartar() {

        colDescartar.setCellFactory(columna -> new TableCell<>() {

            private final Button boton = crearBoton(
                    "⊖", "#DC2626", "Descartar alerta"
            );

            {
                boton.setOnAction(evento -> {
                    ReservaDTO reserva = getTableRow().getItem();

                    if (reserva != null) {
                        confirmarDescarte(reserva);
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean vacia) {
                super.updateItem(item, vacia);
                setGraphic(vacia ? null : boton);
            }
        });
    }

    private Button crearBoton(
            String texto, String color, String ayuda
    ) {
        Button boton = new Button(texto);

        boton.setMinSize(25, 23);
        boton.setPrefSize(25, 23);

        boton.setStyle(
                "-fx-background-color: transparent;" +
                        "-fx-text-fill: " + color + ";" +
                        "-fx-font-size: 14px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-padding: 0;"
        );

        boton.setTooltip(new Tooltip(ayuda));
        return boton;
    }

    public void agregarAlerta(ReservaDTO reserva) {

        if (reserva == null) {
            return;
        }

        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> agregarAlerta(reserva));
            return;
        }

        alertas.add(0, reserva);
    }

    private void mostrarDetalle(ReservaDTO reserva) {

        GridPane contenido = new GridPane();
        contenido.setHgap(12);
        contenido.setVgap(9);
        contenido.setPadding(new Insets(10));

        agregarDato(contenido, 0, "ID:", reserva.getIdReserva());
        agregarDato(contenido, 1, "Funcionario:",
                reserva.getNombreFuncionario());
        agregarDato(contenido, 2, "Fecha:",
                reserva.getFecha() == null
                        ? "" : reserva.getFecha().toString());
        agregarDato(contenido, 3, "Desde:",
                reserva.getHoraInicio() == null
                        ? "" : reserva.getHoraInicio().toString());
        agregarDato(contenido, 4, "Hasta:",
                reserva.getHoraFin() == null
                        ? "" : reserva.getHoraFin().toString());

        String recursos = "Sin recursos registrados";

        if (reserva.getRecursosReservados() != null
                && !reserva.getRecursosReservados().isEmpty()) {

            recursos = reserva.getRecursosReservados()
                    .stream()
                    .filter(r -> r != null)
                    .map(RecursoDTO::getDescripcion)
                    .collect(Collectors.joining("\n"));
        }

        Label lblRecursos = new Label("Recursos:");
        contenido.add(lblRecursos, 0, 5);

        TextArea txtRecursos = new TextArea(recursos);
        txtRecursos.setEditable(false);
        txtRecursos.setWrapText(true);
        txtRecursos.setPrefSize(230, 85);

        contenido.add(txtRecursos, 1, 5);

        Alert detalle = new Alert(Alert.AlertType.INFORMATION);
        detalle.setTitle("Detalle de la reserva");
        detalle.setHeaderText(reserva.getNombreActividad());
        detalle.getDialogPane().setContent(contenido);
        detalle.showAndWait();
    }

    private void agregarDato(
            GridPane panel, int fila, String titulo, String valor
    ) {
        panel.add(new Label(titulo), 0, fila);
        panel.add(new Label(valor == null ? "" : valor), 1, fila);
    }

    private void confirmarDescarte(ReservaDTO reserva) {

        ButtonType btnSi = new ButtonType(
                "Sí", ButtonBar.ButtonData.YES
        );

        ButtonType btnNo = new ButtonType(
                "No", ButtonBar.ButtonData.NO
        );

        Alert confirmacion = new Alert(
                Alert.AlertType.CONFIRMATION
        );

        confirmacion.setTitle("Descartar alerta");
        confirmacion.setHeaderText("¿Descartar esta notificación?");
        confirmacion.setContentText(
                reserva.getNombreActividad()
        );

        confirmacion.getButtonTypes().setAll(btnSi, btnNo);

        confirmacion.showAndWait().ifPresent(respuesta -> {
            if (respuesta == btnSi) {
                alertas.remove(reserva);
            }
        });
    }

    public void limpiarAlertas() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::limpiarAlertas);
            return;
        }

        alertas.clear();
    }
}
