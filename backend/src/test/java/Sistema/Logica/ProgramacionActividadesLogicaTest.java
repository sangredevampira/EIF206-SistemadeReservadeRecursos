package Sistema.Logica;

import Sistema.Datos.ReservaDatos;
import Sistema.DTO.EstadoReserva;
import Sistema.DTO.ProgramacionActividadesDTO;
import Sistema.DTO.ReservaDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

// Sin MySQL: ReservaLogica real con una ReservaDatos falsa, así la validación de fechas es la verdadera.
class ProgramacionActividadesLogicaTest {

    static class ReservaDatosFalsa extends ReservaDatos {
        List<ReservaDTO> reservas = List.of();

        @Override
        public List<ReservaDTO> filtrarReservasRangoFechas(LocalDate desde, LocalDate hasta) {
            return reservas;
        }
    }

    private ReservaDatosFalsa datos;
    private ProgramacionActividadesLogica logica;
    private final LocalDate fecha = LocalDate.of(2026, 9, 21);

    @BeforeEach
    void preparar() {
        datos = new ReservaDatosFalsa();
        logica = new ProgramacionActividadesLogica(new ReservaLogica(datos, null, null));
    }

    private ReservaDTO reserva(String actividad, String funcionario, int horaIni, int horaFin, EstadoReserva estado) {
        return new ReservaDTO("RES-000001", "FUNC-001", funcionario, actividad, fecha,
                LocalTime.of(horaIni, 0), LocalTime.of(horaFin, 0), List.of(), estado);
    }

    @Test
    void lasClavesDeFechaSonTextoIso() {
        datos.reservas = List.of(reserva("Reunión", "Sofia", 7, 8, EstadoReserva.ACTIVA));

        ProgramacionActividadesDTO resultado = logica.consultar(fecha, fecha);

        assertEquals(48, resultado.getHoras().size());
        assertEquals(java.util.Set.of("2026-09-21"), resultado.getActividadesPorFecha().keySet());
        Map<String, String> dia = resultado.getActividadesPorFecha().get("2026-09-21");
        assertEquals("Reunión\nSofia", dia.get("07:00"));
        assertEquals("Reunión\nSofia", dia.get("07:30"));
        assertFalse(dia.containsKey("08:00"));
    }

    @Test
    void dosReservasEnElMismoBloqueSeUnenConSaltoDeLineaDoble() {
        datos.reservas = List.of(
                reserva("Reunión", "Sofia", 7, 8, EstadoReserva.ACTIVA),
                reserva("Clase", "Ana", 7, 8, EstadoReserva.ACTIVA));

        Map<String, String> dia = logica.consultar(fecha, fecha).getActividadesPorFecha().get("2026-09-21");

        assertEquals("Reunión\nSofia\n\nClase\nAna", dia.get("07:00"));
    }

    @Test
    void unaReservaCanceladaSeIgnora() {
        datos.reservas = List.of(reserva("Cancelada", "Sofia", 10, 11, EstadoReserva.CANCELADA));

        ProgramacionActividadesDTO resultado = logica.consultar(fecha, fecha);

        assertTrue(resultado.getActividadesPorFecha().isEmpty());
    }

    @Test
    void validaElRangoDeFechas() {
        IllegalArgumentException faltan = assertThrows(IllegalArgumentException.class,
                () -> logica.consultar(null, fecha));
        assertEquals("Debe indicar ambas fechas del período", faltan.getMessage());

        IllegalArgumentException faltaHasta = assertThrows(IllegalArgumentException.class,
                () -> logica.consultar(fecha, null));
        assertEquals("Debe indicar ambas fechas del período", faltaHasta.getMessage());

        IllegalArgumentException orden = assertThrows(IllegalArgumentException.class,
                () -> logica.consultar(fecha.plusDays(1), fecha));
        assertEquals("La fecha 'desde' no puede ser posterior a la fecha 'hasta'", orden.getMessage());
    }
}
