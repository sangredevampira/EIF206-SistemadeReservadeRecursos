package ui;

import Sistema.Service.Red.ProxyAuthService;
import Sistema.util.SessionManager;
import javafx.application.Platform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.GraphicsEnvironment;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class MainControllerTest {

    @BeforeAll
    static void iniciarJavaFX() throws Exception {
        assumeFalse(
                GraphicsEnvironment.isHeadless(),
                "La prueba requiere un entorno gráfico JavaFX."
        );
        CountDownLatch iniciado = new CountDownLatch(1);

        try {
            Platform.startup(() -> {
                Platform.setImplicitExit(false);
                iniciado.countDown();
            });
        } catch (IllegalStateException e) {
            Platform.runLater(iniciado::countDown);
        }

        assertTrue(iniciado.await(10, TimeUnit.SECONDS));
    }

    @BeforeEach
    void iniciarSesion() {
        SessionManager.getInstancia().iniciarSesion(
                "usuario",
                "FUNCIONARIO",
                null
        );
    }

    @AfterEach
    void limpiarSesion() {
        SessionManager.getInstancia().cerrarSesion();
    }

    @Test
    void logoutExitosoLimpiaSesionYContinuaEnJavaFX() throws Exception {
        ProxyAuthServiceFalso auth = new ProxyAuthServiceFalso();
        MainController controller = new MainController(auth);
        CountDownLatch terminado = new CountDownLatch(1);
        AtomicBoolean callbackEnJavaFX = new AtomicBoolean();

        controller.iniciarCierreSesion(() -> {
            callbackEnJavaFX.set(Platform.isFxApplicationThread());
            terminado.countDown();
        });

        assertTrue(terminado.await(5, TimeUnit.SECONDS));
        assertEquals(1, auth.invocaciones.get());
        assertNull(SessionManager.getInstancia().getIdUsuario());
        assertNull(SessionManager.getInstancia().getRol());
        assertTrue(callbackEnJavaFX.get());
    }

    @Test
    void logoutFallidoTambienLimpiaSesionYContinua() throws Exception {
        ProxyAuthServiceFalso auth = new ProxyAuthServiceFalso();
        auth.error = new IllegalStateException("Servidor desconectado");
        MainController controller = new MainController(auth);
        CountDownLatch terminado = new CountDownLatch(1);

        controller.iniciarCierreSesion(terminado::countDown);

        assertTrue(terminado.await(5, TimeUnit.SECONDS));
        assertEquals(1, auth.invocaciones.get());
        assertNull(SessionManager.getInstancia().getIdUsuario());
        assertNull(SessionManager.getInstancia().getRol());
    }

    @Test
    void ignoraSolicitudesDuplicadasMientrasLogoutEstaEnCurso() throws Exception {
        ProxyAuthServiceFalso auth = new ProxyAuthServiceFalso();
        auth.bloquear = true;
        MainController controller = new MainController(auth);
        CountDownLatch terminado = new CountDownLatch(1);

        controller.iniciarCierreSesion(terminado::countDown);
        assertTrue(auth.iniciado.await(5, TimeUnit.SECONDS));

        controller.iniciarCierreSesion(terminado::countDown);
        assertEquals(1, auth.invocaciones.get());

        auth.continuar.countDown();
        assertTrue(terminado.await(5, TimeUnit.SECONDS));
        assertEquals(1, auth.invocaciones.get());
    }

    private static final class ProxyAuthServiceFalso extends ProxyAuthService {

        private final AtomicInteger invocaciones = new AtomicInteger();
        private final CountDownLatch iniciado = new CountDownLatch(1);
        private final CountDownLatch continuar = new CountDownLatch(1);
        private volatile RuntimeException error;
        private volatile boolean bloquear;

        @Override
        public void logout() {
            invocaciones.incrementAndGet();
            iniciado.countDown();

            if (bloquear) {
                try {
                    continuar.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            }

            if (error != null) {
                throw error;
            }
        }
    }
}
