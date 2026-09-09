package Sistema;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import ui.LoginController;

public class Main extends Application {

    @Override
    public void start(Stage escenarioPrincipal) throws Exception {

        FXMLLoader loader = new FXMLLoader(getClass().getResource("view/LoginView.fxml"));
        loader.setController(new LoginController());
        Parent raiz = loader.load();

        escenarioPrincipal.setTitle("SISTEMA DE RESERVAS");
        escenarioPrincipal.setScene(new Scene(raiz, 400, 320));
        escenarioPrincipal.getIcons().add(new Image(getClass().getResourceAsStream("/Sistema/images/folder.png")));
        escenarioPrincipal.setResizable(false);
        escenarioPrincipal.show();
        escenarioPrincipal.centerOnScreen();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

