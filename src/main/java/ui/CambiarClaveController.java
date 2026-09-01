package ui;

import Sistema.Logica.UsuarioLogica;
import Sistema.Service.AuthService;
import Sistema.util.SessionManager;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.stage.Stage;

public class CambiarClaveController {

    @FXML private PasswordField campoClaveActual;
    @FXML private PasswordField campoClaveNueva;
    @FXML private PasswordField campoClaveNuevaConfirmacion;
    @FXML private Label lblMensaje;

    @FXML private Button btnConfirmar;
    @FXML private Button btnCancelar;

    private final AuthService authService = new AuthService();

    @FXML
    private void initialize() {
        btnConfirmar.setOnAction(this::manejarConfirmar);
        btnCancelar.setOnAction(this::cerrarVentana);
    }

    private void manejarConfirmar(javafx.event.ActionEvent evento) {
        String id = SessionManager.getInstancia().getIdUsuario();
        String claveActual = campoClaveActual.getText();
        String claveNueva = campoClaveNueva.getText();
        String confirmacion = campoClaveNuevaConfirmacion.getText();

        UsuarioLogica.ResultadoCambioClave resultado =
                authService.cambiarClave(id, claveActual, claveNueva, confirmacion);

        if (resultado.isExitoso()) {
            cerrarVentana(evento);
        } else {
            lblMensaje.setText(resultado.getMensajeError());
        }
    }

    private void cerrarVentana(javafx.event.ActionEvent evento) {
        Stage escenario = (Stage) ((Node) evento.getSource()).getScene().getWindow();
        escenario.close();
    }
}
