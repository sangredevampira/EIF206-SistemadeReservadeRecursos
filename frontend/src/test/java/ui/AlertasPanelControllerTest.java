package ui;

import Sistema.DTO.EstadoReserva;
import Sistema.DTO.ReservaDTO;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.TableView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.awt.GraphicsEnvironment;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class AlertasPanelControllerTest {

    @BeforeAll
    static void iniciarJavaFX() throws Exception {
        assumeFalse(
                GraphicsEnvironment.isHeadless(),
                "El panel de alertas requiere un entorno gráfico JavaFX."
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
    void agregaYLimpiaAlertasDesdeUnHiloExterno() throws Exception {
        PanelCargado panel = ejecutarEnJavaFX(() -> {
            FXMLLoader cargador = new FXMLLoader(getClass().getResource(
                    "/Sistema/view/AlertasPanelView.fxml"
            ));
            assertNotNull(cargador.getLocation());
            cargador.load();

            @SuppressWarnings("unchecked")
            TableView<ReservaDTO> tabla =
                    (TableView<ReservaDTO>) cargador.getNamespace().get(
                            "tablaAlertas"
                    );

            return new PanelCargado(cargador.getController(), tabla);
        });
        ReservaDTO reserva = crearReserva();

        ejecutarFueraDeJavaFX(() -> panel.controlador().agregarAlerta(reserva));
        esperarEventosJavaFX();

        ejecutarEnJavaFX(() -> {
            assertEquals(1, panel.tabla().getItems().size());
            assertSame(reserva, panel.tabla().getItems().get(0));
            return null;
        });

        ejecutarFueraDeJavaFX(panel.controlador()::limpiarAlertas);
        esperarEventosJavaFX();

        ejecutarEnJavaFX(() -> {
            assertTrue(panel.tabla().getItems().isEmpty());
            return null;
        });
    }

    private ReservaDTO crearReserva() {
        return new ReservaDTO(
                "RES-001",
                "FUNC-001",
                "Sofía",
                "Reunión de coordinación",
                LocalDate.of(2026, 10, 30),
                LocalTime.of(8, 0),
                LocalTime.of(9, 0),
                List.of(),
                EstadoReserva.ACTIVA
        );
    }

    private void ejecutarFueraDeJavaFX(Runnable accion) throws Exception {
        Thread hilo = new Thread(accion, "Prueba-Alertas");
        hilo.start();
        hilo.join(5000);
        assertFalse(hilo.isAlive());
    }

    private void esperarEventosJavaFX() throws Exception {
        ejecutarEnJavaFX(() -> null);
    }

    private static <T> T ejecutarEnJavaFX(Callable<T> accion)
            throws Exception {
        if (Platform.isFxApplicationThread()) {
            return accion.call();
        }

        FutureTask<T> tarea = new FutureTask<>(accion);
        Platform.runLater(tarea);
        return tarea.get(10, TimeUnit.SECONDS);
    }

    private record PanelCargado(
            AlertasPanelController controlador,
            TableView<ReservaDTO> tabla
    ) {
    }
}
