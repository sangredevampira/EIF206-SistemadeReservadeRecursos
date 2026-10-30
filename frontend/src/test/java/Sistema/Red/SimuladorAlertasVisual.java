
package Sistema.Red;

import ui.AlertasPanelController;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class SimuladorAlertasVisual extends Application {

    @Override
    public void start(Stage escenario) throws Exception {

        // 1. Cargar el panel JavaFX existente.
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource(
                        "/Sistema/view/AlertasPanelView.fxml"
                )
        );

        Parent vista = loader.load();

        AlertasPanelController panel = loader.getController();

        escenario.setTitle("Prueba de notificaciones TCP");
        escenario.setScene(new Scene(vista, 430, 450));
        escenario.show();

        // 2. Crear un servidor TCP temporal.
        ServerSocket servidor = new ServerSocket(0);

        Thread hiloServidor = new Thread(() -> {

            try (servidor;
                 Socket conexion = servidor.accept();
                 BufferedWriter salida = new BufferedWriter(
                         new OutputStreamWriter(
                                 conexion.getOutputStream(),
                                 StandardCharsets.UTF_8))) {

                ObjectMapper json = new ObjectMapper();

                ObjectNode evento = json.createObjectNode();

                evento.put("tipo", "EVENTO");
                evento.put("operacion", "RESERVA_CREADA");

                ObjectNode datos = evento.putObject("datos");

                datos.put("idReserva", "RES-001");
                datos.put("idFuncionario", "FUNC-001");
                datos.put("nombreFuncionario", "Juan Mora");
                datos.put("nombreActividad", "Reunión de prueba");
                datos.put("fecha", "2026-10-29");
                datos.put("horaInicio", "08:00");
                datos.put("horaFin", "09:30");
                datos.put("estado", "ACTIVA");
                datos.putArray("recursosReservados");

                Thread.sleep(700);

                salida.write(json.writeValueAsString(evento));
                salida.newLine();
                salida.flush();

            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        hiloServidor.setDaemon(true);
        hiloServidor.start();

        // 3. Conectar el cliente y procesar el evento.
        ProcesadorAlertaReserva procesador =
                new ProcesadorAlertaReserva();

        ClienteRed cliente = ClienteRed.getInstancia();

        cliente.setReceptorAlertas(mensaje -> {

            try {
                procesador.procesar(mensaje)
                        .ifPresent(panel::agregarAlerta);

            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        cliente.conectar(
                "127.0.0.1",
                servidor.getLocalPort()
        );

        escenario.setOnCloseRequest(evento -> cliente.cerrar());
    }

    public static void main(String[] args) {
        launch(args);
    }
}
