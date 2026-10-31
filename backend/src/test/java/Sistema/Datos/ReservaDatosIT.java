package Sistema.Datos;

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

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Prueba de integracion de las consultas de reservas futuras de ReservaDatos
 * contra MySQL (base reservas_test). Se salta igual que GeneradorIdsBDIT si
 * no existe servidor-test.properties.
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
