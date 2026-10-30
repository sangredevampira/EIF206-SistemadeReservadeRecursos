package Sistema.Logica;

import Sistema.Datos.ReservaDatos;
import Sistema.DTO.CategoriaDTO;
import Sistema.DTO.EstadoReserva;
import Sistema.DTO.RecursoDTO;
import Sistema.DTO.ReservaDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Prueba el algoritmo de disponibilidad de recursos, que vive dentro de
 * ReservaLogica (metodos privados recursoDisponible/buscarPrimerRecursoDisponible).
 * No existe una clase "DisponibilidadService" separada en la implementacion real;
 * este archivo ejercita ReservaLogica de forma real, fingiendo (Fake) sus
 * dos dependencias: ReservaDatos y RecursoLogica.
 */
class DisponibilidadServiceTest {

    static class RecursoLogicaFake extends RecursoLogica {
        List<RecursoDTO> recursosDeLaCategoria;

        @Override
        public List<RecursoDTO> filtrarRecursos(String idCategoria) { return recursosDeLaCategoria; }
    }

    static class ReservaDatosFake extends ReservaDatos {
        Map<String, List<ReservaDTO>> reservasPorRecurso = new HashMap<>();
        List<ReservaDTO> todasLasReservas = List.of();
        ReservaDTO ultimaReservaGuardada;
        boolean seLlamoAgregar = false;

        void definirReservasDe(String idRecurso, List<ReservaDTO> reservas) { reservasPorRecurso.put(idRecurso, reservas); }

        @Override
        public List<ReservaDTO> filtrarReservaPorRecurso(String idRecurso) { return reservasPorRecurso.getOrDefault(idRecurso, List.of()); }

        @Override
        public List<ReservaDTO> listarReservas() { return todasLasReservas; }

        @Override
        public void agregarReserva(ReservaDTO nuevaReserva) {
            seLlamoAgregar = true;
            ultimaReservaGuardada = nuevaReserva;
        }
    }

    private ReservaDatosFake reservaDatos;
    private RecursoLogicaFake recursoLogica;
    private ReservaLogica reservaLogica;

    private final CategoriaDTO categoriaSalas = new CategoriaDTO("CAT-000001", "Salas");
    private final RecursoDTO recursoUnico = new RecursoDTO("REC-0001", "Sala A", categoriaSalas);
    private final LocalDate manana = LocalDate.now().plusDays(1);

    @BeforeEach
    void setUp() {
        reservaDatos = new ReservaDatosFake();
        recursoLogica = new RecursoLogicaFake();
        recursoLogica.recursosDeLaCategoria = List.of(recursoUnico);
        reservaLogica = new ReservaLogica(reservaDatos, recursoLogica);
    }

    private void existeReservaActivaParaRecursoUnico(LocalTime horaInicioExistente, LocalTime horaFinExistente) {
        reservaDatos.definirReservasDe("REC-0001", List.of(
                new ReservaDTO("RES-000001", "FUNC-99", "Otro func.", "Otra actividad",
                        manana, horaInicioExistente, horaFinExistente, List.of(recursoUnico), EstadoReserva.ACTIVA)
        ));
    }

    @Test
    void sinReservasPreviasElRecursoQuedaDisponibleYSeRegistra() {
        int resultado = reservaLogica.registrarReserva("FUNC-01", "Ana", "Reunion",
                manana, LocalTime.of(8, 0), LocalTime.of(10, 0), List.of("CAT-000001"));

        assertEquals(1, resultado);
        assertTrue(reservaDatos.seLlamoAgregar);
        assertEquals("REC-0001", reservaDatos.ultimaReservaGuardada.getRecursosReservados().get(0).getId());
    }

    @Test
    void nuevaReservaEmpiezaDentroDeLaExistenteHayConflictoNoSeRegistra() {
        existeReservaActivaParaRecursoUnico(LocalTime.of(8, 0), LocalTime.of(10, 0));

        int resultado = reservaLogica.registrarReserva("FUNC-01", "Ana", "Reunion",
                manana, LocalTime.of(9, 0), LocalTime.of(11, 0), List.of("CAT-000001"));

        assertEquals(0, resultado);
        assertFalse(reservaDatos.seLlamoAgregar);
    }

    @Test
    void nuevaReservaTerminaDentroDeLaExistenteHayConflictoNoSeRegistra() {
        existeReservaActivaParaRecursoUnico(LocalTime.of(8, 0), LocalTime.of(10, 0));

        int resultado = reservaLogica.registrarReserva("FUNC-01", "Ana", "Reunion",
                manana, LocalTime.of(7, 0), LocalTime.of(9, 0), List.of("CAT-000001"));

        assertEquals(0, resultado);
    }

    @Test
    void nuevaReservaIdenticaALaExistenteHayConflictoNoSeRegistra() {
        existeReservaActivaParaRecursoUnico(LocalTime.of(8, 0), LocalTime.of(10, 0));

        int resultado = reservaLogica.registrarReserva("FUNC-01", "Ana", "Reunion",
                manana, LocalTime.of(8, 0), LocalTime.of(10, 0), List.of("CAT-000001"));

        assertEquals(0, resultado);
    }

    @Test
    void nuevaReservaInmediatamenteDespuesNoHayConflictoSeRegistra() {
        existeReservaActivaParaRecursoUnico(LocalTime.of(8, 0), LocalTime.of(10, 0));

        int resultado = reservaLogica.registrarReserva("FUNC-01", "Ana", "Reunion",
                manana, LocalTime.of(10, 0), LocalTime.of(11, 0), List.of("CAT-000001"));

        assertEquals(1, resultado);
        assertTrue(reservaDatos.seLlamoAgregar);
    }

    @Test
    void nuevaReservaInmediatamenteAntesNoHayConflictoSeRegistra() {
        existeReservaActivaParaRecursoUnico(LocalTime.of(8, 0), LocalTime.of(10, 0));

        int resultado = reservaLogica.registrarReserva("FUNC-01", "Ana", "Reunion",
                manana, LocalTime.of(7, 0), LocalTime.of(8, 0), List.of("CAT-000001"));

        assertEquals(1, resultado);
    }

    @Test
    void reservaCanceladaNoBloqueaElRecursoSeRegistra() {
        reservaDatos.definirReservasDe("REC-0001", List.of(
                new ReservaDTO("RES-000002", "FUNC-99", "Otro", "Otra",
                        manana, LocalTime.of(8, 0), LocalTime.of(10, 0),
                        List.of(recursoUnico), EstadoReserva.CANCELADA)
        ));

        int resultado = reservaLogica.registrarReserva("FUNC-01", "Ana", "Reunion",
                manana, LocalTime.of(8, 0), LocalTime.of(10, 0), List.of("CAT-000001"));

        assertEquals(1, resultado);
    }

    @Test
    void otraFechaNoBloqueaElRecursoSeRegistra() {
        existeReservaActivaParaRecursoUnico(LocalTime.of(8, 0), LocalTime.of(10, 0));

        int resultado = reservaLogica.registrarReserva("FUNC-01", "Ana", "Reunion",
                manana.plusDays(1), LocalTime.of(8, 0), LocalTime.of(10, 0), List.of("CAT-000001"));

        assertEquals(1, resultado);
    }

    @Test
    void eligeElPrimerRecursoDisponibleSaltandoLosOcupados() {
        RecursoDTO recursoOcupado = new RecursoDTO("REC-0001", "Sala A", categoriaSalas);
        RecursoDTO recursoLibre = new RecursoDTO("REC-0002", "Sala B", categoriaSalas);
        recursoLogica.recursosDeLaCategoria = List.of(recursoOcupado, recursoLibre);

        reservaDatos.definirReservasDe("REC-0001", List.of(
                new ReservaDTO("RES-000003", "FUNC-99", "Otro", "Otra",
                        manana, LocalTime.of(8, 0), LocalTime.of(10, 0),
                        List.of(recursoOcupado), EstadoReserva.ACTIVA)
        ));

        int resultado = reservaLogica.registrarReserva("FUNC-01", "Ana", "Reunion",
                manana, LocalTime.of(8, 0), LocalTime.of(10, 0), List.of("CAT-000001"));

        assertEquals(1, resultado);
        assertEquals("REC-0002", reservaDatos.ultimaReservaGuardada.getRecursosReservados().get(0).getId());
    }

    @Test
    void siNingunRecursoDeLaCategoriaEstaLibreNoSeRegistraNiSeGuardaNada() {
        RecursoDTO recursoA = new RecursoDTO("REC-0001", "Sala A", categoriaSalas);
        RecursoDTO recursoB = new RecursoDTO("REC-0002", "Sala B", categoriaSalas);
        recursoLogica.recursosDeLaCategoria = List.of(recursoA, recursoB);

        ReservaDTO conflicto = new ReservaDTO("RES-X", "FUNC-99", "Otro", "Otra",
                manana, LocalTime.of(8, 0), LocalTime.of(10, 0), List.of(), EstadoReserva.ACTIVA);
        reservaDatos.definirReservasDe("REC-0001", List.of(conflicto));
        reservaDatos.definirReservasDe("REC-0002", List.of(conflicto));

        int resultado = reservaLogica.registrarReserva("FUNC-01", "Ana", "Reunion",
                manana, LocalTime.of(8, 0), LocalTime.of(10, 0), List.of("CAT-000001"));

        assertEquals(0, resultado);
        assertFalse(reservaDatos.seLlamoAgregar);
    }

    @Test
    void categoriaSinRecursosRegistradosNoSeRegistraNada() {
        recursoLogica.recursosDeLaCategoria = List.of();

        int resultado = reservaLogica.registrarReserva("FUNC-01", "Ana", "Reunion",
                manana, LocalTime.of(8, 0), LocalTime.of(10, 0), List.of("CAT-000099"));

        assertEquals(0, resultado);
        assertFalse(reservaDatos.seLlamoAgregar);
    }
}