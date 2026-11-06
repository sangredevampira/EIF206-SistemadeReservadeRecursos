package ui;

import Sistema.DTO.Funcionario;
import Sistema.DTO.LoginResultadoDTO;
import Sistema.DTO.ResultadoOperacionDTO;
import Sistema.DTO.Rol;
import Sistema.Service.IAuthService;
import Sistema.util.SessionManager;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class LoginControllerTest {

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
    void loginExitosoSeEjecutaFueraDeJavaFXYNavegaEnJavaFX() throws Exception {
        AuthFalso auth = new AuthFalso(() ->
                new LoginResultadoDTO("admin", Rol.ADMINISTRADOR, null)
        );
        VistaLogin vista = cargarVista(auth);

        intentarLogin(vista, "admin", "admin");
        esperarNavegaciones(vista.controlador(), 1);

        SessionManager sesion = SessionManager.getInstancia();
        assertEquals("admin", sesion.getIdUsuario());
        assertEquals("ADMINISTRADOR", sesion.getRol());
        assertNull(sesion.getFuncionarioAsociado());
        assertFalse(auth.ejecutadoEnJavaFX);
        assertTrue(vista.controlador().navegacionEnJavaFX);
        ejecutarEnJavaFX(() -> {
            assertEquals("", etiqueta(vista).getText());
            return null;
        });
    }

    @Test
    void conservaCredencialesYGuardaLosDatosDevueltosPorElServidor()
            throws Exception {
        Funcionario funcionario = new Funcionario("44444", "Ana", "8888-8888");
        AuthFalso auth = new AuthFalso(() ->
                new LoginResultadoDTO("44444", Rol.FUNCIONARIO, funcionario)
        );
        VistaLogin vista = cargarVista(auth);

        intentarLogin(vista, " 44444 ", "clave con espacios ");
        esperarNavegaciones(vista.controlador(), 1);

        assertEquals(" 44444 ", auth.idRecibido);
        assertEquals("clave con espacios ", auth.claveRecibida);
        assertEquals("44444", SessionManager.getInstancia().getIdUsuario());
        assertEquals("FUNCIONARIO", SessionManager.getInstancia().getRol());
        assertSame(funcionario, SessionManager.getInstancia().getFuncionarioAsociado());
    }

    @Test
    void credencialesIncorrectasMuestranMensajeYRestauranControles()
            throws Exception {
        AuthFalso auth = new AuthFalso(() -> {
            throw new IllegalArgumentException("ID o clave incorrectos.");
        });
        VistaLogin vista = cargarVista(auth);

        intentarLogin(vista, "admin", "mala");
        esperarMensaje(vista, "ID o clave incorrectos.");

        verificarControlesDisponibles(vista);
        assertNull(SessionManager.getInstancia().getIdUsuario());
        assertEquals(0, vista.controlador().navegaciones.get());
    }

    @Test
    void errorDeConexionMuestraMensajeYRestaurarControles() throws Exception {
        AuthFalso auth = new AuthFalso(() -> {
            throw new IllegalStateException("No fue posible comunicarse con el servidor.");
        });
        VistaLogin vista = cargarVista(auth);

        intentarLogin(vista, "admin", "admin");
        esperarMensaje(vista, LoginController.MENSAJE_SIN_CONEXION);

        verificarControlesDisponibles(vista);
        assertNull(SessionManager.getInstancia().getIdUsuario());
    }

    @Test
    void respuestaInvalidaMuestraMensajeGenericoYRestaurarControles()
            throws Exception {
        AuthFalso auth = new AuthFalso(() ->
                new LoginResultadoDTO("admin", null, null)
        );
        VistaLogin vista = cargarVista(auth);

        intentarLogin(vista, "admin", "admin");
        esperarMensaje(vista, LoginController.MENSAJE_ERROR_INESPERADO);

        verificarControlesDisponibles(vista);
        assertNull(SessionManager.getInstancia().getIdUsuario());
    }

    @Test
    void respuestaLentaNoBloqueaJavaFXYMantieneEstadoDeCarga() throws Exception {
        RespuestaControlada respuesta = RespuestaControlada.exito(
                new LoginResultadoDTO("admin", Rol.ADMINISTRADOR, null)
        );
        AuthFalso auth = new AuthFalso(respuesta::obtener);
        VistaLogin vista = cargarVista(auth);

        intentarLogin(vista, "admin", "admin");
        respuesta.esperarInicio();

        CountDownLatch pulsoJavaFX = new CountDownLatch(1);
        Platform.runLater(pulsoJavaFX::countDown);
        assertTrue(
                pulsoJavaFX.await(1, TimeUnit.SECONDS),
                "El hilo JavaFX quedó bloqueado durante la autenticación."
        );

        ejecutarEnJavaFX(() -> {
            assertEquals("Verificando credenciales...", etiqueta(vista).getText());
            assertTrue(campoId(vista).isDisabled());
            assertTrue(campoClave(vista).isDisabled());
            assertTrue(boton(vista, "btnIngresar").isDisabled());
            assertTrue(boton(vista, "btnCambiar").isDisabled());
            assertFalse(boton(vista, "btnCancelar").isDisabled());
            return null;
        });

        respuesta.responder();
        esperarNavegaciones(vista.controlador(), 1);
    }

    @Test
    void clicsRepetidosNoCreanSolicitudesSimultaneas() throws Exception {
        RespuestaControlada respuesta = RespuestaControlada.exito(
                new LoginResultadoDTO("admin", Rol.ADMINISTRADOR, null)
        );
        AuthFalso auth = new AuthFalso(respuesta::obtener);
        VistaLogin vista = cargarVista(auth);

        intentarLogin(vista, "admin", "admin");
        respuesta.esperarInicio();
        ejecutarEnJavaFX(() -> {
            ActionEvent repetido = new ActionEvent(
                    boton(vista, "btnIngresar"),
                    boton(vista, "btnIngresar")
            );
            boton(vista, "btnIngresar").getOnAction().handle(repetido);
            boton(vista, "btnIngresar").getOnAction().handle(repetido);
            return null;
        });

        assertEquals(1, auth.llamadas.get());
        respuesta.responder();
        esperarNavegaciones(vista.controlador(), 1);
    }

    @Test
    void respuestaInvalidadaNoAfectaUnIntentoPosterior() throws Exception {
        RespuestaControlada anterior = RespuestaControlada.exito(
                new LoginResultadoDTO("anterior", Rol.ADMINISTRADOR, null)
        );
        RespuestaControlada vigente = RespuestaControlada.exito(
                new LoginResultadoDTO("vigente", Rol.ADMINISTRADOR, null)
        );
        AuthFalso auth = new AuthFalso(anterior::obtener, vigente::obtener);
        VistaLogin vista = cargarVista(auth);

        intentarLogin(vista, "anterior", "secreto-anterior");
        anterior.esperarInicio();
        ejecutarEnJavaFX(() -> {
            boton(vista, "btnCancelar").fire();
            campoId(vista).setText("vigente");
            campoClave(vista).setText("secreto-vigente");
            boton(vista, "btnIngresar").fire();
            return null;
        });
        vigente.esperarInicio();

        vigente.responder();
        esperarNavegaciones(vista.controlador(), 1);
        anterior.responder();
        anterior.esperarFinalizacion();
        esperarEventosJavaFX();

        assertEquals("vigente", SessionManager.getInstancia().getIdUsuario());
        assertEquals(1, vista.controlador().navegaciones.get());
        assertEquals(1, vista.controlador().salidas.get());
        ejecutarEnJavaFX(() -> {
            assertEquals("", etiqueta(vista).getText());
            return null;
        });
    }

    @Test
    void constructorDePruebasRechazaServicioNulo() {
        assertThrows(NullPointerException.class, () -> new LoginController(null));
    }

    private static VistaLogin cargarVista(IAuthService authService) throws Exception {
        ControladorPrueba controlador = new ControladorPrueba(authService);
        return ejecutarEnJavaFX(() -> {
            URL recurso = LoginControllerTest.class.getResource(
                    "/Sistema/view/LoginView.fxml"
            );
            FXMLLoader cargador = new FXMLLoader(recurso);
            cargador.setController(controlador);
            Parent raiz = cargador.load();
            return new VistaLogin(cargador, raiz, controlador);
        });
    }

    private static void intentarLogin(VistaLogin vista, String id, String clave)
            throws Exception {
        ejecutarEnJavaFX(() -> {
            campoId(vista).setText(id);
            campoClave(vista).setText(clave);
            boton(vista, "btnIngresar").fire();
            return null;
        });
    }

    private static void verificarControlesDisponibles(VistaLogin vista)
            throws Exception {
        ejecutarEnJavaFX(() -> {
            assertFalse(campoId(vista).isDisabled());
            assertFalse(campoClave(vista).isDisabled());
            assertFalse(boton(vista, "btnIngresar").isDisabled());
            assertFalse(boton(vista, "btnCambiar").isDisabled());
            assertFalse(boton(vista, "btnCancelar").isDisabled());
            return null;
        });
    }

    private static void esperarMensaje(VistaLogin vista, String mensaje)
            throws Exception {
        esperarHasta(() -> ejecutarEnJavaFX(() ->
                mensaje.equals(etiqueta(vista).getText())
        ));
    }

    private static void esperarNavegaciones(
            ControladorPrueba controlador,
            int cantidad
    ) throws Exception {
        esperarHasta(() -> controlador.navegaciones.get() == cantidad);
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

    private static TextField campoId(VistaLogin vista) {
        return assertInstanceOf(
                TextField.class,
                vista.cargador().getNamespace().get("campoId")
        );
    }

    private static PasswordField campoClave(VistaLogin vista) {
        return assertInstanceOf(
                PasswordField.class,
                vista.cargador().getNamespace().get("campoClave")
        );
    }

    private static Label etiqueta(VistaLogin vista) {
        return assertInstanceOf(
                Label.class,
                vista.cargador().getNamespace().get("lblMensaje")
        );
    }

    private static Button boton(VistaLogin vista, String id) {
        return assertInstanceOf(
                Button.class,
                vista.cargador().getNamespace().get(id)
        );
    }

    private static final class ControladorPrueba extends LoginController {
        private final AtomicInteger navegaciones = new AtomicInteger();
        private final AtomicInteger salidas = new AtomicInteger();
        private volatile boolean navegacionEnJavaFX;

        private ControladorPrueba(IAuthService authService) {
            super(authService);
        }

        @Override
        boolean abrirVentanaPrincipal(ActionEvent evento) {
            navegacionEnJavaFX = Platform.isFxApplicationThread();
            navegaciones.incrementAndGet();
            return true;
        }

        @Override
        void salirAplicacion() {
            salidas.incrementAndGet();
        }
    }

    private static final class AuthFalso implements IAuthService {
        private final List<Callable<LoginResultadoDTO>> respuestas;
        private final AtomicInteger llamadas = new AtomicInteger();
        private volatile String idRecibido;
        private volatile String claveRecibida;
        private volatile boolean ejecutadoEnJavaFX;

        @SafeVarargs
        private AuthFalso(Callable<LoginResultadoDTO>... respuestas) {
            this.respuestas = List.of(respuestas);
        }

        @Override
        public LoginResultadoDTO login(String id, String clave) {
            idRecibido = id;
            claveRecibida = clave;
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
        public ResultadoOperacionDTO cambiarClave(
                String id,
                String actual,
                String nueva,
                String confirmacion
        ) {
            throw new UnsupportedOperationException("No se usa en estas pruebas.");
        }

        @Override
        public void logout() {
            throw new UnsupportedOperationException("No se usa en estas pruebas.");
        }
    }

    private static final class RespuestaControlada {
        private final LoginResultadoDTO resultado;
        private final CountDownLatch iniciada = new CountDownLatch(1);
        private final CountDownLatch permitida = new CountDownLatch(1);
        private final CountDownLatch finalizada = new CountDownLatch(1);

        private RespuestaControlada(LoginResultadoDTO resultado) {
            this.resultado = resultado;
        }

        private static RespuestaControlada exito(LoginResultadoDTO resultado) {
            return new RespuestaControlada(resultado);
        }

        private LoginResultadoDTO obtener() throws InterruptedException {
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

    private record VistaLogin(
            FXMLLoader cargador,
            Parent raiz,
            ControladorPrueba controlador
    ) {
    }
}
