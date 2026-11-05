
package Sistema.Red;

import javafx.concurrent.Task;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

public final class TareaRed {

    private TareaRed() {
        // Clase de utilidad: no se instancia.
    }

    /**
     * Ejecuta una operación en segundo plano.
     *
     * Los callbacks de éxito y error se ejecutan
     * en el hilo de JavaFX.
     *
     * @param operacion  operación que se ejecutará
     * @param alExito    callback al terminar correctamente
     * @param alError    callback si ocurre una excepción
     * @param <T>        tipo del resultado
     * @return          tarea JavaFX creada
     */
    public static <T> Task<T> ejecutar(
            Callable<T> operacion,
            Consumer<T> alExito,
            Consumer<Throwable> alError
    ) {

        Objects.requireNonNull(
                operacion,
                "La operación no puede ser nula."
        );

        Task<T> tarea = new Task<>() {

            @Override
            protected T call() throws Exception {
                return operacion.call();
            }
        };

        tarea.setOnSucceeded(evento -> {

            if (alExito != null) {
                alExito.accept(tarea.getValue());
            }
        });

        tarea.setOnFailed(evento -> {

            if (alError != null) {
                alError.accept(tarea.getException());
            }
        });

        Thread hilo = new Thread(
                tarea,
                "TareaRed-Operación"
        );

        hilo.setDaemon(true);
        hilo.start();

        return tarea;
    }

    /**
     * Variante para operaciones que no devuelven
     * un resultado.
     */
    public static Task<Void> ejecutar(
            Runnable operacion,
            Runnable alExito,
            Consumer<Throwable> alError
    ) {

        Objects.requireNonNull(
                operacion,
                "La operación no puede ser nula."
        );

        return ejecutar(
                () -> {
                    operacion.run();
                    return null;
                },
                resultado -> {
                    if (alExito != null) {
                        alExito.run();
                    }
                },
                alError
        );
    }
}
