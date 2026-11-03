package ui;

import Sistema.DTO.LoginResultadoDTO;
import Sistema.Service.IAuthService;
import Sistema.Service.Red.ProxyAuthService;
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
import java.util.Objects;

// Conecta la pantalla de login con el servidor por medio de ProxyAuthService (AUTH_LOGIN).
// La llamada es directa, sin TareaRed (decision 13 del equipo).

public class LoginController {

    static final String MENSAJE_SIN_CONEXION =
            "No hay comunicación con el servidor; su clave no fue revisada. "
                    + "Intente de nuevo y, si sigue, reinicie la aplicación.";
    static final String MENSAJE_ERROR_INESPERADO = "No se pudo verificar el usuario. Intente de nuevo.";

    @FXML private TextField campoId;
    @FXML private PasswordField campoClave;
    @FXML private Label lblMensaje;
    @FXML private Button btnIngresar;
    @FXML private Button btnCancelar;
    @FXML private Button btnCambiar;

    private final IAuthService authService;

    // Lo usan Main y MainController (loader.setController(new LoginController()))
    public LoginController() {
        this(new ProxyAuthService());
    }

    // Para las pruebas: permite usar un IAuthService falso sin red
    LoginController(IAuthService authService) {
        this.authService = Objects.requireNonNull(authService);
    }

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
        String mensaje = iniciarSesion(campoId.getText(), campoClave.getText());
        if (mensaje == null) {
            lblMensaje.setText("");
            abrirVentanaPrincipal(evento);
        } else {
            lblMensaje.setText(mensaje);
        }
    }

    /**
     * Pide el login al servidor y, si sale bien, inicia la sesion local (SessionManager).
     * No usa controles de JavaFX, para poder probarlo sin abrir ventanas.
     *
     * @return null si el login salio bien; si no, el mensaje que se muestra en lblMensaje
     */
    String iniciarSesion(String id, String clave) {
        try {
            LoginResultadoDTO resultado = authService.login(id, clave);

            // se guarda el ID que devuelve el servidor (como esta en la base), no el escrito
            SessionManager.getInstancia().iniciarSesion(
                    resultado.getIdUsuario(),
                    resultado.getRol().name(),
                    resultado.getFuncionario()
            );
            return null;
        } catch (IllegalArgumentException e) {
            // el servidor respondio exito=false: regla de negocio ("ID o clave incorrectos.", etc.)
            return e.getMessage();
        } catch (IllegalStateException e) {
            // problema de red o respuesta invalida: la clave no llego a revisarse
            return MENSAJE_SIN_CONEXION;
        } catch (RuntimeException e) {
            return MENSAJE_ERROR_INESPERADO;
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
            escenario.setScene(new Scene(raiz, 1400, 720));

            // Bloquea el cambio de tamaño
            escenario.setResizable(true);

            // Muestra y centra exactamente en la pantalla
            escenario.show();
            escenario.centerOnScreen();

        } catch (IOException e) {
            e.printStackTrace();
            lblMensaje.setText("No se pudo abrir la ventana principal.");
        }
    }
}