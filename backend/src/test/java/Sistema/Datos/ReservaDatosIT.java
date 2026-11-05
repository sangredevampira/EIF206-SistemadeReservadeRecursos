package Sistema.Datos;

import Sistema.DTO.CategoriaDTO;
import Sistema.DTO.EstadoReserva;
import Sistema.DTO.RecursoDTO;
import Sistema.DTO.ReservaDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Prueba de integracion de ReservaDatos contra MySQL (base reservas_test). Se salta igual que
 * GeneradorIdsBDIT si no existe servidor-test.properties.
 *
 * Cada prueba trabaja dentro de una transaccion y termina SIEMPRE con
 * rollback. Antes de tocar datos comprueba que la base sea reservas_test.
 */
public class ReservaDatosIT {

    private static final String ID_FUNCIONARIO = "it-reservas";
    private static final String ID_OTRO_FUNCIONARIO = "it-reservas-2";
    private static final String ID_CATEGORIA = "CAT-IT0001";
    private static final String ID_RECURSO = "it-recurso";
    private static final String ID_OTRO_RECURSO = "it-recurso-2";

    private final ReservaDatos reservaDatos = new ReservaDatos();

    @FunctionalInterface
    private interface PruebaConConexion {
        void ejecutar(Connection conexion) throws SQLException;
    }

    // en @BeforeEach para que cada prueba salga como "Skipped" (ver ConexionBDIT)
    @BeforeEach
    void requiereConfiguracionDePrueba() {
        String ruta = System.getProperty(ConexionBD.PROPIEDAD_CONFIG);
        assumeTrue(ruta != null && Files.isRegularFile(Path.of(ruta)),
                "Se salta: no existe backend/servidor-test.properties (copie servidor-test.properties.example)"
                        + " o no se corre con mvn verify.");
    }

    // --- funcionario ---

    @Test
    void funcionarioConReservaFuturaActivaDevuelveTrue() throws SQLException {
        enTransaccionDePrueba(conexion -> {
            insertarReserva(conexion, "RES-IT0001", ID_FUNCIONARIO, LocalDate.now().plusDays(3), "ACTIVA");

            assertTrue(reservaDatos.existenReservasFuturasDeFuncionario(conexion, ID_FUNCIONARIO));
        });
    }

    @Test
    void funcionarioConReservaActivaDeHoyDevuelveTrue() throws SQLException {
        enTransaccionDePrueba(conexion -> {
            insertarReserva(conexion, "RES-IT0001", ID_FUNCIONARIO, LocalDate.now(), "ACTIVA");

            assertTrue(reservaDatos.existenReservasFuturasDeFuncionario(conexion, ID_FUNCIONARIO));
        });
    }

    @Test
    void funcionarioConSoloReservaFuturaCanceladaDevuelveFalse() throws SQLException {
        enTransaccionDePrueba(conexion -> {
            insertarReserva(conexion, "RES-IT0001", ID_FUNCIONARIO, LocalDate.now().plusDays(3), "CANCELADA");

            assertFalse(reservaDatos.existenReservasFuturasDeFuncionario(conexion, ID_FUNCIONARIO));
        });
    }

    @Test
    void funcionarioConSoloReservaActivaPasadaDevuelveFalse() throws SQLException {
        enTransaccionDePrueba(conexion -> {
            insertarReserva(conexion, "RES-IT0001", ID_FUNCIONARIO, LocalDate.now().minusDays(1), "ACTIVA");

            assertFalse(reservaDatos.existenReservasFuturasDeFuncionario(conexion, ID_FUNCIONARIO));
        });
    }

    @Test
    void funcionarioSinReservasDevuelveFalse() throws SQLException {
        enTransaccionDePrueba(conexion ->
                assertFalse(reservaDatos.existenReservasFuturasDeFuncionario(conexion, ID_FUNCIONARIO)));
    }

    @Test
    void funcionarioNoVeLasReservasDeOtroFuncionario() throws SQLException {
        enTransaccionDePrueba(conexion -> {
            insertarReserva(conexion, "RES-IT0001", ID_OTRO_FUNCIONARIO, LocalDate.now().plusDays(3), "ACTIVA");

            assertFalse(reservaDatos.existenReservasFuturasDeFuncionario(conexion, ID_FUNCIONARIO));
            assertTrue(reservaDatos.existenReservasFuturasDeFuncionario(conexion, ID_OTRO_FUNCIONARIO));
        });
    }

    @Test
    void funcionarioConUnaPasadaYUnaFuturaActivaDevuelveTrue() throws SQLException {
        enTransaccionDePrueba(conexion -> {
            insertarReserva(conexion, "RES-IT0001", ID_FUNCIONARIO, LocalDate.now().minusDays(5), "ACTIVA");
            insertarReserva(conexion, "RES-IT0002", ID_FUNCIONARIO, LocalDate.now().plusDays(5), "ACTIVA");

            assertTrue(reservaDatos.existenReservasFuturasDeFuncionario(conexion, ID_FUNCIONARIO));
        });
    }

    @Test
    void funcionarioActivoEsTrueParaUnFuncionarioRecienInsertado() throws SQLException {
        enTransaccionDePrueba(conexion ->
                assertTrue(reservaDatos.funcionarioActivo(conexion, ID_FUNCIONARIO)));
    }

    @Test
    void funcionarioActivoEsFalseDespuesDeDarloDeBaja() throws SQLException {
        enTransaccionDePrueba(conexion -> {
            ejecutar(conexion, "UPDATE funcionario SET activo = 0 WHERE id_funcionario = ?", ID_FUNCIONARIO);

            assertFalse(reservaDatos.funcionarioActivo(conexion, ID_FUNCIONARIO));
        });
    }

    @Test
    void funcionarioActivoEsFalseParaUnIdInexistente() throws SQLException {
        enTransaccionDePrueba(conexion ->
                assertFalse(reservaDatos.funcionarioActivo(conexion, "it-no-existe")));
    }

    // --- recurso ---

    @Test
    void recursoConReservaFuturaActivaDevuelveTrue() throws SQLException {
        enTransaccionDePrueba(conexion -> {
            insertarReserva(conexion, "RES-IT0001", ID_FUNCIONARIO, LocalDate.now().plusDays(3), "ACTIVA");
            asignarRecurso(conexion, "RES-IT0001", ID_RECURSO);

            assertTrue(reservaDatos.existenReservasFuturasActivasDeRecurso(conexion, ID_RECURSO));
        });
    }

    @Test
    void recursoConReservaActivaDeHoyDevuelveTrue() throws SQLException {
        enTransaccionDePrueba(conexion -> {
            insertarReserva(conexion, "RES-IT0001", ID_FUNCIONARIO, LocalDate.now(), "ACTIVA");
            asignarRecurso(conexion, "RES-IT0001", ID_RECURSO);

            assertTrue(reservaDatos.existenReservasFuturasActivasDeRecurso(conexion, ID_RECURSO));
        });
    }

    @Test
    void recursoConSoloReservaFuturaCanceladaDevuelveFalse() throws SQLException {
        enTransaccionDePrueba(conexion -> {
            insertarReserva(conexion, "RES-IT0001", ID_FUNCIONARIO, LocalDate.now().plusDays(3), "CANCELADA");
            asignarRecurso(conexion, "RES-IT0001", ID_RECURSO);

            assertFalse(reservaDatos.existenReservasFuturasActivasDeRecurso(conexion, ID_RECURSO));
        });
    }

    @Test
    void recursoConSoloReservaActivaPasadaDevuelveFalse() throws SQLException {
        enTransaccionDePrueba(conexion -> {
            insertarReserva(conexion, "RES-IT0001", ID_FUNCIONARIO, LocalDate.now().minusDays(1), "ACTIVA");
            asignarRecurso(conexion, "RES-IT0001", ID_RECURSO);

            assertFalse(reservaDatos.existenReservasFuturasActivasDeRecurso(conexion, ID_RECURSO));
        });
    }

    @Test
    void recursoSinReservasDevuelveFalse() throws SQLException {
        enTransaccionDePrueba(conexion ->
                assertFalse(reservaDatos.existenReservasFuturasActivasDeRecurso(conexion, ID_RECURSO)));
    }

    @Test
    void recursoNoVeLasReservasDeOtroRecurso() throws SQLException {
        enTransaccionDePrueba(conexion -> {
            insertarReserva(conexion, "RES-IT0001", ID_FUNCIONARIO, LocalDate.now().plusDays(3), "ACTIVA");
            asignarRecurso(conexion, "RES-IT0001", ID_OTRO_RECURSO);

            assertFalse(reservaDatos.existenReservasFuturasActivasDeRecurso(conexion, ID_RECURSO));
            assertTrue(reservaDatos.existenReservasFuturasActivasDeRecurso(conexion, ID_OTRO_RECURSO));
        });
    }

    // --- version sin Connection ---

    @Test
    void laVersionSinConexionAbreLaSuyaYNoVeDatosSinCommit() {
        // los datos de las pruebas nunca se confirman, asi que otra conexion no los ve
        assertFalse(reservaDatos.existenReservasFuturasDeFuncionario(ID_FUNCIONARIO));
        assertFalse(reservaDatos.existenReservasFuturasActivasDeRecurso(ID_RECURSO));
    }

    @Test
    void noCierraLaConexionRecibida() throws SQLException {
        enTransaccionDePrueba(conexion -> {
            reservaDatos.existenReservasFuturasDeFuncionario(conexion, ID_FUNCIONARIO);
            reservaDatos.existenReservasFuturasActivasDeRecurso(conexion, ID_RECURSO);

            assertFalse(conexion.isClosed());
        });
    }

    // --- lectura y escritura de reservas (con sus recursos) ---

    @Test
    void agregarReservaGuardaLaReservaYSusRecursos() throws SQLException {
        enTransaccionDePrueba(conexion -> {
            reservaDatos.agregarReserva(conexion, reservaDe("RES-IT0009", ID_FUNCIONARIO,
                    LocalDate.now().plusDays(3), EstadoReserva.ACTIVA, ID_RECURSO, ID_OTRO_RECURSO));

            ReservaDTO leida = reservaDatos.buscarReservaID(conexion, "RES-IT0009");

            assertNotNull(leida);
            assertEquals(ID_FUNCIONARIO, leida.getIdFuncionario());
            assertEquals("Prueba ReservaDatosIT", leida.getNombreFuncionario());   // sale del JOIN con funcionario
            assertEquals(LocalTime.of(9, 0), leida.getHoraInicio());
            assertEquals(EstadoReserva.ACTIVA, leida.getEstado());
            assertEquals(2, leida.getRecursosReservados().size());
            assertEquals(ID_RECURSO, leida.getRecursosReservados().get(0).getId());
            assertEquals(ID_CATEGORIA, leida.getRecursosReservados().get(0).getCategoria().getId());
        });
    }

    @Test
    void buscarUnaReservaInexistenteDevuelveNull() throws SQLException {
        enTransaccionDePrueba(conexion ->
                assertNull(reservaDatos.buscarReservaID(conexion, "RES-NO-EXISTE")));
    }

    @Test
    void listarReservasDevuelveTodas() throws SQLException {
        enTransaccionDePrueba(conexion -> {
            insertarReserva(conexion, "RES-IT0001", ID_FUNCIONARIO, LocalDate.now().plusDays(1), "ACTIVA");
            insertarReserva(conexion, "RES-IT0002", ID_OTRO_FUNCIONARIO, LocalDate.now().plusDays(2), "CANCELADA");

            assertEquals(2, reservaDatos.listarReservas(conexion).size());
        });
    }

    @Test
    void filtrarPorFuncionarioSoloDevuelveLasSuyas() throws SQLException {
        enTransaccionDePrueba(conexion -> {
            insertarReserva(conexion, "RES-IT0001", ID_FUNCIONARIO, LocalDate.now().plusDays(1), "ACTIVA");
            insertarReserva(conexion, "RES-IT0002", ID_OTRO_FUNCIONARIO, LocalDate.now().plusDays(1), "ACTIVA");

            List<ReservaDTO> suyas = reservaDatos.filtrarReservaFuncionario(conexion, ID_FUNCIONARIO);

            assertEquals(1, suyas.size());
            assertEquals("RES-IT0001", suyas.get(0).getIdReserva());
            assertTrue(suyas.get(0).getRecursosReservados().isEmpty());   // sin recursos: lista vacia, no null
        });
    }

    @Test
    void filtrarPorRangoDeFechasIncluyeLosExtremos() throws SQLException {
        enTransaccionDePrueba(conexion -> {
            LocalDate hoy = LocalDate.now();
            insertarReserva(conexion, "RES-IT0001", ID_FUNCIONARIO, hoy.minusDays(1), "ACTIVA");
            insertarReserva(conexion, "RES-IT0002", ID_FUNCIONARIO, hoy, "ACTIVA");
            insertarReserva(conexion, "RES-IT0003", ID_FUNCIONARIO, hoy.plusDays(2), "CANCELADA");
            insertarReserva(conexion, "RES-IT0004", ID_FUNCIONARIO, hoy.plusDays(5), "ACTIVA");

            List<ReservaDTO> enRango = reservaDatos.filtrarReservasRangoFechas(conexion, hoy, hoy.plusDays(2));

            assertEquals(List.of("RES-IT0002", "RES-IT0003"),
                    enRango.stream().map(ReservaDTO::getIdReserva).toList());
        });
    }

    @Test
    void filtrarPorRecursoDevuelveLasReservasQueLoUsanConTodosSusRecursos() throws SQLException {
        enTransaccionDePrueba(conexion -> {
            LocalDate fecha = LocalDate.now().plusDays(1);
            insertarReserva(conexion, "RES-IT0001", ID_FUNCIONARIO, fecha, "ACTIVA");
            asignarRecurso(conexion, "RES-IT0001", ID_RECURSO);
            asignarRecurso(conexion, "RES-IT0001", ID_OTRO_RECURSO);
            insertarReserva(conexion, "RES-IT0002", ID_FUNCIONARIO, fecha, "ACTIVA");
            asignarRecurso(conexion, "RES-IT0002", ID_OTRO_RECURSO);

            List<ReservaDTO> conElPrimero = reservaDatos.filtrarReservaPorRecurso(conexion, ID_RECURSO);

            assertEquals(1, conElPrimero.size());
            assertEquals("RES-IT0001", conElPrimero.get(0).getIdReserva());
            assertEquals(2, conElPrimero.get(0).getRecursosReservados().size());   // aparecen TODOS sus recursos
            assertEquals(2, reservaDatos.filtrarReservaPorRecurso(conexion, ID_OTRO_RECURSO).size());
        });
    }

    @Test
    void modificarReservaCambiaElEstado() throws SQLException {
        enTransaccionDePrueba(conexion -> {
            insertarReserva(conexion, "RES-IT0001", ID_FUNCIONARIO, LocalDate.now().plusDays(1), "ACTIVA");
            ReservaDTO reserva = reservaDatos.buscarReservaID(conexion, "RES-IT0001");

            reserva.setEstado(EstadoReserva.CANCELADA);
            assertTrue(reservaDatos.modificarReserva(conexion, reserva));
            assertEquals(EstadoReserva.CANCELADA,
                    reservaDatos.buscarReservaID(conexion, "RES-IT0001").getEstado());

            reserva.setIdReserva("RES-NO-EXISTE");
            assertFalse(reservaDatos.modificarReserva(conexion, reserva));
        });
    }

    @Test
    void generarNuevoIdDaElSiguienteNumero() throws SQLException {
        enTransaccionDePrueba(conexion -> {
            assertEquals("RES-000001", reservaDatos.generarNuevoId(conexion));

            insertarReserva(conexion, "RES-000005", ID_FUNCIONARIO, LocalDate.now().plusDays(1), "CANCELADA");
            assertEquals("RES-000006", reservaDatos.generarNuevoId(conexion));   // las canceladas cuentan
        });
    }

    // --- apoyo ---

    private static void enTransaccionDePrueba(PruebaConConexion prueba) throws SQLException {
        try (Connection conexion = ConexionBD.obtenerConexion()) {
            // antes de tocar datos: nunca trabajar sobre reservas_db
            assertEquals("reservas_test", baseActual(conexion),
                    "La prueba no corre sobre reservas_test; no se toca ningun dato.");
            conexion.setAutoCommit(false);
            try {
                vaciarTablas(conexion);
                insertarBase(conexion);
                prueba.ejecutar(conexion);
            } finally {
                conexion.rollback();   // nada queda en la base, aunque la prueba falle
            }
        }
    }

    private static String baseActual(Connection conexion) throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement("SELECT DATABASE()");
             ResultSet resultado = sentencia.executeQuery()) {
            resultado.next();
            return resultado.getString(1);
        }
    }

    // en orden de las FK: primero las tablas hijas
    private static void vaciarTablas(Connection conexion) throws SQLException {
        for (String sql : new String[]{
                "DELETE FROM reserva_recurso",
                "DELETE FROM reserva",
                "DELETE FROM recurso",
                "DELETE FROM categoria",
                "DELETE FROM funcionario",
                "DELETE FROM usuario"}) {
            ejecutar(conexion, sql);
        }
    }

    // dos funcionarios (con su usuario), una categoria y dos recursos
    private static void insertarBase(Connection conexion) throws SQLException {
        for (String id : new String[]{ID_FUNCIONARIO, ID_OTRO_FUNCIONARIO}) {
            ejecutar(conexion, "INSERT INTO usuario (id_usuario, clave_hash, rol) VALUES (?, ?, ?)",
                    id, "no-es-un-hash", "FUNCIONARIO");
            ejecutar(conexion, "INSERT INTO funcionario (id_funcionario, nombre, telefono) VALUES (?, ?, ?)",
                    id, "Prueba ReservaDatosIT", "0000-0000");
        }
        ejecutar(conexion, "INSERT INTO categoria (id_categoria, descripcion) VALUES (?, ?)",
                ID_CATEGORIA, "Prueba ReservaDatosIT");
        for (String id : new String[]{ID_RECURSO, ID_OTRO_RECURSO}) {
            ejecutar(conexion, "INSERT INTO recurso (id_recurso, descripcion, id_categoria) VALUES (?, ?, ?)",
                    id, "Prueba ReservaDatosIT " + id, ID_CATEGORIA);
        }
    }

    private static void insertarReserva(Connection conexion, String idReserva, String idFuncionario,
                                        LocalDate fecha, String estado) throws SQLException {
        ejecutar(conexion,
                "INSERT INTO reserva (id_reserva, id_funcionario, nombre_actividad, fecha, hora_inicio,"
                        + " hora_fin, estado) VALUES (?, ?, ?, ?, ?, ?, ?)",
                idReserva, idFuncionario, "Prueba ReservaDatosIT", fecha,
                LocalTime.of(9, 0), LocalTime.of(10, 0), estado);
    }

    private static void asignarRecurso(Connection conexion, String idReserva, String idRecurso)
            throws SQLException {
        ejecutar(conexion, "INSERT INTO reserva_recurso (id_reserva, id_recurso) VALUES (?, ?)",
                idReserva, idRecurso);
    }

    // una reserva armada en memoria (nombreFuncionario se ignora: al guardar no se escribe)
    private static ReservaDTO reservaDe(String idReserva, String idFuncionario, LocalDate fecha,
                                        EstadoReserva estado, String... idsRecursos) {
        List<RecursoDTO> recursos = new ArrayList<>();
        for (String id : idsRecursos) {
            recursos.add(new RecursoDTO(id, "Prueba ReservaDatosIT " + id,
                    new CategoriaDTO(ID_CATEGORIA, "Prueba ReservaDatosIT")));
        }
        return new ReservaDTO(idReserva, idFuncionario, "ignorado", "Prueba ReservaDatosIT", fecha,
                LocalTime.of(9, 0), LocalTime.of(10, 0), recursos, estado);
    }

    // los parametros se pasan con setObject: LocalDate y LocalTime viajan sin java.sql.Date
    private static void ejecutar(Connection conexion, String sql, Object... parametros) throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            for (int i = 0; i < parametros.length; i++) {
                sentencia.setObject(i + 1, parametros[i]);
            }
            sentencia.executeUpdate();
        }
    }
}
