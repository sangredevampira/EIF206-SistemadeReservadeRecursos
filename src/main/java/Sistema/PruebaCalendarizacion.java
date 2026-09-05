package Sistema;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class PruebaCalendarizacion extends Application {

    @Override
    public void start(Stage stage) throws Exception {

        FXMLLoader loader = new FXMLLoader(
                getClass().getResource(
                        "/Sistema/view/CalendarizacionRecursosView.fxml"
                )
        );

        Parent vista = loader.load();

        Scene scene = new Scene(vista);

        stage.setTitle(
                "Prueba - Calendarización de recursos"
        );

        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}