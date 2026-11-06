package ui;

import Sistema.DTO.LoginResultadoDTO;
import Sistema.DTO.ResultadoOperacionDTO;
import Sistema.Service.IAuthService;
import Sistema.util.SessionManager;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.GraphicsEnvironment;
import java.net.URL;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class CambiarClaveControllerTest {

    @BeforeAll
    static void iniciarJavaFX() throws Exception {
        assumeFalse(
                GraphicsEnvironment.isHeadless(),
                "La carga FXML requiere un entorno gráfico para JavaFX."
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
    @AfterEach
    void limpiarSesion() {
        SessionManager.getInstancia().cerrarSesion();
    }

    @Test
    void cambioExitosoSeEjecutaFueraDeJavaFXYCierraEnJavaFX() throws Exception {
        AuthFalso auth = new AuthFalso(ResultadoOperacionDTO::exito);
        VistaCambio vista = cargarVista(auth, "123");

        confirmar(vista, "actual1", "nueva1", "nueva1");
        esperarCierres(vista.controlador(), 1);

        assertEquals("123", auth.idRecibido);
        assertEquals("actual1", auth.actualRecibida);
        assertEquals("nueva1", auth.nuevaRecibida);
        assertEquals("nueva1", auth.confirmacionRecibida);
        assertFalse(auth.ejecutadoEnJavaFX);
        assertTrue(vista.controlador().cierreEnJavaFX);
    }

    @Test
    void claveActualIncorrectaMuestraMensajeYRestaurarControles()
            throws Exception {
        AuthFalso auth = new AuthFalso(() ->
                ResultadoOperacionDTO.fallo("La clave actual es incorrecta.")
        );
        VistaCambio vista = cargarVista(auth, "123");

        confirmar(vista, "incorrecta", "nueva1", "nueva1");
        esperarMensaje(vista, "La clave actual es incorrecta.");

        verificarControlesDisponibles(vista);
        assertEquals(0, vista.controlador().cierres.get());
    }

    @Test
    void clavesNuevasDiferentesMantienenValidacionDelServidor()
            throws Exception {
        AuthFalso auth = new AuthFalso(() ->
                ResultadoOperacionDTO.fallo("Las dos claves nuevas no coinciden.")
        );
        VistaCambio vista = cargarVista(auth, "123");

        confirmar(vista, "actual1", "nueva1", "diferente");
        esperarMensaje(vista, "Las dos claves nuevas no coinciden.");

        assertEquals("nueva1", auth.nuevaRecibida);
        assertEquals("diferente", auth.confirmacionRecibida);
        verificarControlesDisponibles(vista);
    }

    @Test
    void errorDeComunicacionMuestraMensajeYRestaurarControles()
            throws Exception {
        AuthFalso auth = new AuthFalso(() -> {
            throw new IllegalStateException("No fue posible comunicarse con el servidor.");
        });
        VistaCambio vista = cargarVista(auth, "123");

        confirmar(vista, "actual1", "nueva1", "nueva1");
        esperarMensaje(vista, CambiarClaveController.MENSAJE_SIN_CONEXION);

        verificarControlesDisponibles(vista);
    }

    @Test
    void respuestaInesperadaMuestraMensajeGenericoYRestaurarControles()
            throws Exception {
        AuthFalso auth = new AuthFalso(() -> null);
        VistaCambio vista = cargarVista(auth, "123");

        confirmar(vista, "actual1", "nueva1", "nueva1");
        esperarMensaje(vista, CambiarClaveController.MENSAJE_ERROR_INESPERADO);

        verificarControlesDisponibles(vista);
    }

    @Test
    void respuestaLentaNoBloqueaJavaFXYMantieneEstadoDeCarga() throws Exception {
        RespuestaControlada respuesta = new RespuestaControlada(
                ResultadoOperacionDTO.exito()
        );
        AuthFalso auth = new AuthFalso(respuesta::obtener);
        VistaCambio vista = cargarVista(auth, "123");

        confirmar(vista, "actual1", "nueva1", "nueva1");
        respuesta.esperarInicio();

        CountDownLatch pulsoJavaFX = new CountDownLatch(1);
        Platform.runLater(pulsoJavaFX::countDown);
        assertTrue(
                pulsoJavaFX.await(1, TimeUnit.SECONDS),
                "El hilo JavaFX quedó bloqueado durante el cambio de clave."
        );

        ejecutarEnJavaFX(() -> {
            assertEquals("Actualizando clave...", mensaje(vista).getText());
            assertTrue(campo(vista, "campoClaveActual").isDisabled());
            assertTrue(campo(vista, "campoClaveNueva").isDisabled());
            assertTrue(campo(vista, "campoClaveNuevaConfirmacion").isDisabled());
            assertTrue(boton(vista, "btnConfirmar").isDisabled());
            assertFalse(boton(vista, "btnCancelar").isDisabled());
            return null;
        });

        respuesta.responder();
        esperarCierres(vista.controlador(), 1);
    }

    @Test
    void intentosRepetidosNoCreanSolicitudesSimultaneas() throws Exception {
        RespuestaControlada respuesta = new RespuestaControlada(
                ResultadoOperacionDTO.exito()
        );
        AuthFalso auth = new AuthFalso(respuesta::obtener);
        VistaCambio vista = cargarVista(auth, "123");

        confirmar(vista, "actual1", "nueva1", "nueva1");
        respuesta.esperarInicio();
        ejecutarEnJavaFX(() -> {
            ActionEvent repetido = new ActionEvent(
                    boton(vista, "btnConfirmar"),
                    boton(vista, "btnConfirmar")
            );
            boton(vista, "btnConfirmar").getOnAction().handle(repetido);
            boton(vista, "btnConfirmar").getOnAction().handle(repetido);
            return null;
        });

        assertEquals(1, auth.llamadas.get());
        respuesta.responder();
        esperarCierres(vista.controlador(), 1);
    }

    @Test
    void cancelarDuranteSolicitudInvalidaLaRespuestaPosterior() throws Exception {
        RespuestaControlada respuesta = new RespuestaControlada(
                ResultadoOperacionDTO.exito()
        );
        AuthFalso auth = new AuthFalso(respuesta::obtener);
        VistaCambio vista = cargarVista(auth, "123");

        confirmar(vista, "actual1", "nueva1", "nueva1");
        respuesta.esperarInicio();
        ejecutarEnJavaFX(() -> {
            boton(vista, "btnCancelar").fire();
            return null;
        });
        assertEquals(1, vista.controlador().cierres.get());

        respuesta.responder();
        respuesta.esperarFinalizacion();
        esperarEventosJavaFX();

        assertEquals(1, vista.controlador().cierres.get());
        ejecutarEnJavaFX(() -> {
            assertEquals("Actualizando clave...", mensaje(vista).getText());
            return null;
        });
    }

    @Test
    void cierreExternoDuranteSolicitudInvalidaErroresPosteriores()
            throws Exception {
        RespuestaControlada respuesta = new RespuestaControlada(
                ResultadoOperacionDTO.fallo("Respuesta tardía")
        );
        AuthFalso auth = new AuthFalso(respuesta::obtener);
        VistaCambio vista = cargarVista(auth, "123");

        confirmar(vista, "actual1", "nueva1", "nueva1");
        respuesta.esperarInicio();
        ejecutarEnJavaFX(() -> {
            vista.controlador().notificarVentanaCerrada();
            return null;
        });

        respuesta.responder();
        respuesta.esperarFinalizacion();
        esperarEventosJavaFX();

        ejecutarEnJavaFX(() -> {
            assertEquals("Actualizando clave...", mensaje(vista).getText());
            return null;
        });
        assertEquals(0, vista.controlador().cierres.get());
    }

    @Test
    void desdeLoginUsaIdRecibido() throws Exception {
        AuthFalso auth = new AuthFalso(ResultadoOperacionDTO::exito);
        VistaCambio vista = cargarVista(auth, "ADMIN");

        ejecutarEnJavaFX(() -> {
            assertEquals("ADMIN", vista.controlador().obtenerIdObjetivo());
            assertEquals("Usuario: ADMIN", usuario(vista).getText());
            return null;
        });
    }

    @Test
    void desdeVentanaPrincipalUsaIdDeSesion() throws Exception {
        SessionManager.getInstancia().iniciarSesion("44444", "FUNCIONARIO", null);
        AuthFalso auth = new AuthFalso(ResultadoOperacionDTO::exito);
        VistaCambio vista = cargarVista(auth, null);

        ejecutarEnJavaFX(() -> {
            assertEquals("44444", vista.controlador().obtenerIdObjetivo());
            assertEquals("Usuario: 44444", usuario(vista).getText());
            return null;
        });
    }

    @Test
    void constructorDePruebasRechazaServicioNulo() {
        assertThrows(NullPointerException.class, () -> new CambiarClaveController(null));
    }

    private static VistaCambio cargarVista(IAuthService auth, String idObjetivo)
            throws Exception {
        ControladorPrueba controlador = new ControladorPrueba(auth);
        controlador.setIdUsuarioObjetivo(idObjetivo);
        return ejecutarEnJavaFX(() -> {
            URL recurso = CambiarClaveControllerTest.class.getResource(
                    "/Sistema/view/CambiarClaveView.fxml"
            );
            FXMLLoader cargador = new FXMLLoader(recurso);
            cargador.setController(controlador);
            Parent raiz = cargador.load();
            return new VistaCambio(cargador, raiz, controlador);
        });
    }

    private static void confirmar(
            VistaCambio vista,
            String actual,
            String nueva,
            String confirmacion
    ) throws Exception {
        ejecutarEnJavaFX(() -> {
            campo(vista, "campoClaveActual").setText(actual);
            campo(vista, "campoClaveNueva").setText(nueva);
            campo(vista, "campoClaveNuevaConfirmacion").setText(confirmacion);
            boton(vista, "btnConfirmar").fire();
            return null;
        });
    }

    private static void verificarControlesDisponibles(VistaCambio vista)
            throws Exception {
        ejecutarEnJavaFX(() -> {
            assertFalse(campo(vista, "campoClaveActual").isDisabled());
            assertFalse(campo(vista, "campoClaveNueva").isDisabled());
            assertFalse(campo(vista, "campoClaveNuevaConfirmacion").isDisabled());
            assertFalse(boton(vista, "btnConfirmar").isDisabled());
            assertFalse(boton(vista, "btnCancelar").isDisabled());
            return null;
        });
    }

    private static void esperarMensaje(VistaCambio vista, String texto)
            throws Exception {
        esperarHasta(() -> ejecutarEnJavaFX(() ->
                texto.equals(mensaje(vista).getText())
        ));
    }

    private static void esperarCierres(ControladorPrueba controlador, int cantidad)
            throws Exception {
        esperarHasta(() -> controlador.cierres.get() == cantidad);
    }

    private static void esperarEventosJavaFX() throws Exception {
        Thread.sleep(100);
        ejecutarEnJavaFX(() -> null);
    }

    private static void esperarHasta(Callable<Boolean> condicion) throws Exception {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < limite) {
            if (condicion.call()) return;
            Thread.sleep(20);
        }
        fail("La interfaz no alcanzó el estado esperado.");
    }

    private static <T> T ejecutarEnJavaFX(Callable<T> accion) throws Exception {
        if (Platform.isFxApplicationThread()) return accion.call();
        FutureTask<T> tarea = new FutureTask<>(accion);
        Platform.runLater(tarea);
        return tarea.get(10, TimeUnit.SECONDS);
    }

    private static PasswordField campo(VistaCambio vista, String id) {
        return assertInstanceOf(
                PasswordField.class,
                vista.cargador().getNamespace().get(id)
        );
    }

    private static Button boton(VistaCambio vista, String id) {
        return assertInstanceOf(
                Button.class,
                vista.cargador().getNamespace().get(id)
        );
    }

    private static Label mensaje(VistaCambio vista) {
        return assertInstanceOf(
                Label.class,
                vista.cargador().getNamespace().get("lblMensaje")
        );
    }

    private static Label usuario(VistaCambio vista) {
        return assertInstanceOf(
                Label.class,
                vista.cargador().getNamespace().get("lblUsuarioObjetivo")
        );
    }

    private static final class ControladorPrueba extends CambiarClaveController {
        private final AtomicInteger cierres = new AtomicInteger();
        private volatile boolean cierreEnJavaFX;

        private ControladorPrueba(IAuthService authService) {
            super(authService);
        }

        @Override
        void cerrarVentana(ActionEvent evento) {
            cierreEnJavaFX = Platform.isFxApplicationThread();
            cierres.incrementAndGet();
        }
    }

    private static final class AuthFalso implements IAuthService {
        private final List<Callable<ResultadoOperacionDTO>> respuestas;
        private final AtomicInteger llamadas = new AtomicInteger();
        private volatile String idRecibido;
        private volatile String actualRecibida;
        private volatile String nuevaRecibida;
        private volatile String confirmacionRecibida;
        private volatile boolean ejecutadoEnJavaFX;

        @SafeVarargs
        private AuthFalso(Callable<ResultadoOperacionDTO>... respuestas) {
            this.respuestas = List.of(respuestas);
        }

        @Override
        public ResultadoOperacionDTO cambiarClave(
                String id,
                String actual,
                String nueva,
                String confirmacion
        ) {
            idRecibido = id;
            actualRecibida = actual;
            nuevaRecibida = nueva;
            confirmacionRecibida = confirmacion;
            ejecutadoEnJavaFX = Platform.isFxApplicationThread();
            int indice = llamadas.getAndIncrement();
            try {
                return respuestas.get(indice).call();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public LoginResultadoDTO login(String id, String clave) {
            throw new UnsupportedOperationException("No se usa en estas pruebas.");
        }

        @Override
        public void logout() {
            throw new UnsupportedOperationException("No se usa en estas pruebas.");
        }
    }

    private static final class RespuestaControlada {
        private final ResultadoOperacionDTO resultado;
        private final CountDownLatch iniciada = new CountDownLatch(1);
        private final CountDownLatch permitida = new CountDownLatch(1);
        private final CountDownLatch finalizada = new CountDownLatch(1);

        private RespuestaControlada(ResultadoOperacionDTO resultado) {
            this.resultado = resultado;
        }

        private ResultadoOperacionDTO obtener() throws InterruptedException {
            iniciada.countDown();
            try {
                assertTrue(permitida.await(5, TimeUnit.SECONDS));
                return resultado;
            } finally {
                finalizada.countDown();
            }
        }

        private void esperarInicio() throws InterruptedException {
            assertTrue(iniciada.await(5, TimeUnit.SECONDS));
        }

        private void responder() {
            permitida.countDown();
        }

        private void esperarFinalizacion() throws InterruptedException {
            assertTrue(finalizada.await(5, TimeUnit.SECONDS));
        }
    }

    private record VistaCambio(
            FXMLLoader cargador,
            Parent raiz,
            ControladorPrueba controlador
    ) {
    }
}
