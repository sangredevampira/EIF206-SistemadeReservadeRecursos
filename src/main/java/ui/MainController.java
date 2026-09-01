package ui;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import Sistema.util.SessionManager;

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

        btnCambiarClave.setOnAction(e -> System.out.println("Cambiar clave (pendiente)"));
        btnCerrarSesion.setOnAction(e -> System.out.println("Cerrar sesión (pendiente)"));
    }

    private void mostrarUsuarioActivo() {
        String idUsuario = SessionManager.getInstancia().getIdUsuario();
        String rol = SessionManager.getInstancia().getRol();
        lblUsuarioActivo.setText(idUsuario + " (" + rol + ")");
    }

    private void ajustarPestanasSegunRol() {
        boolean esAdmin = SessionManager.getInstancia().esAdministrador();

        tabPanePrincipal.getTabs().removeIf(tab ->
                !esAdmin && (tab == tabFuncionarios || tab == tabCategorias || tab == tabRecursos));

        tabPanePrincipal.getTabs().removeIf(tab ->
                esAdmin && tab == tabReservas);
    }
}
