package Sistema.Service;

import Sistema.DTO.CategoriaDTO;
import Sistema.DTO.EstadisticaCategoriaDTO;
import Sistema.DTO.EstadoReserva;
import Sistema.DTO.RecursoDTO;
import Sistema.DTO.ReservaDTO;
import Sistema.Logica.EstadisticasRecursosLogica;
import Sistema.Logica.ReservaLogica;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias de EstadisticasRecursosService. Internamente delega en
 * ReservaLogica.buscarPorPeriodo; aqui se reemplaza por una ReservaLogica falsa
 * con reservas en memoria, asi que no se usa MySQL ni archivos.
 */
class EstadisticasRecursosServiceTest {

    private EstadisticasRecursosService estadisticasRecursosService;

    @BeforeEach
    void prepararDatosDePrueba() {
        estadisticasRecursosService =
                new EstadisticasRecursosService(new EstadisticasRecursosLogica(new ReservaLogicaFake()));
    }

    @Test
    void calcularCuentaSoloReservasActivasDentroDelPeriodo() {
        var resultado = estadisticasRecursosService.calcular(
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));

        assertTrue(resultado.isExitoso());
        List<EstadisticaCategoriaDTO> datos = resultado.getDatos();
        assertEquals(1, datos.size());
        assertEquals("Sala para 10 personas", datos.get(0).getCategoria());
        assertEquals(1, datos.get(0).getCantidad()); // solo RES-000001; RES-000002 esta cancelada
    }

    @Test
    void calcularAgrupaPorCategoriaEnUnPeriodoMasAmplio() {
        var resultado = estadisticasRecursosService.calcular(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        assertTrue(resultado.isExitoso());
        List<EstadisticaCategoriaDTO> datos = resultado.getDatos();
        assertEquals(1, datos.size());
        assertEquals("Sala para 10 personas", datos.get(0).getCategoria());
        assertEquals(2, datos.get(0).getCantidad()); // RES-000001 + RES-000003
    }

    @Test
    void calcularDevuelveListaVaciaSiNoHayReservasEnElPeriodo() {
        var resultado = estadisticasRecursosService.calcular(
                LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 31));

        assertTrue(resultado.isExitoso());
        assertTrue(resultado.getDatos().isEmpty());
    }

    @Test
    void calcularIncluyeReservasExactamenteEnLosBordesDelPeriodo() {
        // El periodo es 2026-03-10 a 2026-03-10: mismo dia que RES-000001,
        // que cae justo en el limite "desde" y "hasta" a la vez.
        var resultado = estadisticasRecursosService.calcular(
                LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 10));

        assertTrue(resultado.isExitoso());
        assertEquals(1, resultado.getDatos().size());
        assertEquals(1, resultado.getDatos().get(0).getCantidad());
    }

    @Test
    void calcularFallaSiFechaDesdeEsPosteriorAFechaHasta() {
        var resultado = estadisticasRecursosService.calcular(
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 1, 1));

        assertFalse(resultado.isExitoso());
        assertEquals("La fecha 'desde' no puede ser posterior a la fecha 'hasta'.", resultado.getMensajeError());
    }

    @Test
    void calcularFallaSiFaltaAlgunaFecha() {
        var resultado = estadisticasRecursosService.calcular(null, LocalDate.of(2026, 1, 1));

        assertFalse(resultado.isExitoso());
        assertEquals("Debe indicar ambas fechas del período.", resultado.getMensajeError());
    }

    /**
     * ReservaLogica falsa: devuelve de una lista en memoria las reservas cuya
     * fecha cae en el rango (extremos incluidos). No toca MySQL.
     */
    private static class ReservaLogicaFake extends ReservaLogica {

        private final List<ReservaDTO> reservas = new ArrayList<>();

        ReservaLogicaFake() {
            CategoriaDTO sala = new CategoriaDTO("CAT-000001", "Sala para 10 personas");
            CategoriaDTO laptop = new CategoriaDTO("CAT-000002", "Laptop windows 11");

            // RES-000001: activa, dentro del rango de marzo -> cuenta.
            reservas.add(new ReservaDTO("RES-000001", "func1", "Ana Perez", "Reunion",
                    LocalDate.of(2026, 3, 10), LocalTime.of(8, 0), LocalTime.of(9, 0),
                    List.of(new RecursoDTO("REC-0001", "Sala 1", sala)), EstadoReserva.ACTIVA));
            // RES-000002: cancelada, dentro del rango de marzo -> NO debe contarse.
            reservas.add(new ReservaDTO("RES-000002", "func1", "Ana Perez", "Capacitacion",
                    LocalDate.of(2026, 3, 12), LocalTime.of(10, 0), LocalTime.of(11, 0),
                    List.of(new RecursoDTO("REC-0002", "Laptop 1", laptop)), EstadoReserva.CANCELADA));
            // RES-000003: activa, fuera del rango de marzo (abril) -> no cuenta en ese rango.
            reservas.add(new ReservaDTO("RES-000003", "func1", "Ana Perez", "Otra reunion",
                    LocalDate.of(2026, 4, 1), LocalTime.of(8, 0), LocalTime.of(9, 0),
                    List.of(new RecursoDTO("REC-0003", "Sala 2", sala)), EstadoReserva.ACTIVA));
        }

        @Override
        public List<ReservaDTO> buscarPorPeriodo(LocalDate desde, LocalDate hasta) {
            List<ReservaDTO> enRango = new ArrayList<>();
            for (ReservaDTO reserva : reservas) {
                if (!reserva.getFecha().isBefore(desde) && !reserva.getFecha().isAfter(hasta)) {
                    enRango.add(reserva);
                }
            }
            return enRango;
        }
    }
}
