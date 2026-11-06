package Sistema.Logica;

import Sistema.Datos.ReservaDatos;
import Sistema.DTO.CategoriaDTO;
import Sistema.DTO.EstadoReserva;
import Sistema.DTO.RecursoDTO;
import Sistema.DTO.ReservaDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Prueba el algoritmo de disponibilidad de recursos y la transaccion de registrarReserva, que viven
 * dentro de ReservaLogica. No existe una clase "DisponibilidadService" separada en la implementacion real;
 * este archivo ejercita ReservaLogica de forma real, fingiendo (Fake) sus tres dependencias:
 * ReservaDatos, RecursoLogica y la conexion (ConexionFalsa), asi que no usa MySQL.
 */
class DisponibilidadServiceTest {

    static class RecursoLogicaFake extends RecursoLogica {
        List<RecursoDTO> recursosDeLaCategoria;

        @Override
        public List<RecursoDTO> filtrarRecursos(String idCategoria) { return recursosDeLaCategoria; }
    }

    static class ReservaDatosFake extends ReservaDatos {
        Map<String, List<ReservaDTO>> reservasPorRecurso = new HashMap<>();
        ReservaDTO ultimaReservaGuardada;
        Connection conexionDelGuardado;
        boolean seLlamoAgregar = false;
        boolean funcionarioActivo = true;

        @Override
        public boolean funcionarioActivo(Connection conexion, String idFuncionario) { return funcionarioActivo; }

        void definirReservasDe(String idRecurso, List<ReservaDTO> reservas) { reservasPorRecurso.put(idRecurso, reservas); }

        @Override
        public List<ReservaDTO> filtrarReservaPorRecurso(Connection conexion, String idRecurso) {
            return reservasPorRecurso.getOrDefault(idRecurso, List.of());
        }

        @Override
        public String generarNuevoId(Connection conexion) { return "RES-000001"; }

        @Override
        public void agregarReserva(Connection conexion, ReservaDTO nuevaReserva) {
            seLlamoAgregar = true;
            ultimaReservaGuardada = nuevaReserva;
            conexionDelGuardado = conexion;
        }
    }

    private ReservaDatosFake reservaDatos;
    private RecursoLogicaFake recursoLogica;
    private ConexionFalsa conexionFalsa;
    private ReservaLogica reservaLogica;

    private final CategoriaDTO categoriaSalas = new CategoriaDTO("CAT-000001", "Salas");
    private final RecursoDTO recursoUnico = new RecursoDTO("REC-0001", "Sala A", categoriaSalas);
    private final LocalDate manana = LocalDate.now().plusDays(1);

    @BeforeEach
    void setUp() {
        reservaDatos = new ReservaDatosFake();
        recursoLogica = new RecursoLogicaFake();
        recursoLogica.recursosDeLaCategoria = List.of(recursoUnico);
        conexionFalsa = new ConexionFalsa();
        reservaLogica = new ReservaLogica(reservaDatos, recursoLogica, () -> conexionFalsa.conexion);
    }

    private void existeReservaActivaParaRecursoUnico(LocalTime horaInicioExistente, LocalTime horaFinExistente) {
        reservaDatos.definirReservasDe("REC-0001", List.of(
                new ReservaDTO("RES-000001", "FUNC-99", "Otro func.", "Otra actividad",
                        manana, horaInicioExistente, horaFinExistente, List.of(recursoUnico), EstadoReserva.ACTIVA)
        ));
    }

    // registra una reserva de FUNC-01 para "manana" y la categoria de salas
    private ReservaDTO registrar(LocalTime horaInicio, LocalTime horaFin) {
        return reservaLogica.registrarReserva("FUNC-01", "Ana", "Reunion",
                manana, horaInicio, horaFin, List.of("CAT-000001"));
    }

    private ReservaDTO registrarActividad(String actividad) {
        return reservaLogica.registrarReserva("FUNC-01", "Ana", actividad,
                manana, LocalTime.of(8, 0), LocalTime.of(10, 0), List.of("CAT-000001"));
    }

    @Test
    void nombreDeActividadValidoSeAcepta() {
        ReservaDTO reserva = registrarActividad("Reunión de planificación");

        assertEquals("Reunión de planificación", reserva.getNombreActividad());
        assertTrue(reservaDatos.seLlamoAgregar);
    }

    @Test
    void nombreDeActividadVacioSeRechaza() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> registrarActividad("")
        );

        assertEquals("Debe describir la actividad a realizar.", error.getMessage());
        assertTrue(conexionFalsa.llamadas.isEmpty());
    }

    @Test
    void nombreDeActividadDeDoscientosCaracteresSeAcepta() {
        String actividad = "a".repeat(200);

        ReservaDTO reserva = registrarActividad(actividad);

        assertEquals(200, reserva.getNombreActividad().length());
        assertTrue(reservaDatos.seLlamoAgregar);
    }

    @Test
    void nombreDeActividadDeDoscientosUnCaracteresSeRechaza() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> registrarActividad("a".repeat(201))
        );

        assertEquals("La actividad no puede exceder los 200 caracteres.", error.getMessage());
        assertTrue(conexionFalsa.llamadas.isEmpty());
    }

    @Test
    void nombreDeActividadConEspaciosConservaLasReglasExistentes() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> registrarActividad("   ")
        );
        assertEquals("Debe describir la actividad a realizar.", error.getMessage());

        ReservaDTO reserva = registrarActividad("  Reunión  ");
        assertEquals("  Reunión  ", reserva.getNombreActividad());
    }

    @Test
    void sinReservasPreviasElRecursoQuedaDisponibleYSeRegistra() {
        ReservaDTO reserva = registrar(LocalTime.of(8, 0), LocalTime.of(10, 0));

        assertNotNull(reserva);
        assertEquals("RES-000001", reserva.getIdReserva());
        assertEquals(EstadoReserva.ACTIVA, reserva.getEstado());
        assertTrue(reservaDatos.seLlamoAgregar);
        assertEquals("REC-0001", reservaDatos.ultimaReservaGuardada.getRecursosReservados().get(0).getId());
    }

    @Test
    void nuevaReservaEmpiezaDentroDeLaExistenteHayConflictoNoSeRegistra() {
        existeReservaActivaParaRecursoUnico(LocalTime.of(8, 0), LocalTime.of(10, 0));

        assertThrows(IllegalArgumentException.class, () -> registrar(LocalTime.of(9, 0), LocalTime.of(11, 0)));
        assertFalse(reservaDatos.seLlamoAgregar);
    }

    @Test
    void nuevaReservaTerminaDentroDeLaExistenteHayConflictoNoSeRegistra() {
        existeReservaActivaParaRecursoUnico(LocalTime.of(8, 0), LocalTime.of(10, 0));

        assertThrows(IllegalArgumentException.class, () -> registrar(LocalTime.of(7, 0), LocalTime.of(9, 0)));
    }

    @Test
    void nuevaReservaIdenticaALaExistenteHayConflictoNoSeRegistra() {
        existeReservaActivaParaRecursoUnico(LocalTime.of(8, 0), LocalTime.of(10, 0));

        assertThrows(IllegalArgumentException.class, () -> registrar(LocalTime.of(8, 0), LocalTime.of(10, 0)));
    }

    @Test
    void nuevaReservaInmediatamenteDespuesNoHayConflictoSeRegistra() {
        existeReservaActivaParaRecursoUnico(LocalTime.of(8, 0), LocalTime.of(10, 0));

        assertNotNull(registrar(LocalTime.of(10, 0), LocalTime.of(11, 0)));
        assertTrue(reservaDatos.seLlamoAgregar);
    }

    @Test
    void nuevaReservaInmediatamenteAntesNoHayConflictoSeRegistra() {
        existeReservaActivaParaRecursoUnico(LocalTime.of(8, 0), LocalTime.of(10, 0));

        assertNotNull(registrar(LocalTime.of(7, 0), LocalTime.of(8, 0)));
    }

    @Test
    void reservaCanceladaNoBloqueaElRecursoSeRegistra() {
        reservaDatos.definirReservasDe("REC-0001", List.of(
                new ReservaDTO("RES-000002", "FUNC-99", "Otro", "Otra",
                        manana, LocalTime.of(8, 0), LocalTime.of(10, 0),
                        List.of(recursoUnico), EstadoReserva.CANCELADA)
        ));

        assertNotNull(registrar(LocalTime.of(8, 0), LocalTime.of(10, 0)));
    }

    @Test
    void otraFechaNoBloqueaElRecursoSeRegistra() {
        existeReservaActivaParaRecursoUnico(LocalTime.of(8, 0), LocalTime.of(10, 0));

        ReservaDTO reserva = reservaLogica.registrarReserva("FUNC-01", "Ana", "Reunion",
                manana.plusDays(1), LocalTime.of(8, 0), LocalTime.of(10, 0), List.of("CAT-000001"));

        assertNotNull(reserva);
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

        ReservaDTO reserva = registrar(LocalTime.of(8, 0), LocalTime.of(10, 0));

        assertNotNull(reserva);
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

        assertThrows(IllegalArgumentException.class, () -> registrar(LocalTime.of(8, 0), LocalTime.of(10, 0)));
        assertFalse(reservaDatos.seLlamoAgregar);
    }

    @Test
    void categoriaSinRecursosRegistradosNoSeRegistraNada() {
        recursoLogica.recursosDeLaCategoria = List.of();

        assertThrows(IllegalArgumentException.class, () -> reservaLogica.registrarReserva(
                "FUNC-01", "Ana", "Reunion", manana, LocalTime.of(8, 0), LocalTime.of(10, 0),
                List.of("CAT-000099")));
        assertFalse(reservaDatos.seLlamoAgregar);
    }

    // --- mensaje de las categorias sin recursos libres (decision 17) ---

    @Test
    void elMensajeNombraLaCategoriaSinRecursosLibres() {
        existeReservaActivaParaRecursoUnico(LocalTime.of(8, 0), LocalTime.of(10, 0));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> registrar(LocalTime.of(9, 0), LocalTime.of(11, 0)));

        assertEquals("No hay recursos disponibles en las categorías: Salas", error.getMessage());
    }

    @Test
    void siLaCategoriaNoTieneRecursosElMensajeUsaSuId() {
        recursoLogica.recursosDeLaCategoria = List.of();

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> reservaLogica.registrarReserva("FUNC-01", "Ana", "Reunion", manana,
                        LocalTime.of(8, 0), LocalTime.of(10, 0), List.of("CAT-000099")));

        assertEquals("No hay recursos disponibles en las categorías: CAT-000099", error.getMessage());
    }

    // --- transaccion ---

    @Test
    void siSeRegistraSeHaceCommitYSeCierraLaConexion() {
        registrar(LocalTime.of(8, 0), LocalTime.of(10, 0));

        assertEquals(List.of("setAutoCommit(false)", "commit", "close"), conexionFalsa.llamadas);
        // la reserva se guardo con la MISMA conexion de la transaccion
        assertSame(conexionFalsa.conexion, reservaDatos.conexionDelGuardado);
    }

    @Test
    void siNoHayDisponibilidadSeHaceRollbackYSeCierraLaConexion() {
        existeReservaActivaParaRecursoUnico(LocalTime.of(8, 0), LocalTime.of(10, 0));

        assertThrows(IllegalArgumentException.class, () -> registrar(LocalTime.of(9, 0), LocalTime.of(11, 0)));

        assertEquals(List.of("setAutoCommit(false)", "rollback", "close"), conexionFalsa.llamadas);
    }

    // --- funcionario dado de baja ---

    @Test
    void conFuncionarioDadoDeBajaNoSeRegistraYSeHaceRollback() {
        reservaDatos.funcionarioActivo = false;

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> registrar(LocalTime.of(8, 0), LocalTime.of(10, 0)));

        assertEquals("El funcionario de la reserva no existe o está dado de baja.", error.getMessage());
        assertFalse(reservaDatos.seLlamoAgregar);
        assertEquals(List.of("setAutoCommit(false)", "rollback", "close"), conexionFalsa.llamadas);
    }

    @Test
    void conFuncionarioActivoSeRegistraConNormalidad() {
        reservaDatos.funcionarioActivo = true;

        assertNotNull(registrar(LocalTime.of(8, 0), LocalTime.of(10, 0)));
        assertTrue(reservaDatos.seLlamoAgregar);
        assertEquals(List.of("setAutoCommit(false)", "commit", "close"), conexionFalsa.llamadas);
    }

    @Test
    void conDatosInvalidosNoSeAbreNingunaConexion() {
        assertThrows(IllegalArgumentException.class, () -> reservaLogica.registrarReserva(
                "FUNC-01", "Ana", "", manana, LocalTime.of(8, 0), LocalTime.of(10, 0), List.of("CAT-000001")));

        assertTrue(conexionFalsa.llamadas.isEmpty());
    }
}
