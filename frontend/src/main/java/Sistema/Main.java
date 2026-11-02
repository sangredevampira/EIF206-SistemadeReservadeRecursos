package Sistema;

import Sistema.Red.ConexionInicialRed;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import ui.LoginController;

import java.io.IOException;

public class Main extends Application {

    @Override
    public void start(Stage escenarioPrincipal) {

        ConexionInicialRed.iniciar(
                escenarioPrincipal,
                () -> mostrarLogin(escenarioPrincipal)
        );
    }

    private void mostrarLogin(Stage escenarioPrincipal) {

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("view/LoginView.fxml"));
            loader.setController(new LoginController());
            Parent raiz = loader.load();

            escenarioPrincipal.setTitle("SISTEMA DE RESERVAS");
            escenarioPrincipal.setScene(new Scene(raiz, 400, 320));
            escenarioPrincipal.getIcons().add(new Image(getClass().getResourceAsStream("/Sistema/images/folder.png")));
            escenarioPrincipal.setResizable(false);
            escenarioPrincipal.show();
            escenarioPrincipal.centerOnScreen();

        } catch (IOException e) {
            throw new IllegalStateException(
                    "No se pudo cargar la pantalla de inicio de sesión.",
                    e
            );
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
