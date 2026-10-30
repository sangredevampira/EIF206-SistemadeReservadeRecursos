package ui;

import Sistema.DTO.ResultadoOperacionDTO;
import Sistema.Service.AuthService;
import Sistema.util.SessionManager;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.stage.Stage;

public class CambiarClaveController {

    @FXML private Label lblUsuarioObjetivo;
    @FXML private PasswordField campoClaveActual;
    @FXML private PasswordField campoClaveNueva;
    @FXML private PasswordField campoClaveNuevaConfirmacion;
    @FXML private Label lblMensaje;

    @FXML private Button btnConfirmar;
    @FXML private Button btnCancelar;

    private final AuthService authService = new AuthService();

    private String idUsuarioObjetivo;

    public void setIdUsuarioObjetivo(String idUsuarioObjetivo) {
        this.idUsuarioObjetivo = idUsuarioObjetivo;
    }

    @FXML
    private void initialize() {
        String id = obtenerIdObjetivo();
        lblUsuarioObjetivo.setText("Usuario: " + (id == null || id.isBlank() ? "(ninguno)" : id));

        btnConfirmar.setOnAction(this::manejarConfirmar);
        btnCancelar.setOnAction(this::cerrarVentana);
    }

    private String obtenerIdObjetivo() {
        return (idUsuarioObjetivo != null) ? idUsuarioObjetivo : SessionManager.getInstancia().getIdUsuario();
    }

    private void manejarConfirmar(javafx.event.ActionEvent evento) {
        String id = obtenerIdObjetivo();
        String claveActual = campoClaveActual.getText();
        String claveNueva = campoClaveNueva.getText();
        String confirmacion = campoClaveNuevaConfirmacion.getText();

        try {
            ResultadoOperacionDTO resultado =
                    authService.cambiarClave(id, claveActual, claveNueva, confirmacion);

            if (resultado.isExito()) {
                cerrarVentana(evento);
            } else {
                lblMensaje.setText(resultado.getMensaje());
            }
        } catch (RuntimeException e) {
            lblMensaje.setText("No se pudo actualizar la clave. Intente de nuevo.");
        }
    }

    private void cerrarVentana(javafx.event.ActionEvent evento) {
        Stage escenario = (Stage) ((Node) evento.getSource()).getScene().getWindow();
        escenario.close();
    }
}
