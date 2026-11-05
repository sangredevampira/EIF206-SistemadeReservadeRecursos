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
 * Prueba de integracion contra MySQL (base reservas_test).
 * - Si no existe servidor-test.properties, se salta: quien no configuro
 *   MySQL puede correr mvn clean verify igual.
 * - Si existe pero MySQL no responde o la clave esta mal, falla.
 * Se corre con mvn verify (Failsafe pone servidor.config). Con la flecha
 * verde de IntelliJ no hay servidor.config y tambien se salta.
 */
public class ConexionBDIT {

    // en @BeforeEach y no en @BeforeAll: asi cada prueba sale como "Skipped"
    // en el reporte; desde @BeforeAll Failsafe la contaria como 0 pruebas
    @BeforeEach
    void requiereConfiguracionDePrueba() {
        String ruta = System.getProperty(ConexionBD.PROPIEDAD_CONFIG);
        assumeTrue(ruta != null && Files.isRegularFile(Path.of(ruta)),
                "Se salta: no existe backend/servidor-test.properties (copie servidor-test.properties.example)"
                        + " o no se corre con mvn verify.");
    }

    @Test
    void conectaALaBaseDePruebasReservasTest() throws SQLException {
        try (Connection conexion = ConexionBD.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement("SELECT DATABASE()");
             ResultSet resultado = sentencia.executeQuery()) {

            assertTrue(conexion.isValid(2));
            assertTrue(resultado.next());
            assertEquals("reservas_test", resultado.getString(1));
        }
    }

    @Test
    void cadaLlamadaDevuelveUnaConexionNueva() throws SQLException {
        try (Connection primera = ConexionBD.obtenerConexion();
             Connection segunda = ConexionBD.obtenerConexion()) {

            assertNotSame(primera, segunda);
            primera.close();
            assertTrue(primera.isClosed());
            assertFalse(segunda.isClosed());
            assertTrue(segunda.isValid(2));
        }
    }

    @Test
    void tildesFechasYHorasVuelvenIguales() throws SQLException {
        try (Connection conexion = ConexionBD.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(
                     "SELECT 'Cámara', DATE '2026-07-01', TIME '09:00:00'");
             ResultSet resultado = sentencia.executeQuery()) {

            assertTrue(resultado.next());
            assertEquals("Cámara", resultado.getString(1));
            assertEquals(LocalDate.of(2026, 7, 1), resultado.getObject(2, LocalDate.class));
            assertEquals(LocalTime.of(9, 0), resultado.getObject(3, LocalTime.class));
        }
    }
}
