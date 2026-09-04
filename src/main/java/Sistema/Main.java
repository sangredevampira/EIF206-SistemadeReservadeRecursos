package Sistema;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image; // <-- Importante: Importar la clase Image
import javafx.stage.Stage;
import Sistema.Controller.MainController;
import Sistema.util.SessionManager;

public class Main extends Application {

    @Override
    public void start(Stage escenarioPrincipal) throws Exception {

        //temporal
        SessionManager.getInstancia().iniciarSesion("111", "FUNCIONARIO");

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/Sistema/view/MainView.fxml"));

        Parent raiz = loader.load();

        String titulo = "SISTEMA DE RESERVAS - "
                + SessionManager.getInstancia().getIdUsuario()
                + " (" + SessionManager.getInstancia().getRol() + ")";

        // Configuración del ícono de la ventana
        try {
            Image icono = new Image(getClass().getResourceAsStream("/Sistema/images/folder.png"));
            escenarioPrincipal.getIcons().add(icono);
        } catch (Exception e) {
            System.out.println("No se pudo cargar el ícono de la aplicación: " + e.getMessage());
        }

        escenarioPrincipal.setTitle(titulo);
        escenarioPrincipal.setScene(new Scene(raiz, 1150, 720));
        escenarioPrincipal.setResizable(false);
        escenarioPrincipal.centerOnScreen();
        escenarioPrincipal.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}