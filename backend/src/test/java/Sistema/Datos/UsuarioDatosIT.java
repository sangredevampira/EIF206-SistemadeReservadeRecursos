package Sistema.Datos;

import Sistema.DTO.Funcionario;
import Sistema.DTO.Rol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Prueba de integracion de UsuarioDatos contra MySQL (base reservas_test).
 * Mismo patron que GeneradorIdsBDIT: se salta si no existe
 * servidor-test.properties, comprueba que la base sea reservas_test antes de
 * tocar datos y cada prueba termina con rollback (usa las versiones que
 * reciben la conexion). No borra nada: inserta usuarios con IDs propios.
 */
public class UsuarioDatosIT {

    private static final String ID_ADMIN = "admin-it";
    private static final String ID_FUNCIONARIO = "func-it";

    private final UsuarioDatos datos = new UsuarioDatos();

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
    void buscarPorIdDevuelveLosDatosGuardados() throws SQLException {
        enTransaccionDePrueba(conexion -> {
            insertarUsuario(conexion, ID_ADMIN, "hash-de-prueba", Rol.ADMINISTRADOR, true);

            UsuarioRegistro usuario = datos.buscarPorId(conexion, ID_ADMIN);

            assertNotNull(usuario);
            assertEquals(ID_ADMIN, usuario.id());
            assertEquals("hash-de-prueba", usuario.claveHash());
            assertEquals(Rol.ADMINISTRADOR, usuario.rol());
            assertTrue(usuario.activo());
        });
    }

    @Test
    void buscaSinDistinguirMayusculasYDevuelveElIdGuardado() throws SQLException {
        enTransaccionDePrueba(conexion -> {
            insertarUsuario(conexion, ID_ADMIN, "hash-de-prueba", Rol.ADMINISTRADOR, true);

            UsuarioRegistro usuario = datos.buscarPorId(conexion, "ADMIN-IT");

            assertNotNull(usuario);
            assertEquals(ID_ADMIN, usuario.id());
        });
    }

    @Test
    void idInexistenteDaNull() throws SQLException {
        enTransaccionDePrueba(conexion -> assertNull(datos.buscarPorId(conexion, "no-existe-it")));
    }

    @Test
    void leeUnUsuarioInactivo() throws SQLException {
        enTransaccionDePrueba(conexion -> {
            insertarUsuario(conexion, ID_FUNCIONARIO, "hash-de-prueba", Rol.FUNCIONARIO, false);

            assertFalse(datos.buscarPorId(conexion, ID_FUNCIONARIO).activo());
        });
    }

    @Test
    void buscarFuncionarioAsociadoDevuelveSusDatosYNullParaElAdministrador() throws SQLException {
        enTransaccionDePrueba(conexion -> {
            insertarUsuario(conexion, ID_ADMIN, "hash-de-prueba", Rol.ADMINISTRADOR, true);
            insertarUsuario(conexion, ID_FUNCIONARIO, "hash-de-prueba", Rol.FUNCIONARIO, true);
            insertarFuncionario(conexion, ID_FUNCIONARIO, "Funcionario de prueba", "8888-8888");

            Funcionario funcionario = datos.buscarFuncionarioAsociado(conexion, ID_FUNCIONARIO);

            assertNotNull(funcionario);
            assertEquals(ID_FUNCIONARIO, funcionario.getId());
            assertEquals("Funcionario de prueba", funcionario.getNombre());
            assertEquals("8888-8888", funcionario.getTelefono());
            assertNull(datos.buscarFuncionarioAsociado(conexion, ID_ADMIN));
        });
    }

    @Test
    void actualizarClaveHashCambiaElHashYDevuelveTrue() throws SQLException {
        enTransaccionDePrueba(conexion -> {
            insertarUsuario(conexion, ID_ADMIN, "hash-viejo", Rol.ADMINISTRADOR, true);

            assertTrue(datos.actualizarClaveHash(conexion, ID_ADMIN, "hash-nuevo"));
            assertEquals("hash-nuevo", datos.buscarPorId(conexion, ID_ADMIN).claveHash());
        });
    }

    @Test
    void actualizarClaveHashDeUnIdInexistenteDevuelveFalse() throws SQLException {
        enTransaccionDePrueba(conexion ->
                assertFalse(datos.actualizarClaveHash(conexion, "no-existe-it", "hash-nuevo")));
    }

    @Test
    void laVersionQueAbreSuPropiaConexionFunciona() {
        // no inserta nada: solo prueba el camino que abre y cierra su conexion
        assertNull(datos.buscarPorId("no-existe-it"));
    }

    // --- apoyo ---

    private static void enTransaccionDePrueba(PruebaConConexion prueba) throws SQLException {
        try (Connection conexion = ConexionBD.obtenerConexion()) {
            // antes de tocar datos: nunca trabajar sobre reservas_db
            assertEquals("reservas_test", baseActual(conexion),
                    "La prueba no corre sobre reservas_test; no se toca ningun dato.");
            conexion.setAutoCommit(false);
            try {
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

    private static void insertarUsuario(Connection conexion, String id, String claveHash, Rol rol, boolean activo)
            throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement(
                "INSERT INTO usuario (id_usuario, clave_hash, rol, activo) VALUES (?, ?, ?, ?)")) {
            sentencia.setString(1, id);
            sentencia.setString(2, claveHash);
            sentencia.setString(3, rol.name());
            sentencia.setBoolean(4, activo);
            sentencia.executeUpdate();
        }
    }

    private static void insertarFuncionario(Connection conexion, String id, String nombre, String telefono)
            throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement(
                "INSERT INTO funcionario (id_funcionario, nombre, telefono) VALUES (?, ?, ?)")) {
            sentencia.setString(1, id);
            sentencia.setString(2, nombre);
            sentencia.setString(3, telefono);
            sentencia.executeUpdate();
        }
    }
}
