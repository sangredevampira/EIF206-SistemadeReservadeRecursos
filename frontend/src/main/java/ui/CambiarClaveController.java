package ui;

import Sistema.DTO.ResultadoOperacionDTO;
import Sistema.Red.TareaRed;
import Sistema.Service.IAuthService;
import Sistema.Service.Red.ProxyAuthService;
import Sistema.util.SessionManager;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;

import java.util.Objects;

// Cambio de clave por medio de ProxyAuthService (AUTH_CAMBIAR_CLAVE), con o sin sesion.
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
    private boolean cambioEnCurso;
    private long numeroIntento;
    private Window ventanaObservada;

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
        btnCancelar.setOnAction(this::manejarCancelar);
    }

    // de paquete para las pruebas: el ID recibido desde el login o, si no hay, el de la sesion
    String obtenerIdObjetivo() {
        return (idUsuarioObjetivo != null) ? idUsuarioObjetivo : SessionManager.getInstancia().getIdUsuario();
    }

    private void manejarConfirmar(ActionEvent evento) {
        if (cambioEnCurso) return;

        observarCierreVentana(evento);
        String id = obtenerIdObjetivo();
        String actual = campoClaveActual.getText();
        String nueva = campoClaveNueva.getText();
        String confirmacion = campoClaveNuevaConfirmacion.getText();
        long intentoActual = ++numeroIntento;

        establecerSolicitudEnCurso(true);
        lblMensaje.setText("Actualizando clave...");

        TareaRed.ejecutar(
                () -> authService.cambiarClave(id, actual, nueva, confirmacion),
                resultado -> completarCambio(intentoActual, resultado, evento),
                error -> mostrarErrorCambio(intentoActual, error)
        );
    }

    private void completarCambio(
            long intento,
            ResultadoOperacionDTO resultado,
            ActionEvent evento
    ) {
        if (!esIntentoVigente(intento)) return;

        if (resultado == null) {
            lblMensaje.setText(MENSAJE_ERROR_INESPERADO);
            establecerSolicitudEnCurso(false);
            return;
        }

        if (!resultado.isExito()) {
            String mensaje = resultado.getMensaje();
            lblMensaje.setText(mensaje == null || mensaje.isBlank()
                    ? MENSAJE_ERROR_INESPERADO
                    : mensaje);
            establecerSolicitudEnCurso(false);
            return;
        }

        invalidarSolicitud();
        cerrarVentana(evento);
    }

    private void mostrarErrorCambio(long intento, Throwable error) {
        if (!esIntentoVigente(intento)) return;

        if (error instanceof IllegalArgumentException
                && error.getMessage() != null
                && !error.getMessage().isBlank()) {
            lblMensaje.setText(error.getMessage());
        } else if (error instanceof IllegalStateException) {
            // Si hubo un timeout, el servidor pudo haber aplicado el cambio aunque
            // el cliente no recibiera la confirmación.
            lblMensaje.setText(MENSAJE_SIN_CONEXION);
        } else {
            lblMensaje.setText(MENSAJE_ERROR_INESPERADO);
        }
        establecerSolicitudEnCurso(false);
    }

    private boolean esIntentoVigente(long intento) {
        return cambioEnCurso && intento == numeroIntento;
    }

    private void establecerSolicitudEnCurso(boolean enCurso) {
        cambioEnCurso = enCurso;
        campoClaveActual.setDisable(enCurso);
        campoClaveNueva.setDisable(enCurso);
        campoClaveNuevaConfirmacion.setDisable(enCurso);
        btnConfirmar.setDisable(enCurso);
    }

    private void manejarCancelar(ActionEvent evento) {
        invalidarSolicitud();
        cerrarVentana(evento);
    }

    private void invalidarSolicitud() {
        numeroIntento++;
        establecerSolicitudEnCurso(false);
    }

    void notificarVentanaCerrada() {
        invalidarSolicitud();
    }

    private void observarCierreVentana(ActionEvent evento) {
        if (!(evento.getSource() instanceof Node origen)
                || origen.getScene() == null
                || origen.getScene().getWindow() == null) {
            return;
        }

        Window ventana = origen.getScene().getWindow();
        if (ventana == ventanaObservada) return;

        ventanaObservada = ventana;
        ventana.addEventHandler(
                WindowEvent.WINDOW_HIDDEN,
                ocultada -> notificarVentanaCerrada()
        );
    }

    void cerrarVentana(ActionEvent evento) {
        Stage escenario = (Stage) ((Node) evento.getSource()).getScene().getWindow();
        escenario.close();
    }
}
