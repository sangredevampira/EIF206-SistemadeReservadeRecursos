package ui;

import Sistema.Logica.UsuarioLogica;
import Sistema.Service.AuthService;
import Sistema.util.SessionManager;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;

//conecta los botones de la futura pantalla con AuthService.

public class LoginController {

    @FXML private TextField campoId;
    @FXML private PasswordField campoClave;
    @FXML private Label lblMensaje;
    @FXML private Button btnIngresar;
    @FXML private Button btnCancelar;
    @FXML private Button btnCambiar;

    private final AuthService authService = new AuthService();

    @FXML
    private void initialize() {
        btnIngresar.setOnAction(this::manejarIngresar);
        btnCancelar.setOnAction(e -> Platform.exit());
        btnCambiar.setOnAction(this::abrirCambiarClave);
    }

    private void abrirCambiarClave(javafx.event.ActionEvent evento) {
        String id = campoId.getText();
        if (id == null || id.isBlank()) {
            lblMensaje.setText("Escribí tu ID de usuario antes de cambiar la clave.");
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/Sistema/view/CambiarClaveView.fxml"));
            CambiarClaveController controller = new CambiarClaveController();
            controller.setIdUsuarioObjetivo(id);
            loader.setController(controller);
            Parent raiz = loader.load();

            Stage dialogo = new Stage();
            dialogo.setTitle("Cambiar Clave");
            dialogo.initModality(Modality.APPLICATION_MODAL);
            dialogo.initOwner(((Node) evento.getSource()).getScene().getWindow());
            dialogo.setScene(new Scene(raiz));
            dialogo.setResizable(false); // Tamano fijo para la modal

            // Mostrar y centrar la ventana modal
            dialogo.show();
            dialogo.centerOnScreen();
        } catch (IOException e) {
            lblMensaje.setText("No se pudo abrir la ventana de cambio de clave.");
        }
    }

    private void manejarIngresar(javafx.event.ActionEvent evento) {
        String id = campoId.getText();
        String clave = campoClave.getText();

        UsuarioLogica.ResultadoLogin resultado = authService.login(id, clave);

        if (resultado.isExitoso()) {
            lblMensaje.setText("");
            abrirVentanaPrincipal(evento);
        } else {
            lblMensaje.setText(resultado.getMensajeError());
        }
    }

    private void abrirVentanaPrincipal(javafx.event.ActionEvent evento) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/Sistema/view/MainView.fxml"));
            loader.setController(new MainController());
            Parent raiz = loader.load();

            String titulo = "SISTEMA DE RESERVAS - "
                    + SessionManager.getInstancia().getIdUsuario()
                    + " (" + SessionManager.getInstancia().getRol() + ")";

            Stage escenario = (Stage) ((Node) evento.getSource()).getScene().getWindow();
            escenario.setTitle(titulo);
            escenario.setScene(new Scene(raiz, 1150, 720));

            // Bloquea el cambio de tamaño
            escenario.setResizable(false);

            // Muestra y centra exactamente en la pantalla
            escenario.show();
            escenario.centerOnScreen();

        } catch (IOException e) {
            e.printStackTrace();
            lblMensaje.setText("No se pudo abrir la ventana principal.");
        }
    }
}