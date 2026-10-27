package Sistema.Service;

import Sistema.DTO.EstadoReserva;
import Sistema.DTO.ReservaDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ProgramacionActividadesServiceTest {

    private ProgramacionActividadesService service;
    private LocalDate fecha;

    @BeforeEach
    void prepararDatosDePrueba() {
        service = new ProgramacionActividadesService();
        fecha = LocalDate.of(2026, 9, 21);
    }

    @Test
    void reservaDeSieteAOchoApareceEnDosBloques() {
        ReservaDTO reserva = new ReservaDTO(
                "RES-000001",
                "FUNC-001",
                "Sofia",
                "Reunión",
                fecha,
                LocalTime.of(7, 0),
                LocalTime.of(8, 0),
                List.of(),
                EstadoReserva.ACTIVA
        );

        Map<LocalDate, Map<String, String>> programacion =
                service.construirProgramacion(List.of(reserva));

        Map<String, String> actividadesDelDia = programacion.get(fecha);

        assertNotNull(actividadesDelDia);
        assertEquals("Reunión\nSofia", actividadesDelDia.get("07:00"));
        assertEquals("Reunión\nSofia", actividadesDelDia.get("07:30"));
        assertFalse(actividadesDelDia.containsKey("08:00"));
    }

    @Test
    void reservaCanceladaNoApareceEnLaProgramacion() {
        ReservaDTO reservaCancelada = new ReservaDTO(
                "RES-000002",
                "FUNC-001",
                "Sofia",
                "Actividad cancelada",
                fecha,
                LocalTime.of(10, 0),
                LocalTime.of(11, 0),
                List.of(),
                EstadoReserva.CANCELADA
        );

        Map<LocalDate, Map<String, String>> programacion = service.construirProgramacion(List.of(reservaCancelada));
        assertTrue(programacion.isEmpty());
    }
}