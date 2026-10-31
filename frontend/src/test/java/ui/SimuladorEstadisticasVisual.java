package ui;

import Sistema.DTO.EstadisticaCategoriaDTO;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableView;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Simulador exclusivamente visual. Inserta datos conocidos directamente en
 * las tablas y gráficos; no prueba cálculos, persistencia ni comunicación TCP.
 */
public class SimuladorEstadisticasVisual extends Application {

    @Override
    public void start(Stage escenario) throws Exception {
        Path archivoReservaHeredado = Path.of(
                "src",
                "data",
                "reservas.json"
        );
        boolean archivoReservaExistia = Files.exists(archivoReservaHeredado);
        FXMLLoader cargadorRecursos = crearCargador(
                "EstadisticasRecursosView.fxml"
        );
        FXMLLoader cargadorActividades = crearCargador(
                "EstadisticasActividadesView.fxml"
        );
        Parent vistaRecursos;
        Parent vistaActividades;

        try {
            vistaRecursos = cargadorRecursos.load();
            vistaActividades = cargadorActividades.load();
        } finally {
            limpiarReservaVaciaCreadaPorControlador(
                    archivoReservaHeredado,
                    archivoReservaExistia
            );
        }

        cargarRecursosSimulados(cargadorRecursos);
        cargarActividadesSimuladas(cargadorActividades);

        SplitPane contenido = new SplitPane(vistaRecursos, vistaActividades);
        contenido.setDividerPositions(0.5);

        escenario.setTitle("Simulador visual de estadísticas");
        escenario.setScene(new Scene(contenido, 1100, 650));
        escenario.show();

        enfocarPdfSolicitado(cargadorRecursos, cargadorActividades);
        guardarCapturaSiFueSolicitada(escenario);
    }

    private FXMLLoader crearCargador(String archivo) {
        return new FXMLLoader(getClass().getResource(
                "/Sistema/view/" + archivo
        ));
    }

    private void cargarRecursosSimulados(FXMLLoader cargador) {
        Map<String, Object> elementos = cargador.getNamespace();
        List<EstadisticaCategoriaDTO> datos = List.of(
                new EstadisticaCategoriaDTO("Computadoras", 8),
                new EstadisticaCategoriaDTO("Proyectores", 5),
                new EstadisticaCategoriaDTO("Cámaras", 3)
        );

        @SuppressWarnings("unchecked")
        TableView<EstadisticaCategoriaDTO> tabla =
                (TableView<EstadisticaCategoriaDTO>) elementos.get(
                        "tvEstadisticasRecursos"
                );
        tabla.setItems(FXCollections.observableArrayList(datos));

        @SuppressWarnings("unchecked")
        BarChart<Number, String> grafico =
                (BarChart<Number, String>) elementos.get(
                        "barChartRecursos"
                );
        XYChart.Series<Number, String> serie = new XYChart.Series<>();
        serie.setName("Recursos reservados");

        for (EstadisticaCategoriaDTO dato : datos) {
            serie.getData().add(new XYChart.Data<>(
                    dato.getCantidad(),
                    dato.getCategoria()
            ));
        }

        grafico.getData().clear();
        grafico.getData().add(serie);
        NumberAxis eje = (NumberAxis) grafico.getXAxis();
        eje.setAutoRanging(false);
        eje.setLowerBound(0);
        eje.setUpperBound(9);
        eje.setTickUnit(1);

        establecerPeriodo(elementos, "Recursos");
        Object controlador = cargador.getController();
        establecerCampo(controlador, "datosCalculados", true);
        establecerCampo(
                controlador,
                "desdeCargado",
                LocalDate.of(2026, 10, 1)
        );
        establecerCampo(
                controlador,
                "hastaCargado",
                LocalDate.of(2026, 10, 31)
        );
        ((Label) elementos.get("lblEstadoRecursos")).setText(
                "Datos simulados: puede probar el botón Generar PDF."
        );
    }

    private void cargarActividadesSimuladas(FXMLLoader cargador)
            throws Exception {
        Map<String, Object> elementos = cargador.getNamespace();
        LocalDate primeraSemana = LocalDate.of(2026, 10, 5);
        Map<LocalDate, Integer> datos = new TreeMap<>();
        datos.put(primeraSemana, 4);
        datos.put(primeraSemana.plusWeeks(1), 7);
        datos.put(primeraSemana.plusWeeks(2), 2);

        Object controlador = cargador.getController();
        Method mostrar = controlador.getClass().getDeclaredMethod(
                "mostrarEstadisticas",
                Map.class
        );
        mostrar.setAccessible(true);
        mostrar.invoke(controlador, datos);

        establecerPeriodo(elementos, "Actividades");
        establecerCampo(
                controlador,
                "fechaDesdeCargada",
                LocalDate.of(2026, 10, 1)
        );
        establecerCampo(
                controlador,
                "fechaHastaCargada",
                LocalDate.of(2026, 10, 31)
        );
        ((Label) elementos.get("lblEstadoActividades")).setText(
                "Datos simulados: puede probar el botón Generar PDF."
        );
    }

    private void establecerPeriodo(
            Map<String, Object> elementos,
            String sufijo
    ) {
        ((DatePicker) elementos.get("dpDesde" + sufijo)).setValue(
                LocalDate.of(2026, 10, 1)
        );
        ((DatePicker) elementos.get("dpHasta" + sufijo)).setValue(
                LocalDate.of(2026, 10, 31)
        );

        ((Button) elementos.get("btnCargar" + sufijo)).setDisable(true);
    }

    private void establecerCampo(
            Object objetivo,
            String nombre,
            Object valor
    ) {
        try {
            Field campo = objetivo.getClass().getDeclaredField(nombre);
            campo.setAccessible(true);
            campo.set(objetivo, valor);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "No fue posible preparar el simulador visual.",
                    e
            );
        }
    }

    private void guardarCapturaSiFueSolicitada(Stage escenario) {
        String rutaCaptura = System.getProperty("simulador.captura");

        if (rutaCaptura == null || rutaCaptura.isBlank()) {
            return;
        }

        Platform.runLater(() -> {
            try {
                WritableImage captura = escenario.getScene().snapshot(null);
                ImageIO.write(
                        SwingFXUtils.fromFXImage(captura, null),
                        "png",
                        new File(rutaCaptura)
                );
            } catch (Exception e) {
                throw new IllegalStateException(
                        "No fue posible guardar la captura del simulador.",
                        e
                );
            } finally {
                Platform.exit();
            }
        });
    }

    private void enfocarPdfSolicitado(
            FXMLLoader cargadorRecursos,
            FXMLLoader cargadorActividades
    ) {
        boolean enfocarActividades = "actividades".equalsIgnoreCase(
                System.getProperty("simulador.focoPdf", "recursos")
        );
        FXMLLoader cargador = enfocarActividades
                ? cargadorActividades
                : cargadorRecursos;
        String id = enfocarActividades
                ? "btnGenerarPdfActividades"
                : "btnGenerarPdfRecursos";

        Platform.runLater(() -> ((Button) cargador.getNamespace().get(id))
                .requestFocus());
    }

    private void limpiarReservaVaciaCreadaPorControlador(
            Path archivo,
            boolean existiaAntes
    ) {
        if (existiaAntes || !Files.exists(archivo)) {
            return;
        }

        try {
            String contenido = Files.readString(archivo)
                    .replaceAll("\\s", "");

            if (!"[]".equals(contenido)) {
                return;
            }

            Files.delete(archivo);
            try {
                Files.deleteIfExists(archivo.getParent());
            } catch (DirectoryNotEmptyException e) {
                // Se preservan otros datos que ya existieran en el directorio.
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    "No fue posible limpiar los datos vacíos del simulador.",
                    e
            );
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
