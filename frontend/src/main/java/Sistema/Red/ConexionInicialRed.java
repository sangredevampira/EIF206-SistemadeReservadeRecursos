
package Sistema.Red;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;

import java.util.Objects;

public final class ConexionInicialRed {

    private ConexionInicialRed() {
        // Clase de utilidad.
    }

    /**
     * Intenta establecer la conexion con el servidor.
     * La operacion se ejecuta fuera del hilo de JavaFX.
     *
     * @param ventana ventana principal
     * @param alConectar accion a ejecutar al conectar
     */
    public static void iniciar(
            Stage ventana,
            Runnable alConectar
    ) {

        Objects.requireNonNull(
                alConectar,
                "Debe indicar la accion posterior a la conexion."
        );

        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(
                    () -> iniciar(ventana, alConectar)
            );
            return;
        }

        Task<Void> tarea = new Task<>() {

            @Override
            protected Void call() throws Exception {

                conectar(ConfiguracionRed.cargar());

                return null;
            }
        };

        tarea.setOnSucceeded(evento -> {
            alConectar.run();
        });

        tarea.setOnFailed(evento -> {
            mostrarError(
                    ventana,
                    alConectar,
                    tarea.getException()
            );
        });

        Thread hilo = new Thread(
                tarea,
                "ConexionInicialRed"
        );

        hilo.setDaemon(true);
        hilo.start();
    }

    static void conectar(ConfiguracionRed config)
            throws java.io.IOException {

        Objects.requireNonNull(
                config,
                "La configuración no puede ser nula."
        );

        ClienteRed.getInstancia().conectar(
                config.getHost(),
                config.getPuerto()
        );
    }

    private static void mostrarError(
            Stage ventana,
            Runnable alConectar,
            Throwable error
    ) {

        ButtonType reintentar = new ButtonType(
                "Reintentar",
                ButtonBar.ButtonData.OK_DONE
        );

        ButtonType salir = new ButtonType(
                "Salir",
                ButtonBar.ButtonData.CANCEL_CLOSE
        );

        Alert alerta = new Alert(Alert.AlertType.ERROR);

        alerta.setTitle("Error de conexión");

        alerta.setHeaderText(
                "No se pudo conectar con el servidor."
        );

        alerta.setContentText(
                "Comprueba que el servidor esté encendido "
                        + "y que la dirección y el puerto sean correctos."
        );

        alerta.getButtonTypes().setAll(
                reintentar,
                salir
        );

        if (ventana != null) {
            alerta.initOwner(ventana);
        }

        ButtonType respuesta = alerta
                .showAndWait()
                .orElse(salir);

        if (respuesta == reintentar) {

            iniciar(ventana, alConectar);

        } else {

            ClienteRed.getInstancia().cerrar();
            Platform.exit();
        }
    }
}
