package ui;

import Sistema.DTO.CalendarizacionDTO;
import Sistema.DTO.CategoriaDTO;
import Sistema.DTO.DisponibilidadRecursoDTO;
import Sistema.DTO.ProgramacionActividadesDTO;
import Sistema.DTO.RecursoDTO;
import Sistema.DTO.ResultadoOperacionDTO;
import Sistema.Service.ICalendarizacionService;
import Sistema.Service.ICategoriaService;
import Sistema.Service.IProgramacionActividadesService;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.awt.GraphicsEnvironment;
import java.net.URL;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class ConsultasAsincronasVistasTest {

    private static final CategoriaDTO CATEGORIA_A =
            new CategoriaDTO("CAT-000001", "Salas");
    private static final CategoriaDTO CATEGORIA_B =
            new CategoriaDTO("CAT-000002", "Equipos");

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

    @Test
    void calendarizacionDescartaRespuestaSiCambiaLaFecha() throws Exception {
        RespuestaControlada<CalendarizacionDTO> respuesta =
                RespuestaControlada.exito(calendarizacion("Respuesta anterior"));
        CalendarizacionControlada servicio =
                new CalendarizacionControlada(respuesta);
        VistaCargada vista = cargarCalendarizacion(servicio);
        LocalDate fechaConsultada = LocalDate.of(2026, 11, 2);

        seleccionarCalendarizacion(vista, fechaConsultada, CATEGORIA_A);
        respuesta.esperarInicio();
        ejecutarEnJavaFX(() -> {
            fecha(vista, "dpFecha").setValue(fechaConsultada.plusDays(1));
            return null;
        });

        respuesta.responder();
        esperarTexto(vista, "lblEstado", "Los filtros cambiaron");

        ejecutarEnJavaFX(() -> {
            assertTrue(tabla(vista, "tvCalendarizacion").getItems().isEmpty());
            assertEquals(1, tabla(vista, "tvCalendarizacion").getColumns().size());
            return null;
        });
    }

    @Test
    void calendarizacionDescartaRespuestaSiCambiaLaCategoria() throws Exception {
        RespuestaControlada<CalendarizacionDTO> respuesta =
                RespuestaControlada.exito(calendarizacion("Respuesta anterior"));
        CalendarizacionControlada servicio =
                new CalendarizacionControlada(respuesta);
        VistaCargada vista = cargarCalendarizacion(servicio);

        seleccionarCalendarizacion(
                vista,
                LocalDate.of(2026, 11, 2),
                CATEGORIA_A
        );
        respuesta.esperarInicio();
        ejecutarEnJavaFX(() -> {
            categoria(vista, "cbCategoria").setValue(CATEGORIA_B);
            return null;
        });

        respuesta.responder();
        esperarTexto(vista, "lblEstado", "Los filtros cambiaron");

        ejecutarEnJavaFX(() -> {
            assertTrue(tabla(vista, "tvCalendarizacion").getItems().isEmpty());
            return null;
        });
    }

    @Test
    void calendarizacionNoReactivaRespuestaAlVolverALosFiltrosOriginales()
            throws Exception {
        LocalDate fechaOriginal = LocalDate.of(2026, 11, 2);
        RespuestaControlada<CalendarizacionDTO> respuesta =
                RespuestaControlada.exito(calendarizacion("Respuesta invalidada"));
        CalendarizacionControlada servicio =
                new CalendarizacionControlada(respuesta);
        VistaCargada vista = cargarCalendarizacion(servicio);

        seleccionarCalendarizacion(vista, fechaOriginal, CATEGORIA_A);
        respuesta.esperarInicio();
        ejecutarEnJavaFX(() -> {
            fecha(vista, "dpFecha").setValue(fechaOriginal.plusDays(1));
            fecha(vista, "dpFecha").setValue(fechaOriginal);
            return null;
        });

        respuesta.responder();
        esperarProcesamiento(respuesta);

        ejecutarEnJavaFX(() -> {
            assertTrue(tabla(vista, "tvCalendarizacion").getItems().isEmpty());
            assertEquals(1, tabla(vista, "tvCalendarizacion").getColumns().size());
            assertTrue(etiqueta(vista, "lblEstado").getText().contains(
                    "Los filtros cambiaron"
            ));
            return null;
        });
    }

    @Test
    void calendarizacionSoloMuestraLaConsultaMasRecienteAunqueTerminePrimero()
            throws Exception {
        RespuestaControlada<CalendarizacionDTO> anterior =
                RespuestaControlada.exito(calendarizacion("Respuesta anterior"));
        RespuestaControlada<CalendarizacionDTO> actual =
                RespuestaControlada.exito(calendarizacion("Respuesta actual"));
        CalendarizacionControlada servicio =
                new CalendarizacionControlada(anterior, actual);
        VistaCargada vista = cargarCalendarizacion(servicio);

        seleccionarCalendarizacion(
                vista,
                LocalDate.of(2026, 11, 2),
                CATEGORIA_A
        );
        anterior.esperarInicio();
        seleccionarCalendarizacion(
                vista,
                LocalDate.of(2026, 11, 9),
                CATEGORIA_B
        );
        actual.esperarInicio();

        actual.responder();
        esperarTexto(vista, "lblEstado", "cargada correctamente");
        verificarColumnaCalendarizacion(vista, "Respuesta actual");

        anterior.responder();
        esperarProcesamiento(anterior);
        verificarColumnaCalendarizacion(vista, "Respuesta actual");
    }

    @Test
    void errorTardioDeCalendarizacionNoBorraResultadoMasReciente()
            throws Exception {
        RespuestaControlada<CalendarizacionDTO> anterior =
                RespuestaControlada.error(new IllegalStateException("Error anterior"));
        RespuestaControlada<CalendarizacionDTO> actual =
                RespuestaControlada.exito(calendarizacion("Resultado vigente"));
        CalendarizacionControlada servicio =
                new CalendarizacionControlada(anterior, actual);
        VistaCargada vista = cargarCalendarizacion(servicio);

        seleccionarCalendarizacion(
                vista,
                LocalDate.of(2026, 11, 2),
                CATEGORIA_A
        );
        anterior.esperarInicio();
        seleccionarCalendarizacion(
                vista,
                LocalDate.of(2026, 11, 9),
                CATEGORIA_B
        );
        actual.esperarInicio();

        actual.responder();
        esperarTexto(vista, "lblEstado", "cargada correctamente");
        anterior.responder();
        esperarProcesamiento(anterior);

        verificarColumnaCalendarizacion(vista, "Resultado vigente");
        ejecutarEnJavaFX(() -> {
            assertEquals(
                    "Calendarización cargada correctamente.",
                    etiqueta(vista, "lblEstado").getText()
            );
            return null;
        });
    }

    @Test
    void actividadesDescartaRespuestaSiCambiaElDiaAunqueSeaLaMismaSemana()
            throws Exception {
        LocalDate lunes = LocalDate.of(2026, 11, 2);
        RespuestaControlada<ProgramacionActividadesDTO> respuesta =
                RespuestaControlada.exito(programacion(lunes, "Respuesta anterior"));
        ActividadesControladas servicio = new ActividadesControladas(respuesta);
        VistaCargada vista = cargarActividades(servicio);

        seleccionarActividad(vista, lunes);
        respuesta.esperarInicio();
        ejecutarEnJavaFX(() -> {
            fecha(vista, "dpFechaReferencia").setValue(lunes.plusDays(1));
            return null;
        });

        respuesta.responder();
        esperarTexto(vista, "lblEstado", "La fecha cambió");

        ejecutarEnJavaFX(() -> {
            assertTrue(tabla(vista, "tvActividades").getItems().isEmpty());
            assertEquals(
                    "Semana no seleccionada",
                    etiqueta(vista, "lblRangoSemana").getText()
            );
            return null;
        });
    }

    @Test
    void actividadesSoloMuestraConsultaMasRecienteYPermiteVolverAFechaOriginal()
            throws Exception {
        LocalDate fechaOriginal = LocalDate.of(2026, 11, 2);
        LocalDate fechaIntermedia = fechaOriginal.plusWeeks(1);
        RespuestaControlada<ProgramacionActividadesDTO> primera =
                RespuestaControlada.exito(programacion(fechaOriginal, "Primera"));
        RespuestaControlada<ProgramacionActividadesDTO> intermedia =
                RespuestaControlada.exito(programacion(fechaIntermedia, "Intermedia"));
        RespuestaControlada<ProgramacionActividadesDTO> vigente =
                RespuestaControlada.exito(programacion(fechaOriginal, "Vigente"));
        ActividadesControladas servicio =
                new ActividadesControladas(primera, intermedia, vigente);
        VistaCargada vista = cargarActividades(servicio);

        seleccionarActividad(vista, fechaOriginal);
        primera.esperarInicio();
        seleccionarActividad(vista, fechaIntermedia);
        intermedia.esperarInicio();
        seleccionarActividad(vista, fechaOriginal);
        vigente.esperarInicio();

        vigente.responder();
        esperarTexto(vista, "lblEstado", "cargada correctamente");
        verificarActividad(vista, "Vigente");

        intermedia.responder();
        primera.responder();
        esperarProcesamiento(intermedia);
        esperarProcesamiento(primera);
        verificarActividad(vista, "Vigente");
    }

    @Test
    void errorTardioDeActividadesNoBorraResultadoMasReciente()
            throws Exception {
        LocalDate anteriorFecha = LocalDate.of(2026, 11, 2);
        LocalDate actualFecha = anteriorFecha.plusWeeks(1);
        RespuestaControlada<ProgramacionActividadesDTO> anterior =
                RespuestaControlada.error(new IllegalStateException("Error anterior"));
        RespuestaControlada<ProgramacionActividadesDTO> actual =
                RespuestaControlada.exito(programacion(actualFecha, "Actividad vigente"));
        ActividadesControladas servicio =
                new ActividadesControladas(anterior, actual);
        VistaCargada vista = cargarActividades(servicio);

        seleccionarActividad(vista, anteriorFecha);
        anterior.esperarInicio();
        seleccionarActividad(vista, actualFecha);
        actual.esperarInicio();

        actual.responder();
        esperarTexto(vista, "lblEstado", "cargada correctamente");
        anterior.responder();
        esperarProcesamiento(anterior);

        verificarActividad(vista, "Actividad vigente");
        ejecutarEnJavaFX(() -> {
            assertEquals(
                    "Programación semanal cargada correctamente.",
                    etiqueta(vista, "lblEstado").getText()
            );
            return null;
        });
    }

    private static VistaCargada cargarCalendarizacion(
            ICalendarizacionService servicio
    ) throws Exception {
        CalendarizacionController controlador = new CalendarizacionController(
                new CategoriasFijas(),
                servicio
        );
        VistaCargada vista = ejecutarEnJavaFX(() -> cargarVista(
                "CalendarizacionRecursosView.fxml",
                controlador
        ));
        esperarHasta(() -> ejecutarEnJavaFX(() ->
                categoria(vista, "cbCategoria").getItems().size() == 2
        ));
        return vista;
    }

    private static VistaCargada cargarActividades(
            IProgramacionActividadesService servicio
    ) throws Exception {
        return ejecutarEnJavaFX(() -> cargarVista(
                "ProgramacionActividadesView.fxml",
                new ProgramacionActividadesController(servicio)
        ));
    }

    private static VistaCargada cargarVista(String archivo, Object controlador)
            throws Exception {
        URL recurso = ConsultasAsincronasVistasTest.class.getResource(
                "/Sistema/view/" + archivo
        );
        assertNotNull(recurso);
        FXMLLoader cargador = new FXMLLoader(recurso);
        cargador.setControllerFactory(tipo -> controlador);
        Parent raiz = cargador.load();
        return new VistaCargada(cargador, raiz);
    }

    private static void seleccionarCalendarizacion(
            VistaCargada vista,
            LocalDate valorFecha,
            CategoriaDTO valorCategoria
    ) throws Exception {
        ejecutarEnJavaFX(() -> {
            fecha(vista, "dpFecha").setValue(valorFecha);
            categoria(vista, "cbCategoria").setValue(valorCategoria);
            boton(vista, "btnCargar").fire();
            return null;
        });
    }

    private static void seleccionarActividad(VistaCargada vista, LocalDate valor)
            throws Exception {
        ejecutarEnJavaFX(() -> {
            fecha(vista, "dpFechaReferencia").setValue(valor);
            boton(vista, "btnCargar").fire();
            return null;
        });
    }

    private static void verificarColumnaCalendarizacion(
            VistaCargada vista,
            String encabezado
    ) throws Exception {
        ejecutarEnJavaFX(() -> {
            TableView<?> tabla = tabla(vista, "tvCalendarizacion");
            assertEquals(2, tabla.getColumns().size());
            assertEquals(encabezado, tabla.getColumns().get(1).getText());
            assertEquals(List.of("08:00"), tabla.getItems());
            return null;
        });
    }

    private static void verificarActividad(VistaCargada vista, String texto)
            throws Exception {
        ejecutarEnJavaFX(() -> {
            TableView<?> tabla = tabla(vista, "tvActividades");
            assertEquals(List.of("08:00"), tabla.getItems());
            assertEquals(texto, tabla.getColumns().get(1).getCellData(0));
            return null;
        });
    }

    private static CalendarizacionDTO calendarizacion(String recurso) {
        CalendarizacionDTO dto = new CalendarizacionDTO();
        dto.setHoras(List.of("08:00"));
        DisponibilidadRecursoDTO disponibilidad = new DisponibilidadRecursoDTO();
        disponibilidad.setRecurso(new RecursoDTO(
                "100001",
                recurso,
                CATEGORIA_A
        ));
        disponibilidad.setEstados(List.of("Disponible"));
        dto.setRecursos(List.of(disponibilidad));
        return dto;
    }

    private static ProgramacionActividadesDTO programacion(
            LocalDate lunes,
            String actividad
    ) {
        ProgramacionActividadesDTO dto = new ProgramacionActividadesDTO();
        dto.setHoras(List.of("08:00"));
        dto.setActividadesPorFecha(Map.of(
                lunes.toString(),
                Map.of("08:00", actividad)
        ));
        return dto;
    }

    private static void esperarTexto(
            VistaCargada vista,
            String id,
            String fragmento
    ) throws Exception {
        esperarHasta(() -> ejecutarEnJavaFX(() ->
                etiqueta(vista, id).getText().contains(fragmento)
        ));
    }

    private static void esperarProcesamiento(RespuestaControlada<?> respuesta)
            throws Exception {
        respuesta.esperarFinalizacion();
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

    private static Button boton(VistaCargada vista, String id) {
        return assertInstanceOf(Button.class, vista.cargador().getNamespace().get(id));
    }

    private static DatePicker fecha(VistaCargada vista, String id) {
        return assertInstanceOf(DatePicker.class, vista.cargador().getNamespace().get(id));
    }

    @SuppressWarnings("unchecked")
    private static ComboBox<CategoriaDTO> categoria(VistaCargada vista, String id) {
        return (ComboBox<CategoriaDTO>) assertInstanceOf(
                ComboBox.class,
                vista.cargador().getNamespace().get(id)
        );
    }

    private static Label etiqueta(VistaCargada vista, String id) {
        return assertInstanceOf(Label.class, vista.cargador().getNamespace().get(id));
    }

    private static TableView<?> tabla(VistaCargada vista, String id) {
        return assertInstanceOf(TableView.class, vista.cargador().getNamespace().get(id));
    }

    private static final class CategoriasFijas implements ICategoriaService {
        @Override
        public List<CategoriaDTO> listarTodos() {
            return List.of(CATEGORIA_A, CATEGORIA_B);
        }

        @Override
        public ResultadoOperacionDTO registrar(CategoriaDTO categoria) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ResultadoOperacionDTO modificar(CategoriaDTO categoria) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ResultadoOperacionDTO eliminar(String id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<CategoriaDTO> buscar(String descripcionParcial) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class CalendarizacionControlada
            implements ICalendarizacionService {
        private final List<RespuestaControlada<CalendarizacionDTO>> respuestas;
        private final AtomicInteger siguiente = new AtomicInteger();

        @SafeVarargs
        private CalendarizacionControlada(
                RespuestaControlada<CalendarizacionDTO>... respuestas
        ) {
            this.respuestas = List.of(respuestas);
        }

        @Override
        public CalendarizacionDTO consultar(String idCategoria, LocalDate fecha) {
            return respuestas.get(siguiente.getAndIncrement()).obtener();
        }
    }

    private static final class ActividadesControladas
            implements IProgramacionActividadesService {
        private final List<RespuestaControlada<ProgramacionActividadesDTO>> respuestas;
        private final AtomicInteger siguiente = new AtomicInteger();

        @SafeVarargs
        private ActividadesControladas(
                RespuestaControlada<ProgramacionActividadesDTO>... respuestas
        ) {
            this.respuestas = List.of(respuestas);
        }

        @Override
        public ProgramacionActividadesDTO consultar(
                LocalDate fechaDesde,
                LocalDate fechaHasta
        ) {
            return respuestas.get(siguiente.getAndIncrement()).obtener();
        }
    }

    private static final class RespuestaControlada<T> {
        private final T resultado;
        private final RuntimeException error;
        private final CountDownLatch iniciada = new CountDownLatch(1);
        private final CountDownLatch permitida = new CountDownLatch(1);
        private final CountDownLatch finalizada = new CountDownLatch(1);

        private RespuestaControlada(T resultado, RuntimeException error) {
            this.resultado = resultado;
            this.error = error;
        }

        private static <T> RespuestaControlada<T> exito(T resultado) {
            return new RespuestaControlada<>(resultado, null);
        }

        private static <T> RespuestaControlada<T> error(RuntimeException error) {
            return new RespuestaControlada<>(null, error);
        }

        private T obtener() {
            iniciada.countDown();
            try {
                if (!permitida.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("La prueba no liberó la respuesta a tiempo.");
                }
                if (error != null) throw error;
                return resultado;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("La consulta fue interrumpida.", e);
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

    private record VistaCargada(FXMLLoader cargador, Parent raiz) {
    }
}
