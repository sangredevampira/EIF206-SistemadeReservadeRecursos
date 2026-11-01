package Sistema.Red;

import Sistema.DTO.CategoriaDTO;
import Sistema.DTO.EstadoReserva;
import Sistema.DTO.RecursoDTO;
import Sistema.DTO.ReservaDTO;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

// Verifica que el evento armado con JsonRed lo acepta el ProcesadorAlertaReserva del Frontend.
class MensajeRedCompatibilidadTest {

    @Test
    void eventoReservaCreadaEsAceptadoPorElProcesador() throws Exception {
        RecursoDTO recurso = new RecursoDTO("R-1", "Proyector", new CategoriaDTO("C-1", "Audiovisual"));
        ReservaDTO reserva = new ReservaDTO("RES-1", "F-1", "Ana Mora", "Clase de redes",
                LocalDate.of(2026, 11, 3), LocalTime.of(6, 0), LocalTime.of(8, 0),
                List.of(recurso), EstadoReserva.ACTIVA);

        String linea = JsonRed.aLinea(
                MensajeRed.evento(Operaciones.RESERVA_CREADA, JsonRed.aNodo(reserva)));

        Optional<ReservaDTO> resultado = new ProcesadorAlertaReserva().procesar(linea);

        assertTrue(resultado.isPresent());
        ReservaDTO recibida = resultado.get();
        assertEquals("RES-1", recibida.getIdReserva());
        assertEquals("Ana Mora", recibida.getNombreFuncionario());
        assertEquals(LocalDate.of(2026, 11, 3), recibida.getFecha());
        assertEquals(LocalTime.of(6, 0), recibida.getHoraInicio());
        assertEquals(LocalTime.of(8, 0), recibida.getHoraFin());
        assertEquals(1, recibida.getRecursosReservados().size());
        assertEquals("Proyector", recibida.getRecursosReservados().get(0).getDescripcion());
    }
}
