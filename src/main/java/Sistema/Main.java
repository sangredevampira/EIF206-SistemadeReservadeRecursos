package Sistema;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import Sistema.controller.MainController;
import Sistema.util.SessionManager;

public class Main extends Application {

    @Override
    public void start(Stage escenarioPrincipal) throws Exception {

        //temporal
        SessionManager.getInstancia().iniciarSesion("111", "FUNCIONARIO");

        FXMLLoader loader = new FXMLLoader(getClass().getResource("view/MainView.fxml"));
        loader.setController(new MainController());
        Parent raiz = loader.load();

        String titulo = "SISTEMA DE RESERVAS - "
                + SessionManager.getInstancia().getIdUsuario()
                + " (" + SessionManager.getInstancia().getRol() + ")";

        escenarioPrincipal.setTitle(titulo);
        escenarioPrincipal.setScene(new Scene(raiz, 1150, 720));
        escenarioPrincipal.show();
    }
    public static void main(String[] args) {
        launch(args);
    }

}
