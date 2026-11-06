package ui;

import Sistema.DTO.LoginResultadoDTO;
import Sistema.Red.TareaRed;
import Sistema.Service.IAuthService;
import Sistema.Service.Red.ProxyAuthService;
import Sistema.util.SessionManager;
import Sistema.util.VentanaUtil;

import javafx.application.Platform;
import javafx.event.ActionEvent;
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
    private boolean inicioSesionEnCurso;
    private long numeroIntento;

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
        btnCancelar.setOnAction(evento -> cancelarInicioSesion());
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
            dialogo.setResizable(false); // Tamano fijo; con dueño, JavaFX la centra sobre él
            dialogo.show();
        } catch (IOException e) {
            lblMensaje.setText("No se pudo abrir la ventana de cambio de clave.");
        }
    }

    private void manejarIngresar(ActionEvent evento) {
        if (inicioSesionEnCurso) return;

        String id = campoId.getText();
        String clave = campoClave.getText();
        long intentoActual = ++numeroIntento;

        establecerSolicitudEnCurso(true);
        lblMensaje.setText("Verificando credenciales...");

        TareaRed.ejecutar(
                () -> authService.login(id, clave),
                resultado -> completarInicioSesion(
                        intentoActual,
                        resultado,
                        evento
                ),
                error -> mostrarErrorInicioSesion(intentoActual, error)
        );
    }

    private void completarInicioSesion(
            long intento,
            LoginResultadoDTO resultado,
            ActionEvent evento
    ) {
        if (intento != numeroIntento || !inicioSesionEnCurso) return;

        try {
            // se guarda el ID que devuelve el servidor (como esta en la base), no el escrito
            SessionManager.getInstancia().iniciarSesion(
                    resultado.getIdUsuario(),
                    resultado.getRol().name(),
                    resultado.getFuncionario()
            );
            lblMensaje.setText("");

            if (!abrirVentanaPrincipal(evento)) {
                SessionManager.getInstancia().cerrarSesion();
                establecerSolicitudEnCurso(false);
            }
        } catch (RuntimeException error) {
            SessionManager.getInstancia().cerrarSesion();
            lblMensaje.setText(MENSAJE_ERROR_INESPERADO);
            establecerSolicitudEnCurso(false);
        }
    }

    private void mostrarErrorInicioSesion(long intento, Throwable error) {
        if (intento != numeroIntento || !inicioSesionEnCurso) return;

        if (error instanceof IllegalArgumentException
                && error.getMessage() != null
                && !error.getMessage().isBlank()) {
            lblMensaje.setText(error.getMessage());
        } else if (error instanceof IllegalStateException) {
            lblMensaje.setText(MENSAJE_SIN_CONEXION);
        } else {
            lblMensaje.setText(MENSAJE_ERROR_INESPERADO);
        }
        establecerSolicitudEnCurso(false);
    }

    private void establecerSolicitudEnCurso(boolean enCurso) {
        inicioSesionEnCurso = enCurso;
        campoId.setDisable(enCurso);
        campoClave.setDisable(enCurso);
        btnIngresar.setDisable(enCurso);
        btnCambiar.setDisable(enCurso);
    }

    private void cancelarInicioSesion() {
        numeroIntento++;
        establecerSolicitudEnCurso(false);
        salirAplicacion();
    }

    void salirAplicacion() {
        Platform.exit();
    }

    boolean abrirVentanaPrincipal(ActionEvent evento) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/Sistema/view/MainView.fxml"));
            loader.setController(new MainController());
            Parent raiz = loader.load();

            String titulo = "SISTEMA DE RESERVAS - "
                    + SessionManager.getInstancia().getIdUsuario()
                    + " (" + SessionManager.getInstancia().getRol() + ")";

            Stage escenario = (Stage) ((Node) evento.getSource()).getScene().getWindow();
            escenario.setTitle(titulo);
            escenario.setScene(VentanaUtil.escenaAjustada(raiz, 1400, 720));

            // Tamaño fijo y centrado exacto en la pantalla
            VentanaUtil.fijarYCentrar(escenario);
            escenario.show();
            return true;

        } catch (IOException e) {
            lblMensaje.setText("No se pudo abrir la ventana principal.");
            return false;
        }
    }
}
