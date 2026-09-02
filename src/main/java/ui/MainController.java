package ui;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import Sistema.util.SessionManager;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.Node;
import javafx.stage.Stage;
import javafx.stage.Modality;
import java.io.IOException;

public class MainController {

    @FXML private TabPane tabPanePrincipal;
    @FXML private Label lblUsuarioActivo;

    @FXML private Tab tabFuncionarios;
    @FXML private Tab tabCategorias;
    @FXML private Tab tabRecursos;
    @FXML private Tab tabReservas;
    @FXML private Tab tabCalendarizacion;
    @FXML private Tab tabActividades;
    @FXML private Tab tabEstadisticas;

    @FXML private Button btnCambiarClave;
    @FXML private Button btnCerrarSesion;

    @FXML
    private void initialize() {
        mostrarUsuarioActivo();
        ajustarPestanasSegunRol();

        btnCambiarClave.setOnAction(this::abrirCambiarClave);
        btnCerrarSesion.setOnAction(this::manejarCerrarSesion);
    }

    private void mostrarUsuarioActivo() {
        String idUsuario = SessionManager.getInstancia().getIdUsuario();
        String rol = SessionManager.getInstancia().getRol();
        lblUsuarioActivo.setText(idUsuario + " (" + rol + ")");
    }

    private void abrirCambiarClave(javafx.event.ActionEvent evento) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/Sistema/view/CambiarClaveView.fxml"));
            loader.setController(new CambiarClaveController());
            Parent raiz = loader.load();

            Stage dialogo = new Stage();
            dialogo.setTitle("Cambiar Clave");
            dialogo.initModality(Modality.APPLICATION_MODAL);
            dialogo.initOwner(((Node) evento.getSource()).getScene().getWindow());
            dialogo.setScene(new Scene(raiz));
            dialogo.showAndWait();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void manejarCerrarSesion(javafx.event.ActionEvent evento) {
        SessionManager.getInstancia().cerrarSesion();

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/Sistema/view/LoginView.fxml"));
            loader.setController(new LoginController());
            Parent raiz = loader.load();

            Stage escenario = (Stage) ((Node) evento.getSource()).getScene().getWindow();
            escenario.setTitle("SISTEMA DE RESERVAS");
            escenario.setScene(new Scene(raiz, 400, 320));
            escenario.show();
        } catch (IOException e) {
            System.out.println("No se pudo volver a la pantalla de login: " + e.getMessage());
        }
    }

    private void ajustarPestanasSegunRol() {
        boolean esAdmin = SessionManager.getInstancia().esAdministrador();

        tabPanePrincipal.getTabs().removeIf(tab ->
                !esAdmin && (tab == tabFuncionarios || tab == tabCategorias || tab == tabRecursos));

        tabPanePrincipal.getTabs().removeIf(tab ->
                esAdmin && tab == tabReservas);
    }
}
