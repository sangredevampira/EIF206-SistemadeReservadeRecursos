
package Sistema.Red;

import Sistema.DTO.ReservaDTO;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ProcesadorAlertaReservaTest {

    private final ProcesadorAlertaReserva procesador =
            new ProcesadorAlertaReserva();

    @Test
    void convierteNotificacionEnReservaDTO() throws Exception {

        String mensaje = """
                {
                  "tipo": "EVENTO",
                  "operacion": "RESERVA_CREADA",
                  "datos": {
                    "idReserva": "RES-001",
                    "idFuncionario": "FUNC-001",
                    "nombreFuncionario": "Juan Mora",
                    "nombreActividad": "Reunión de coordinación",
                    "fecha": "2026-10-24",
                    "horaInicio": "08:00",
                    "horaFin": "09:30",
                    "recursosReservados": [],
                    "estado": "ACTIVA"
                  }
                }
                """;

        Optional<ReservaDTO> resultado =
                procesador.procesar(mensaje);

        assertTrue(resultado.isPresent());

        ReservaDTO reserva = resultado.get();

        assertEquals("RES-001", reserva.getIdReserva());

        assertEquals(
                "Reunión de coordinación",
                reserva.getNombreActividad()
        );

        assertEquals(
                LocalDate.of(2026, 10, 24),
                reserva.getFecha()
        );

        assertEquals(
                LocalTime.of(8, 0),
                reserva.getHoraInicio()
        );
    }

    @Test
    void ignoraMensajesQueNoSonAlertasDeReserva()
            throws Exception {

        String mensaje = """
                {
                  "tipo": "RESPUESTA",
                  "operacion": "CONSULTAR_RECURSOS",
                  "datos": []
                }
                """;

        Optional<ReservaDTO> resultado =
                procesador.procesar(mensaje);

        assertTrue(resultado.isEmpty());
    }

    @Test
    void rechazaEventoSinDatosDeReserva() {
        String mensaje = """
                {
                  "tipo": "EVENTO",
                  "operacion": "RESERVA_CREADA"
                }
                """;

        Exception error = assertThrows(
                java.io.IOException.class,
                () -> procesador.procesar(mensaje)
        );

        assertTrue(error.getMessage().contains("datos de la reserva"));
    }

    @Test
    void rechazaEventoSinIdDeReserva() {
        String mensaje = """
                {
                  "tipo": "EVENTO",
                  "operacion": "RESERVA_CREADA",
                  "datos": {
                    "nombreActividad": "Reunión"
                  }
                }
                """;

        Exception error = assertThrows(
                java.io.IOException.class,
                () -> procesador.procesar(mensaje)
        );

        assertTrue(error.getMessage().contains("ID de reserva válido"));
    }
}
