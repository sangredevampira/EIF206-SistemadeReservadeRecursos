package ui;

import Sistema.DTO.CategoriaDTO;
import Sistema.DTO.RecursoDTO;
import Sistema.DTO.ResultadoOperacionDTO;
import Sistema.Service.ICategoriaService;
import Sistema.Service.IRecursoService;
import Sistema.util.SessionManager;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

class RecursosAsincronosTest {
    private static final CategoriaDTO CAT = new CategoriaDTO("CAT-000001", "Salas");
    private static final RecursoDTO UNO = new RecursoDTO("123456", "Sala uno", CAT);
    private static final RecursoDTO DOS = new RecursoDTO("234567", "Sala dos", CAT);

    @BeforeAll
    static void iniciar() throws Exception {
        CountDownLatch listo = new CountDownLatch(1);
        try { Platform.startup(() -> { Platform.setImplicitExit(false); listo.countDown(); }); }
        catch (IllegalStateException e) { Platform.runLater(listo::countDown); }
        assertTrue(listo.await(10, TimeUnit.SECONDS));
    }

    @BeforeEach
    void iniciarSesionAdministrativa() {
        SessionManager.getInstancia().iniciarSesion("admin", "ADMINISTRADOR", null);
    }

    @AfterEach
    void cerrarSesion() {
        SessionManager.getInstancia().cerrarSesion();
    }

    @Test void cargaInicialIncluyeCategoriasIdYTabla() throws Exception {
        Servicio s = new Servicio(); Vista v = vista(s);
        lista(v, List.of(UNO));
        fx(() -> {
            assertEquals("345678", v.texto("txtIdRecurso").getText());
            assertEquals(List.of(CAT), v.combo("cbCategoriaRecurso").getItems());
            assertFalse(v.boton("btnGuardarRecursos").isDisabled());
            return null;
        });
        assertEquals(1, s.cantidad("generarNuevoId"));
    }

    @Test void funcionarioNoSolicitaNuevoId() throws Exception {
        SessionManager.getInstancia().iniciarSesion("funcionario", "FUNCIONARIO", null);
        Servicio s = new Servicio(); Vista v = abrir(s);

        lista(v, List.of(UNO));
        esperarFx(() -> !v.combo("cbCategoriaRecurso").isDisabled());

        assertEquals(0, s.cantidad("generarNuevoId"));
        fx(() -> {
            assertEquals("", v.texto("txtIdRecurso").getText());
            assertTrue(v.boton("btnGuardarRecursos").isDisabled());
            assertTrue(v.control.alertas.isEmpty());
            return null;
        });
    }

    @Test void sinSesionNoSolicitaNuevoId() throws Exception {
        SessionManager.getInstancia().cerrarSesion();
        Servicio s = new Servicio(); Vista v = abrir(s);

        lista(v, List.of(UNO));
        esperarFx(() -> !v.combo("cbCategoriaRecurso").isDisabled());

        assertEquals(0, s.cantidad("generarNuevoId"));
        fx(() -> {
            assertEquals("", v.texto("txtIdRecurso").getText());
            assertTrue(v.boton("btnGuardarRecursos").isDisabled());
            return null;
        });
    }

    @Test void busquedaExitosaYVacia() throws Exception {
        Servicio s = new Servicio(); Vista v = vista(s);
        s.responder("filtrarRecursos", List.of(DOS)); buscar(v, "dos"); lista(v, List.of(DOS));
        s.responder("filtrarRecursos", List.of()); buscar(v, "ninguno"); lista(v, List.of());
        esperar(() -> v.control.alertas.size() == 1);
        assertEquals(Alert.AlertType.INFORMATION, v.control.alertas.getFirst().tipo());
    }

    @Test void erroresDeConsultaConservanTabla() throws Exception {
        Servicio s = new Servicio(); Vista v = vista(s);
        s.fallar("filtrarRecursos", new IllegalStateException("red")); buscar(v, "uno");
        esperar(() -> v.control.alertas.size() == 1); lista(v, List.of(UNO));
        assertEquals(RecursoViewController.MENSAJE_SIN_CONEXION_BUSCAR, v.control.alertas.getFirst().mensaje());
        s.fallar("filtrarRecursos", new IllegalArgumentException("Sin permiso")); buscar(v, "dos");
        esperar(() -> v.control.alertas.size() == 2); lista(v, List.of(UNO));
        assertEquals("Sin permiso", v.control.alertas.getLast().mensaje());
    }

    @Test void registroExitoso() throws Exception { escritura(false, true); }
    @Test void registroRechazado() throws Exception { escritura(false, false); }
    @Test void modificacionExitosa() throws Exception { escritura(true, true); }
    @Test void modificacionRechazada() throws Exception { escritura(true, false); }

    private void escritura(boolean modificar, boolean exito) throws Exception {
        Servicio s = new Servicio(); Vista v = vista(s);
        if (modificar) seleccionar(v);
        formulario(v);
        s.responder("buscarRecursoID", modificar ? UNO : null);
        String metodo = modificar ? "modificarRecurso" : "registrarRecurso";
        s.responder(metodo, exito ? ResultadoOperacionDTO.exito() : ResultadoOperacionDTO.fallo("Rechazado"));
        if (exito) s.responder("listarRecursos", List.of(DOS));
        pulsar(v, "btnGuardarRecursos");
        esperar(() -> !v.control.alertas.isEmpty()); terminado(v);
        assertEquals(1, s.cantidad(metodo));
        fx(() -> {
            assertEquals(exito ? "" : "Descripción editada", v.texto("txtDescripcionRecurso").getText());
            assertFalse(v.boton("btnGuardarRecursos").isDisabled());
            return null;
        });
        lista(v, exito ? List.of(DOS) : List.of(UNO));
    }

    @Test void bajaExitosa() throws Exception {
        Servicio s = new Servicio(); Vista v = vista(s); seleccionar(v);
        s.responder("eliminarRecurso", ResultadoOperacionDTO.exito());
        s.responder("listarRecursos", List.of()); pulsar(v, "btnBorrarRecursos");
        esperar(() -> !v.control.alertas.isEmpty()); terminado(v); lista(v, List.of());
        assertEquals("123456", s.ultimoId);
    }

    @Test void bajaRechazadaPorReservasMantieneSeleccion() throws Exception {
        Servicio s = new Servicio(); Vista v = vista(s); seleccionar(v);
        String mensaje = "No se puede eliminar el recurso porque tiene reservas futuras activas";
        s.responder("eliminarRecurso", ResultadoOperacionDTO.fallo(mensaje));
        pulsar(v, "btnBorrarRecursos"); esperar(() -> !v.control.alertas.isEmpty()); terminado(v);
        assertEquals(mensaje, v.control.alertas.getFirst().mensaje());
        fx(() -> { assertSame(UNO, v.tabla().getSelectionModel().getSelectedItem());
            assertEquals(UNO.getDescripcion(), v.texto("txtDescripcionRecurso").getText()); return null; });
    }

    @Test void cancelarConfirmacionNoEnviaBaja() throws Exception {
        Servicio s = new Servicio(); Vista v = vista(s); seleccionar(v);
        v.control.confirmar = false; pulsar(v, "btnBorrarRecursos");
        assertEquals(0, s.cantidad("eliminarRecurso"));
    }

    @Test void escrituraLentaBloqueaDuplicadosPeroNoJavaFX() throws Exception {
        Servicio s = new Servicio(); Vista v = vista(s); formulario(v);
        Puerta p = s.pausar("registrarRecurso", ResultadoOperacionDTO.fallo("Rechazado"));
        pulsar(v, "btnGuardarRecursos"); p.iniciada();
        fx(() -> {
            assertTrue(v.boton("btnGuardarRecursos").isDisabled());
            assertTrue(v.boton("btnFiltroBuscarRecursos").isDisabled());
            assertTrue(v.tabla().isDisabled());
            for (String id : List.of("btnGuardarRecursos", "btnBorrarRecursos", "btnFiltroBuscarRecursos"))
                v.boton(id).getOnAction().handle(new ActionEvent());
            return null;
        });
        assertEquals(1, s.cantidad("registrarRecurso"));
        assertEquals(0, s.cantidad("filtrarRecursos"));
        p.liberar(); esperar(() -> !v.control.alertas.isEmpty()); terminado(v);
    }

    @Test void bajaLentaNoEliminaAntesDeConfirmacionNiDuplica() throws Exception {
        Servicio s = new Servicio(); Vista v = vista(s); seleccionar(v);
        Puerta p = s.pausar("eliminarRecurso", ResultadoOperacionDTO.fallo("Rechazado"));
        pulsar(v, "btnBorrarRecursos"); p.iniciada();
        fx(() -> { assertEquals(List.of(UNO), v.tabla().getItems());
            v.boton("btnBorrarRecursos").getOnAction().handle(new ActionEvent()); return null; });
        assertEquals(1, s.cantidad("eliminarRecurso"));
        p.liberar(); esperar(() -> !v.control.alertas.isEmpty()); terminado(v);
    }

    @Test void errorDeEscrituraConservaFormulario() throws Exception {
        Servicio s = new Servicio(); Vista v = vista(s); formulario(v);
        s.fallar("registrarRecurso", new IllegalStateException("red"));
        pulsar(v, "btnGuardarRecursos"); esperar(() -> !v.control.alertas.isEmpty()); terminado(v);
        assertEquals(RecursoViewController.MENSAJE_SIN_CONEXION_GUARDAR, v.control.alertas.getFirst().mensaje());
        fx(() -> { assertEquals("Descripción editada", v.texto("txtDescripcionRecurso").getText()); return null; });
    }

    @Test void permisoDenegadoAlComprobarExistenciaNoIntentaRegistrar() throws Exception {
        Servicio s = new Servicio(); Vista v = vista(s); formulario(v);
        s.fallar("buscarRecursoID", new IllegalArgumentException("Sin permiso"));
        pulsar(v, "btnGuardarRecursos"); esperar(() -> !v.control.alertas.isEmpty()); terminado(v);
        assertEquals("Sin permiso", v.control.alertas.getFirst().mensaje());
        assertEquals(0, s.cantidad("registrarRecurso"));
    }

    @Test void exitoSeguidoDeErrorDeRecargaSeInformaSinBorrarTabla() throws Exception {
        Servicio s = new Servicio(); Vista v = vista(s); formulario(v);
        s.fallar("listarRecursos", new IllegalStateException("red"));
        pulsar(v, "btnGuardarRecursos"); esperar(() -> v.control.alertas.size() == 2); terminado(v);
        assertEquals(Alert.AlertType.INFORMATION, v.control.alertas.getFirst().tipo());
        assertEquals(RecursoViewController.MENSAJE_SIN_CONEXION_CARGAR, v.control.alertas.getLast().mensaje());
        lista(v, List.of(UNO));
    }

    @Test void respuestasFueraDeOrdenNoSobrescribenConsultaNueva() throws Exception {
        Servicio s = new Servicio(); Vista v = vista(s);
        Puerta vieja = s.pausar("filtrarRecursos", List.of(UNO)); buscar(v, "uno"); vieja.iniciada();
        s.responder("filtrarRecursos", List.of(DOS)); buscar(v, "dos"); lista(v, List.of(DOS));
        vieja.liberar(); lista(v, List.of(DOS));
    }

    @Test void cambioFiltroInvalidaErrorTardio() throws Exception {
        Servicio s = new Servicio(); Vista v = vista(s);
        Puerta vieja = s.pausar("filtrarRecursos", new IllegalStateException("red"));
        buscar(v, "uno"); vieja.iniciada();
        fx(() -> { v.combo("cbFiltroCategoriaRecursos").setValue(CAT); return null; });
        vieja.liberar(); lista(v, List.of(UNO)); assertTrue(v.control.alertas.isEmpty());
    }

    @Test void consultaAntiguaNoDesbloqueaEscrituraVigente() throws Exception {
        Servicio s = new Servicio(); Vista v = vista(s); formulario(v);
        Puerta consulta = s.pausar("filtrarRecursos", List.of(DOS)); buscar(v, "dos"); consulta.iniciada();
        Puerta escritura = s.pausar("registrarRecurso", ResultadoOperacionDTO.fallo("Rechazado"));
        pulsar(v, "btnGuardarRecursos"); escritura.iniciada(); consulta.liberar();
        fx(() -> { assertTrue(v.tabla().isDisabled()); assertTrue(v.boton("btnGuardarRecursos").isDisabled()); return null; });
        escritura.liberar(); esperar(() -> !v.control.alertas.isEmpty()); terminado(v);
    }

    @Test void categoriasNoInvalidanCargaInicialLenta() throws Exception {
        Servicio s = new Servicio(); Puerta p = s.pausar("listarRecursos", List.of(UNO));
        Vista v = abrir(s); p.iniciada();
        esperarFx(() -> !v.combo("cbCategoriaRecurso").isDisabled());
        p.liberar(); lista(v, List.of(UNO));
    }

    @Test void cambioFiltroDuranteCargaInicialInvalidaResultado() throws Exception {
        Servicio s = new Servicio(); Puerta p = s.pausar("listarRecursos", List.of(UNO));
        Vista v = abrir(s); p.iniciada();
        fx(() -> { v.texto("txtFiltroDescripcionRecursos").setText("otro"); return null; });
        p.liberar(); lista(v, List.of());
    }

    @Test void idTardioNoReemplazaSeleccion() throws Exception {
        Servicio s = new Servicio(); Puerta p = s.pausar("generarNuevoId", "999999");
        Vista v = abrir(s); p.iniciada();
        CountDownLatch pulsoJavaFX = new CountDownLatch(1);
        Platform.runLater(pulsoJavaFX::countDown);
        assertTrue(pulsoJavaFX.await(1, TimeUnit.SECONDS));
        lista(v, List.of(UNO)); seleccionar(v);
        p.liberar();
        fx(() -> { assertEquals("123456", v.texto("txtIdRecurso").getText()); return null; });
    }

    @Test void errorIdSeExplicaYPermiteReintentar() throws Exception {
        Servicio s = new Servicio(); s.fallar("generarNuevoId", new IllegalStateException("red"));
        Vista v = abrir(s); esperar(() -> !v.control.alertas.isEmpty());
        assertEquals(RecursoViewController.MENSAJE_SIN_CONEXION_ID, v.control.alertas.getFirst().mensaje());
        fx(() -> { assertTrue(v.boton("btnGuardarRecursos").isDisabled()); return null; });
        pulsar(v, "btnLimpiarRecursos"); terminado(v);
        fx(() -> { assertEquals("345678", v.texto("txtIdRecurso").getText()); return null; });
    }

    @Test void categoriasFueraDeOrdenConservanSeleccionActual() throws Exception {
        Servicio s = new Servicio(); Vista v = vista(s); seleccionar(v);
        Puerta p = s.pausar("listarTodos", List.of());
        fx(() -> { v.control.cargarCategorias(); return null; }); p.iniciada();
        fx(() -> { v.control.cargarCategorias(); return null; });
        esperarFx(() -> !v.combo("cbCategoriaRecurso").isDisabled()); p.liberar();
        fx(() -> { assertSame(CAT, v.combo("cbCategoriaRecurso").getValue()); return null; });
    }

    @Test void errorCategoriasConservaDatosValidos() throws Exception {
        Servicio s = new Servicio(); Vista v = vista(s); seleccionar(v);
        s.fallar("listarTodos", new IllegalStateException("red"));
        fx(() -> { v.control.cargarCategorias(); return null; });
        esperar(() -> !v.control.alertas.isEmpty()); terminado(v);
        fx(() -> { assertEquals(List.of(CAT), v.combo("cbCategoriaRecurso").getItems());
            assertSame(CAT, v.combo("cbCategoriaRecurso").getValue()); return null; });
    }

    private static Vista abrir(Servicio s) throws Exception {
        return fx(() -> {
            Control control = new Control(s);
            FXMLLoader loader = new FXMLLoader(RecursosAsincronosTest.class.getResource("/Sistema/view/RecursosView.fxml"));
            loader.setControllerFactory(tipo -> control); loader.load();
            return new Vista(loader, control);
        });
    }
    private static Vista vista(Servicio s) throws Exception {
        Vista v = abrir(s); terminado(v); lista(v, List.of(UNO)); return v;
    }
    private static void terminado(Vista v) throws Exception {
        esperarFx(() -> !v.boton("btnGuardarRecursos").isDisabled() && !v.boton("btnLimpiarRecursos").isDisabled());
    }
    private static void seleccionar(Vista v) throws Exception {
        fx(() -> { v.tabla().getSelectionModel().select(UNO); return null; });
    }
    private static void formulario(Vista v) throws Exception {
        fx(() -> { v.texto("txtDescripcionRecurso").setText("Descripción editada");
            v.combo("cbCategoriaRecurso").setValue(CAT); return null; });
    }
    private static void buscar(Vista v, String texto) throws Exception {
        fx(() -> { v.texto("txtFiltroDescripcionRecursos").setText(texto);
            v.boton("btnFiltroBuscarRecursos").fire(); return null; });
    }
    private static void pulsar(Vista v, String id) throws Exception {
        fx(() -> { v.boton(id).fire(); return null; });
    }
    private static void lista(Vista v, List<RecursoDTO> lista) throws Exception {
        esperarFx(() -> !v.tabla().isDisabled() && v.tabla().getItems().equals(lista));
    }
    private static void esperarFx(BooleanSupplier condicion) throws Exception { esperar(() -> fx(condicion::getAsBoolean)); }
    private static void esperar(Callable<Boolean> condicion) throws Exception {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (System.nanoTime() < limite) { if (condicion.call()) return; Thread.sleep(10); }
        fail("No se alcanzó el estado esperado");
    }
    private static <T> T fx(Callable<T> accion) throws Exception {
        FutureTask<T> tarea = new FutureTask<>(accion); Platform.runLater(tarea); return tarea.get(10, TimeUnit.SECONDS);
    }
    private record Vista(FXMLLoader loader, Control control) {
        Button boton(String id) { return (Button) loader.getNamespace().get(id); }
        TextField texto(String id) { return (TextField) loader.getNamespace().get(id); }
        @SuppressWarnings("unchecked") ComboBox<CategoriaDTO> combo(String id) { return (ComboBox<CategoriaDTO>) loader.getNamespace().get(id); }
        @SuppressWarnings("unchecked") TableView<RecursoDTO> tabla() { return (TableView<RecursoDTO>) loader.getNamespace().get("tvwRecursos"); }
    }
    private record Alerta(Alert.AlertType tipo, String mensaje) {}
    private static class Control extends RecursoViewController {
        final List<Alerta> alertas = new CopyOnWriteArrayList<>();
        boolean confirmar = true;
        Control(Servicio s) { super(s.proxy(IRecursoService.class), s.proxy(ICategoriaService.class)); }
        @Override void mostrarAlerta(Alert.AlertType tipo, String titulo, String mensaje) {
            assertTrue(Platform.isFxApplicationThread()); alertas.add(new Alerta(tipo, mensaje));
        }
        @Override boolean confirmarBorrado(RecursoDTO recurso) { assertTrue(Platform.isFxApplicationThread()); return confirmar; }
        @Override public void cargarTabla(List<RecursoDTO> recursos) {
            assertTrue(Platform.isFxApplicationThread()); super.cargarTabla(recursos);
        }
    }
    private static class Servicio {
        final Map<String, ConcurrentLinkedQueue<Callable<Object>>> respuestas = new ConcurrentHashMap<>();
        final Map<String, AtomicInteger> llamadas = new ConcurrentHashMap<>();
        volatile String ultimoId;
        void responder(String nombre, Object valor) { agregar(nombre, () -> valor); }
        void fallar(String nombre, RuntimeException error) { agregar(nombre, () -> { throw error; }); }
        void agregar(String nombre, Callable<Object> respuesta) { respuestas.computeIfAbsent(nombre, k -> new ConcurrentLinkedQueue<>()).add(respuesta); }
        int cantidad(String nombre) { return llamadas.getOrDefault(nombre, new AtomicInteger()).get(); }
        Puerta pausar(String nombre, Object resultado) {
            Puerta p = new Puerta(resultado); agregar(nombre, p::obtener); return p;
        }
        <T> T proxy(Class<T> tipo) {
            return tipo.cast(Proxy.newProxyInstance(tipo.getClassLoader(), new Class<?>[]{tipo}, (proxy, metodo, args) -> {
                assertFalse(Platform.isFxApplicationThread(), metodo.getName());
                llamadas.computeIfAbsent(metodo.getName(), k -> new AtomicInteger()).incrementAndGet();
                if (metodo.getName().equals("eliminarRecurso")) ultimoId = (String) args[0];
                var cola = respuestas.get(metodo.getName());
                Callable<Object> respuesta = cola == null ? null : cola.poll();
                if (respuesta != null) return respuesta.call();
                return switch (metodo.getName()) {
                    case "listarRecursos", "filtrarRecursos" -> List.of(UNO);
                    case "listarTodos" -> List.of(CAT);
                    case "generarNuevoId" -> "345678";
                    case "buscarRecursoID" -> null;
                    default -> ResultadoOperacionDTO.exito();
                };
            }));
        }
    }
    private static class Puerta {
        final Object resultado;
        final CountDownLatch inicio = new CountDownLatch(1), permiso = new CountDownLatch(1);
        volatile Thread hilo;
        Puerta(Object resultado) { this.resultado = resultado; }
        Object obtener() throws Exception {
            hilo = Thread.currentThread(); inicio.countDown();
            assertTrue(permiso.await(10, TimeUnit.SECONDS));
            if (resultado instanceof RuntimeException error) throw error;
            return resultado;
        }
        void iniciada() throws Exception { assertTrue(inicio.await(5, TimeUnit.SECONDS)); }
        void liberar() throws Exception {
            permiso.countDown(); hilo.join(5000); assertFalse(hilo.isAlive());
            // Task ya encoló su notificación: la barrera procesa también callbacks descartados.
            fx(() -> null);
        }
    }
}
