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
 * Prueba de integracion de GeneradorIdsBD contra MySQL (base reservas_test).
 * Se salta igual que ConexionBDIT si no existe servidor-test.properties.
 *
 * Cada prueba trabaja dentro de una transaccion y termina SIEMPRE con
 * rollback: no deja nada en la base aunque falle. Antes de tocar datos
 * comprueba que la base sea reservas_test.
 */
public class GeneradorIdsBDIT {

    private static final String ID_FUNCIONARIO = "it-generador";

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

    @Test
    void tablaVaciaDaElPrimerId() throws SQLException {
        enTransaccionDePrueba(conexion -> {
            assertEquals("CAT-000001", GeneradorIdsBD.siguienteId(conexion, "categoria", "id_categoria", "CAT"));
            assertEquals("RES-000001", GeneradorIdsBD.siguienteId(conexion, "reserva", "id_reserva", "RES"));
        });
    }

    @Test
    void usaElNumeroMasAltoNoLaCantidadDeFilas() throws SQLException {
        enTransaccionDePrueba(conexion -> {
            insertarReservas(conexion, "RES-000003", "RES-000011");

            assertEquals("RES-000012", GeneradorIdsBD.siguienteId(conexion, "reserva", "id_reserva", "RES"));
        });
    }

    @Test
    void ignoraLosIdsQueNoTienenElFormato() throws SQLException {
        enTransaccionDePrueba(conexion -> {
            insertarReservas(conexion, "RES-000011", "RES-12", "RES-9999999", "OTRO-000050");

            assertEquals("RES-000012", GeneradorIdsBD.siguienteId(conexion, "reserva", "id_reserva", "RES"));
        });
    }

    @Test
    void generaIdsDeCategoria() throws SQLException {
        enTransaccionDePrueba(conexion -> {
            insertarCategoria(conexion, "CAT-000007");

            assertEquals("CAT-000008", GeneradorIdsBD.siguienteId(conexion, "categoria", "id_categoria", "CAT"));
        });
    }

    @Test
    void noCierraLaConexion() throws SQLException {
        enTransaccionDePrueba(conexion -> {
            GeneradorIdsBD.siguienteId(conexion, "reserva", "id_reserva", "RES");

            assertFalse(conexion.isClosed());
            assertTrue(conexion.isValid(2));
        });
    }

    @Test
    void conAutoCommitEncendidoLanzaIllegalStateException() throws SQLException {
        try (Connection conexion = ConexionBD.obtenerConexion()) {
            assertEquals("reservas_test", baseActual(conexion));
            assertTrue(conexion.getAutoCommit());

            assertThrows(IllegalStateException.class,
                    () -> GeneradorIdsBD.siguienteId(conexion, "reserva", "id_reserva", "RES"));
        }
    }

    @Test
    void elRollbackNoDejaDatosEnLaBase() throws SQLException {
        enTransaccionDePrueba(conexion -> insertarReservas(conexion, "RES-000011"));

        try (Connection conexion = ConexionBD.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(
                     "SELECT COUNT(*) FROM usuario WHERE id_usuario = ?")) {
            sentencia.setString(1, ID_FUNCIONARIO);
            try (ResultSet resultado = sentencia.executeQuery()) {
                resultado.next();
                assertEquals(0, resultado.getInt(1));
            }
        }
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
                "DELETE FROM categoria"}) {
            try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
                sentencia.executeUpdate();
            }
        }
    }

    // la FK de reserva exige un funcionario, y la de funcionario un usuario
    private static void insertarReservas(Connection conexion, String... ids) throws SQLException {
        try (PreparedStatement usuario = conexion.prepareStatement(
                "INSERT INTO usuario (id_usuario, clave_hash, rol) VALUES (?, ?, ?)")) {
            usuario.setString(1, ID_FUNCIONARIO);
            usuario.setString(2, "no-es-un-hash");
            usuario.setString(3, "FUNCIONARIO");
            usuario.executeUpdate();
        }
        try (PreparedStatement funcionario = conexion.prepareStatement(
                "INSERT INTO funcionario (id_funcionario, nombre, telefono) VALUES (?, ?, ?)")) {
            funcionario.setString(1, ID_FUNCIONARIO);
            funcionario.setString(2, "Prueba GeneradorIdsBDIT");
            funcionario.setString(3, "0000-0000");
            funcionario.executeUpdate();
        }
        try (PreparedStatement reserva = conexion.prepareStatement(
                "INSERT INTO reserva (id_reserva, id_funcionario, nombre_actividad, fecha, hora_inicio, hora_fin)"
                        + " VALUES (?, ?, ?, ?, ?, ?)")) {
            for (String id : ids) {
                reserva.setString(1, id);
                reserva.setString(2, ID_FUNCIONARIO);
                reserva.setString(3, "Prueba GeneradorIdsBDIT");
                reserva.setObject(4, LocalDate.of(2026, 7, 1));
                reserva.setObject(5, LocalTime.of(9, 0));
                reserva.setObject(6, LocalTime.of(10, 0));
                reserva.executeUpdate();
            }
        }
    }

    private static void insertarCategoria(Connection conexion, String id) throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement(
                "INSERT INTO categoria (id_categoria, descripcion) VALUES (?, ?)")) {
            sentencia.setString(1, id);
            sentencia.setString(2, "Prueba GeneradorIdsBDIT");
            sentencia.executeUpdate();
        }
    }
}
