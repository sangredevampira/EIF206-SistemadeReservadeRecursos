package Sistema.util;

import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;

/**
 * Utilidades de presentación para las ventanas: tamaño fijo y centrado exacto.
 */
public final class VentanaUtil {

    private static final double MAX_ANCHO = 0.98;
    private static final double MAX_ALTO = 0.95;

    private VentanaUtil() {
    }

    /**
     * Deja la ventana sin redimensionar ni maximizar y la centra en la pantalla.
     * Debe llamarse después de setScene y antes de show(). Si la ventana ya
     * estaba visible (cambio de escena), se ajusta al tamaño de la nueva escena
     * y se centra de inmediato.
     */
    public static void fijarYCentrar(Stage stage) {
        stage.setResizable(false);
        stage.setMaximized(false);
        stage.setOnShown(evento -> centrar(stage));

        if (stage.isShowing()) {
            stage.sizeToScene();
            centrar(stage);
            Platform.runLater(() -> centrar(stage));
        }
    }

    /**
     * Crea la escena con el tamaño pedido, limitado al 98 % del ancho y al 95 %
     * del alto del área visible de la pantalla principal.
     */
    public static Scene escenaAjustada(Parent raiz, double ancho, double alto) {
        Rectangle2D area = Screen.getPrimary().getVisualBounds();
        return new Scene(raiz,
                Math.min(ancho, area.getWidth() * MAX_ANCHO),
                Math.min(alto, area.getHeight() * MAX_ALTO));
    }

    private static void centrar(Stage stage) {
        Rectangle2D area = Screen.getPrimary().getVisualBounds();
        stage.setX(area.getMinX() + (area.getWidth() - stage.getWidth()) / 2);
        stage.setY(area.getMinY() + (area.getHeight() - stage.getHeight()) / 2);
    }
}
