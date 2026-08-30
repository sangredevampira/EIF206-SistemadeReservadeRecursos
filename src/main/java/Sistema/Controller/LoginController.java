package Sistema.Controller;

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
        btnCambiar.setOnAction(e -> System.out.println("Cambiar clave (pendiente)"));
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
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/Sistema/view/mainView.fxml"));
            loader.setController(new MainController());
            Parent raiz = loader.load();

            String titulo = "SISTEMA DE RESERVAS - "
                    + SessionManager.getInstancia().getIdUsuario()
                    + " (" + SessionManager.getInstancia().getRol() + ")";

            Stage escenario = (Stage) ((Node) evento.getSource()).getScene().getWindow();
            escenario.setTitle(titulo);
            escenario.setScene(new Scene(raiz, 1150, 720));
            escenario.show();
        } catch (IOException e) {
            lblMensaje.setText("No se pudo abrir la ventana principal.");
        }
    }
}
