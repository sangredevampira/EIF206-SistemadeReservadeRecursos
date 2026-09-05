package Sistema;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class PruebaProgramacionActividades extends Application {

    @Override
    public void start(Stage stage) throws Exception {

        FXMLLoader loader = new FXMLLoader(
                getClass().getResource(
                        "/Sistema/view/ProgramacionActividadesView.fxml"
                )
        );

        Scene scene = new Scene(loader.load());

        stage.setTitle("Programación semanal de actividades");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
