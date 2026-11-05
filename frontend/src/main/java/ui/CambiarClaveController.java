package ui;

import Sistema.DTO.ResultadoOperacionDTO;
import Sistema.Service.IAuthService;
import Sistema.Service.Red.ProxyAuthService;
import Sistema.util.SessionManager;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.stage.Stage;

import java.util.Objects;

// Cambio de clave por medio de ProxyAuthService (AUTH_CAMBIAR_CLAVE), con o sin sesion.
// La llamada es directa, sin TareaRed (decision 13 del equipo).
public class CambiarClaveController {

    static final String MENSAJE_SIN_CONEXION =
            "No hay comunicación con el servidor; no se pudo confirmar el cambio. "
                    + "Intente de nuevo y, si sigue, reinicie la aplicación.";
    static final String MENSAJE_ERROR_INESPERADO = "No se pudo actualizar la clave. Intente de nuevo.";

    @FXML private Label lblUsuarioObjetivo;
    @FXML private PasswordField campoClaveActual;
    @FXML private PasswordField campoClaveNueva;
    @FXML private PasswordField campoClaveNuevaConfirmacion;
    @FXML private Label lblMensaje;

    @FXML private Button btnConfirmar;
    @FXML private Button btnCancelar;

    private final IAuthService authService;

    private String idUsuarioObjetivo;

    // Lo usan LoginController y MainController (new CambiarClaveController())
    public CambiarClaveController() {
        this(new ProxyAuthService());
    }

    // Para las pruebas: permite usar un IAuthService falso sin red
    CambiarClaveController(IAuthService authService) {
        this.authService = Objects.requireNonNull(authService);
    }

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

    // de paquete para las pruebas: el ID recibido desde el login o, si no hay, el de la sesion
    String obtenerIdObjetivo() {
        return (idUsuarioObjetivo != null) ? idUsuarioObjetivo : SessionManager.getInstancia().getIdUsuario();
    }

    private void manejarConfirmar(javafx.event.ActionEvent evento) {
        String mensaje = cambiarClave(
                obtenerIdObjetivo(),
                campoClaveActual.getText(),
                campoClaveNueva.getText(),
                campoClaveNuevaConfirmacion.getText());

        if (mensaje == null) {
            cerrarVentana(evento);
        } else {
            lblMensaje.setText(mensaje);
        }
    }

    /**
     * Pide el cambio de clave al servidor. No usa controles de JavaFX, para poder
     * probarlo sin abrir ventanas.
     *
     * @return null si el cambio salio bien; si no, el mensaje que se muestra en lblMensaje
     */
    String cambiarClave(String id, String actual, String nueva, String confirmacion) {
        try {
            // un rechazo del servidor llega como fallo(mensaje) (ApoyoProxies.resultado)
            ResultadoOperacionDTO resultado = authService.cambiarClave(id, actual, nueva, confirmacion);
            return resultado.isExito() ? null : resultado.getMensaje();
        } catch (IllegalStateException e) {
            // problema de red o respuesta invalida; si fue por tiempo limite, el servidor
            // pudo haber cambiado la clave: por eso "no se pudo confirmar"
            return MENSAJE_SIN_CONEXION;
        } catch (RuntimeException e) {
            return MENSAJE_ERROR_INESPERADO;
        }
    }

    private void cerrarVentana(javafx.event.ActionEvent evento) {
        Stage escenario = (Stage) ((Node) evento.getSource()).getScene().getWindow();
        escenario.close();
    }
}
