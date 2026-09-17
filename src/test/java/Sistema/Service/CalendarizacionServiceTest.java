package Sistema.Service;

import Sistema.DTO.CategoriaDTO;
import Sistema.DTO.EstadoReserva;
import Sistema.DTO.RecursoDTO;
import Sistema.DTO.ReservaDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
class CalendarizacionServiceTest {

    private CalendarizacionService service;
    private RecursoDTO recurso;
    private LocalDate fecha;

    @BeforeEach
    void prepararDatosDePrueba() {
        service = new CalendarizacionService();

        CategoriaDTO categoria = new CategoriaDTO("CAT-000001", "Sala de estudio");
        recurso = new RecursoDTO("REC-000001", "Sala A", categoria);
        fecha = LocalDate.of(2026, 9, 20);
    }

    @Test
    void generarHorasDelDiaCreaBloquesDeTreintaMinutos() {
        List<String> horas = service.generarHorasDelDia();

        assertEquals(48, horas.size());
        assertEquals("00:00", horas.get(0));
        assertEquals("00:30", horas.get(1));
        assertEquals("23:30", horas.get(47));
    }

    @Test
    void reservaDeSieteTreintaAOchoTreintaMarcaLosBloquesCorrectos() {
        ReservaDTO reserva = new ReservaDTO(
                "RES-000001",
                "FUNC-001",
                "Sofia",
                "Reunión",
                fecha,
                LocalTime.of(7, 30),
                LocalTime.of(8, 30),
                List.of(recurso),
                EstadoReserva.ACTIVA
        );

        List<ReservaDTO> reservas = List.of(reserva);

        assertEquals("Disponible", service.obtenerEstadoRecurso(recurso, fecha, "07:00", reservas));
        assertTrue(service.obtenerEstadoRecurso(recurso, fecha, "07:30", reservas).startsWith("Reservado"));
        assertTrue(service.obtenerEstadoRecurso(recurso, fecha, "08:00", reservas).startsWith("Reservado"));
        assertEquals("Disponible", service.obtenerEstadoRecurso(recurso, fecha, "08:30", reservas));
    }
}