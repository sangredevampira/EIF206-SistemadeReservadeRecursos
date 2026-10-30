
package Sistema.Red;

import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class TareaRedTest {

    @BeforeAll
    static void iniciarJavaFX() throws Exception {

        CountDownLatch iniciado = new CountDownLatch(1);

        try {
            Platform.startup(() -> {
                Platform.setImplicitExit(false);
                iniciado.countDown();
            });
        } catch (IllegalStateException e) {
            // JavaFX ya estaba inicializado.
            Platform.runLater(iniciado::countDown);
        }

        assertTrue(
                iniciado.await(10, TimeUnit.SECONDS),
                "No se pudo iniciar JavaFX."
        );
    }

    @Test
    void ejecutaOperacionEnSegundoPlano() throws Exception {

        CountDownLatch terminado = new CountDownLatch(1);

        AtomicReference<String> nombreHilo =
                new AtomicReference<>();

        AtomicReference<String> resultado =
                new AtomicReference<>();

        TareaRed.ejecutar(
                () -> {
                    nombreHilo.set(
                            Thread.currentThread().getName()
                    );

                    return "OPERACION_OK";
                },

                valor -> {
                    resultado.set(valor);
                    terminado.countDown();
                },

                error -> terminado.countDown()
        );

        assertTrue(
                terminado.await(5, TimeUnit.SECONDS)
        );

        assertEquals("OPERACION_OK", resultado.get());

        assertNotNull(nombreHilo.get());

        assertFalse(nombreHilo.get().isBlank());
    }

    @Test
    void ejecutaCallbackEnHiloJavaFX() throws Exception {

        CountDownLatch terminado = new CountDownLatch(1);

        AtomicBoolean callbackEnJavaFX =
                new AtomicBoolean(false);

        AtomicBoolean operacionEnJavaFX =
                new AtomicBoolean(true);

        TareaRed.ejecutar(
                () -> {
                    operacionEnJavaFX.set(
                            Platform.isFxApplicationThread()
                    );

                    return 10;
                },

                valor -> {
                    callbackEnJavaFX.set(
                            Platform.isFxApplicationThread()
                    );

                    terminado.countDown();
                },

                error -> terminado.countDown()
        );

        assertTrue(
                terminado.await(5, TimeUnit.SECONDS)
        );

        assertFalse(operacionEnJavaFX.get());
        assertTrue(callbackEnJavaFX.get());
    }

    @Test
    void notificaErroresCorrectamente() throws Exception {

        CountDownLatch terminado = new CountDownLatch(1);

        AtomicReference<Throwable> errorRecibido =
                new AtomicReference<>();

        TareaRed.ejecutar(
                () -> {
                    throw new IOException(
                            "Error de conexión simulado"
                    );
                },

                (String valor) -> {
                    fail("La operación no debía finalizar con éxito.");
                },

                error -> {
                    errorRecibido.set(error);
                    terminado.countDown();
                }
        );

        assertTrue(
                terminado.await(5, TimeUnit.SECONDS)
        );

        assertNotNull(errorRecibido.get());

        assertInstanceOf(
                IOException.class,
                errorRecibido.get()
        );

        assertEquals(
                "Error de conexión simulado",
                errorRecibido.get().getMessage()
        );
    }

    @Test
    void ejecutaOperacionesSinResultado() throws Exception {

        CountDownLatch terminado = new CountDownLatch(1);

        AtomicBoolean operacionEjecutada =
                new AtomicBoolean(false);

        AtomicBoolean exitoEjecutado =
                new AtomicBoolean(false);

        TareaRed.ejecutar(
                (Runnable) () ->
                        operacionEjecutada.set(true),

                () -> {
                    exitoEjecutado.set(true);
                    terminado.countDown();
                },

                error -> terminado.countDown()
        );

        assertTrue(
                terminado.await(5, TimeUnit.SECONDS)
        );

        assertTrue(operacionEjecutada.get());
        assertTrue(exitoEjecutado.get());
    }
}
