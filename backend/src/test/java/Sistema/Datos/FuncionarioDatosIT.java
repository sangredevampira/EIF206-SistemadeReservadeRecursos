package Sistema.Datos;

import Sistema.DTO.Funcionario;
import Sistema.DTO.ResultadoOperacionDTO;
import Sistema.DTO.Rol;
import Sistema.Logica.FuncionarioLogica;
import Sistema.Logica.ReservaLogica;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Prueba de integracion de FuncionarioDatos contra MySQL (base reservas_test).
 * Mismo patron que UsuarioDatosIT: se salta si no existe
 * servidor-test.properties, comprueba que la base sea reservas_test antes de
 * tocar datos y cada prueba termina con rollback (usa las versiones que
 * reciben la conexion). Los IDs empiezan con "it-func" para no chocar con
 * los de otras pruebas; las listas se revisan solo sobre esos IDs.
 *
 * Incluye el rollback de usuario + funcionario cuando falla el segundo
 * INSERT: con JDBC directo y de punta a punta con FuncionarioLogica.
 */
public class FuncionarioDatosIT {

    private static final String PREFIJO = "it-func";
    private static final String ID_A = "it-func-a";
    private static final String ID_B = "it-func-b";
    private static final String ID_INACTIVO = "it-func-inactivo";
    private static final String ID_ADMIN = "it-func-admin";
    private static final String ID_ROLLBACK = "it-func-rollback";

    private final FuncionarioDatos datos = new FuncionarioDatos();

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

    // Seguro: la prueba de punta a punta no deberia confirmar nada, pero si
    // fallara a la mitad se borra lo que haya quedado (solo en reservas_test).
    @AfterEach
    void borrarLoQueHayaQuedadoConfirmado() throws SQLException {
        String ruta = System.getProperty(ConexionBD.PROPIEDAD_CONFIG);
        if (ruta == null || !Files.isRegularFile(Path.of(ruta))) {
            return;
        }
        try (Connection conexion = ConexionBD.obtenerConexion()) {
            if (!"reservas_test".equals(baseActual(conexion))) {
                return;
            }
            ejecutar(conexion, "DELETE FROM funcionario WHERE id_funcionario = ?", ID_ROLLBACK);
            ejecutar(conexion, "DELETE FROM usuario WHERE id_usuario = ?", ID_ROLLBACK);
        }
    }

    // --- insertar y buscar ---

    @Test
    void insertarUsuarioYFuncionarioYBuscarloPorId() throws SQLException {
        enTransaccionDePrueba(conexion -> {
            new UsuarioDatos().insertar(conexion, ID_A, "hash-de-prueba", Rol.FUNCIONARIO);
            datos.insertar(conexion, new Funcionario(ID_A, "Ana Prueba", "8888-8888"));

            Funcionario leido = datos.buscarActivoPorId(conexion, ID_A);

            assertNotNull(leido);
            assertEquals(ID_A, leido.getId());
            assertEquals("Ana Prueba", leido.getNombre());
            assertEquals("8888-8888", leido.getTelefono());
        });
    }

    @Test
    void buscarActivoPorIdDeUnInactivoDaNull() throws SQLException {
        enTransaccionDePrueba(conexion -> {
            insertarFuncionario(conexion, ID_INACTIVO, "Inactivo Prueba", "8000-0000", false);

            assertNull(datos.buscarActivoPorId(conexion, ID_INACTIVO));
            assertNull(datos.buscarActivoPorId(conexion, "it-func-no-existe"));
        });
    }

    @Test
    void buscarFuncionarioAsociadoDevuelveSusDatosAunqueEsteInactivoYNullParaElAdministrador()
            throws SQLException {
        enTransaccionDePrueba(conexion -> {
            insertarUsuario(conexion, ID_ADMIN, Rol.ADMINISTRADOR, true);
            insertarFuncionario(conexion, ID_A, "Ana Prueba", "8888-8888", true);
            insertarFuncionario(conexion, ID_INACTIVO, "Inactivo Prueba", "8000-0000", false);

            Funcionario funcionario = datos.buscarFuncionarioAsociado(conexion, ID_A);
            assertNotNull(funcionario);
            assertEquals("Ana Prueba", funcionario.getNombre());
            assertEquals("8888-8888", funcionario.getTelefono());
            // no filtra activo: el login ya rechazo al usuario inactivo antes
            assertNotNull(datos.buscarFuncionarioAsociado(conexion, ID_INACTIVO));
            assertNull(datos.buscarFuncionarioAsociado(conexion, ID_ADMIN));
        });
    }

    // --- listas y busquedas (solo activos) ---

    @Test
    void listarActivosExcluyeLosInactivosYOrdenaPorId() throws SQLException {
        enTransaccionDePrueba(conexion -> {
            insertarFuncionario(conexion, ID_B, "Beto Prueba", "8111-1111", true);
            insertarFuncionario(conexion, ID_A, "Ana Prueba", "8888-8888", true);
            insertarFuncionario(conexion, ID_INACTIVO, "Inactivo Prueba", "8000-0000", false);

            assertEquals(List.of(ID_A, ID_B), idsDePrueba(datos.listarActivos(conexion)));
        });
    }

    @Test
    void buscarPorIdParcialSinDistinguirMayusculas() throws SQLException {
        enTransaccionDePrueba(conexion -> {
            insertarFuncionario(conexion, ID_A, "Ana Prueba", "8888-8888", true);
            insertarFuncionario(conexion, ID_B, "Beto Prueba", "8111-1111", true);

            assertEquals(List.of(ID_A), idsDePrueba(datos.buscar(conexion, "IT-FUNC-A", null)));
        });
    }

    @Test
    void buscarPorNombreParcialSinDistinguirMayusculas() throws SQLException {
        enTransaccionDePrueba(conexion -> {
            insertarFuncionario(conexion, ID_A, "Ana Prueba", "8888-8888", true);
            insertarFuncionario(conexion, ID_B, "Beto Prueba", "8111-1111", true);
            insertarFuncionario(conexion, ID_INACTIVO, "Beto Inactivo", "8000-0000", false);

            assertEquals(List.of(ID_B), idsDePrueba(datos.buscar(conexion, null, "beto")));
        });
    }

    @Test
    void conLosDosFiltrosSeCombinanConAnd() throws SQLException {
        enTransaccionDePrueba(conexion -> {
            insertarFuncionario(conexion, ID_A, "Ana Prueba", "8888-8888", true);
            insertarFuncionario(conexion, ID_B, "Beto Prueba", "8111-1111", true);

            // los dos coinciden por ID, pero solo uno por nombre
            assertEquals(List.of(ID_B), idsDePrueba(datos.buscar(conexion, PREFIJO, "beto")));
            // coincide por ID pero no por nombre: queda fuera
            assertEquals(List.of(), idsDePrueba(datos.buscar(conexion, ID_A, "beto")));
        });
    }

    @Test
    void losComodinesDelUsuarioSeBuscanComoTexto() throws SQLException {
        enTransaccionDePrueba(conexion -> {
            insertarFuncionario(conexion, ID_A, "Prueba 50% IT", "8888-8888", true);
            insertarFuncionario(conexion, ID_B, "Prueba 500 IT", "8111-1111", true);

            assertEquals(List.of(ID_A), idsDePrueba(datos.buscar(conexion, null, "50%")));
            assertEquals(List.of(), idsDePrueba(datos.buscar(conexion, "it_func", null)));
        });
    }

    @Test
    void buscarSinFiltrosEsIgualAListarActivos() throws SQLException {
        enTransaccionDePrueba(conexion -> {
            insertarFuncionario(conexion, ID_A, "Ana Prueba", "8888-8888", true);
            insertarFuncionario(conexion, ID_INACTIVO, "Inactivo Prueba", "8000-0000", false);

            assertEquals(idsDePrueba(datos.listarActivos(conexion)), idsDePrueba(datos.buscar(conexion, null, null)));
        });
    }

    // --- actualizar y desactivar ---

    @Test
    void actualizarCambiaNombreYTelefonoSoloSiEstaActivo() throws SQLException {
        enTransaccionDePrueba(conexion -> {
            insertarFuncionario(conexion, ID_A, "Ana Prueba", "8888-8888", true);
            insertarFuncionario(conexion, ID_INACTIVO, "Inactivo Prueba", "8000-0000", false);

            assertTrue(datos.actualizar(conexion, new Funcionario(ID_A, "Ana Maria", "8000-1111")));
            Funcionario leido = datos.buscarActivoPorId(conexion, ID_A);
            assertEquals("Ana Maria", leido.getNombre());
            assertEquals("8000-1111", leido.getTelefono());

            assertFalse(datos.actualizar(conexion, new Funcionario(ID_INACTIVO, "Otro", "8000-2222")));
        });
    }

    @Test
    void desactivarDevuelveTrueLaPrimeraVezYFalseLaSegunda() throws SQLException {
        enTransaccionDePrueba(conexion -> {
            insertarFuncionario(conexion, ID_A, "Ana Prueba", "8888-8888", true);

            assertTrue(datos.desactivar(conexion, ID_A));
            assertNull(datos.buscarActivoPorId(conexion, ID_A));
            assertFalse(datos.desactivar(conexion, ID_A));
        });
    }

    // --- errores que interpreta FuncionarioLogica ---

    @Test
    void unFuncionarioSinUsuarioDaElError1452() throws SQLException {
        enTransaccionDePrueba(conexion -> {
            SQLException error = assertThrows(SQLException.class,
                    () -> datos.insertar(conexion, new Funcionario("it-func-sin-usuario", "Sin Usuario", "8000-0000")));
            assertEquals(1452, error.getErrorCode());
        });
    }

    @Test
    void unIdRepetidoConOtrasMayusculasDaElError1062() throws SQLException {
        enTransaccionDePrueba(conexion -> {
            insertarFuncionario(conexion, ID_A, "Ana Prueba", "8888-8888", true);

            SQLException error = assertThrows(SQLException.class,
                    () -> datos.insertar(conexion, new Funcionario(ID_A.toUpperCase(), "Otra", "8000-0000")));
            assertEquals(1062, error.getErrorCode());
        });
    }

    // --- rollback de usuario + funcionario ---

    @Test
    void siFallaElInsertDelFuncionarioElRollbackQuitaTambienElUsuario() throws SQLException {
        enTransaccionDePrueba(conexion -> {
            new UsuarioDatos().insertar(conexion, ID_ROLLBACK, "hash-de-prueba", Rol.FUNCIONARIO);

            // nombre NULL en una columna NOT NULL: MySQL siempre lo rechaza (1048)
            SQLException error = assertThrows(SQLException.class,
                    () -> datos.insertar(conexion, new Funcionario(ID_ROLLBACK, null, "8000-0000")));
            assertEquals(1048, error.getErrorCode());

            conexion.rollback();
            assertNull(new UsuarioDatos().buscarPorId(conexion, ID_ROLLBACK));
        });
    }

    @Test
    void registrarDeshaceElUsuarioSiFallaElSegundoInsertDePuntaAPunta() throws SQLException {
        // la Logica real abre su conexion con ConexionBD: confirmar antes que es reservas_test
        try (Connection conexion = ConexionBD.obtenerConexion()) {
            assertEquals("reservas_test", baseActual(conexion),
                    "La prueba no corre sobre reservas_test; no se toca ningun dato.");
        }

        // el segundo INSERT llega a MySQL de verdad, pero con nombre NULL (error 1048)
        FuncionarioDatos datosQueFallan = new FuncionarioDatos() {
            @Override
            public void insertar(Connection conexion, Funcionario funcionario) throws SQLException {
                super.insertar(conexion, new Funcionario(funcionario.getId(), null, funcionario.getTelefono()));
            }
        };
        FuncionarioLogica logica = new FuncionarioLogica(
                datosQueFallan, new UsuarioDatos(), new ReservaLogica(), ConexionBD::obtenerConexion);

        ResultadoOperacionDTO resultado = logica.registrar(new Funcionario(ID_ROLLBACK, "Rollback Prueba", "8000-0000"));

        assertFalse(resultado.isExito());
        assertEquals(UsuarioDatos.MENSAJE_ERROR_BD, resultado.getMensaje());
        // con OTRA conexion: no quedo ni el usuario ni el funcionario
        try (Connection otra = ConexionBD.obtenerConexion()) {
            assertNull(new UsuarioDatos().buscarPorId(otra, ID_ROLLBACK));
            assertNull(datos.buscarFuncionarioAsociado(otra, ID_ROLLBACK));
        }
    }

    @Test
    void laVersionQueAbreSuPropiaConexionFunciona() {
        // no inserta nada: solo prueba el camino que abre y cierra su conexion
        assertNull(datos.buscarActivoPorId("it-func-no-existe"));
        assertTrue(idsDePrueba(datos.buscar("it-func-no-existe", null)).isEmpty());
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

    // solo los IDs de esta prueba, en el orden en que llegaron
    private static List<String> idsDePrueba(List<Funcionario> funcionarios) {
        return funcionarios.stream().map(Funcionario::getId).filter(id -> id.startsWith(PREFIJO)).toList();
    }

    private static void insertarUsuario(Connection conexion, String id, Rol rol, boolean activo)
            throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement(
                "INSERT INTO usuario (id_usuario, clave_hash, rol, activo) VALUES (?, 'hash-de-prueba', ?, ?)")) {
            sentencia.setString(1, id);
            sentencia.setString(2, rol.name());
            sentencia.setBoolean(3, activo);
            sentencia.executeUpdate();
        }
    }

    // usuario + funcionario con el mismo estado, como los deja la Logica
    private static void insertarFuncionario(Connection conexion, String id, String nombre, String telefono,
                                            boolean activo) throws SQLException {
        insertarUsuario(conexion, id, Rol.FUNCIONARIO, activo);
        try (PreparedStatement sentencia = conexion.prepareStatement(
                "INSERT INTO funcionario (id_funcionario, nombre, telefono, activo) VALUES (?, ?, ?, ?)")) {
            sentencia.setString(1, id);
            sentencia.setString(2, nombre);
            sentencia.setString(3, telefono);
            sentencia.setBoolean(4, activo);
            sentencia.executeUpdate();
        }
    }

    private static void ejecutar(Connection conexion, String sql, String id) throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, id);
            sentencia.executeUpdate();
        }
    }
}
