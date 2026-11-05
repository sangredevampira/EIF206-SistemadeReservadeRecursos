package Sistema.Datos;

import Sistema.DTO.CategoriaDTO;
import Sistema.DTO.RecursoDTO;
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
 * Prueba de integracion de RecursoDatos contra MySQL (base reservas_test).
 * Mismo patron que UsuarioDatosIT: se salta si no existe
 * servidor-test.properties, comprueba que la base sea reservas_test antes de
 * tocar datos y cada prueba termina con rollback (usa las versiones que
 * reciben la conexion). Inserta sus propias categorias y recursos.
 */
public class RecursoDatosIT {

    private static final String CAT_UNO = "CAT-IT-01";
    private static final String CAT_DOS = "CAT-IT-02";

    private final RecursoDatos datos = new RecursoDatos();

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
    void filtrarPorCategoriaDevuelveSoloActivosOrdenadosYConSuCategoria() throws SQLException {
        enTransaccionDePrueba(conexion -> {
            insertarCategorias(conexion);
            insertarRecurso(conexion, "REC-IT-3", "Sala azul", CAT_UNO, true);
            insertarRecurso(conexion, "REC-IT-1", "Sala roja", CAT_UNO, true);
            insertarRecurso(conexion, "REC-IT-2", "Sala vieja", CAT_UNO, false);
            insertarRecurso(conexion, "REC-IT-4", "Laptop", CAT_DOS, true);

            List<RecursoDTO> recursos = datos.filtrarPorCategoria(conexion, CAT_UNO);

            assertEquals(List.of("REC-IT-1", "REC-IT-3"), ids(recursos));
            RecursoDTO primero = recursos.get(0);
            assertEquals("Sala roja", primero.getDescripcion());
            assertEquals(CAT_UNO, primero.getCategoria().getId());
            assertEquals("Categoria IT uno", primero.getCategoria().getDescripcion());
        });
    }

    @Test
    void filtrarPorDescripcionEsContieneSinDistinguirMayusculasPeroSiTildes() throws SQLException {
        enTransaccionDePrueba(conexion -> {
            insertarCategorias(conexion);
            insertarRecurso(conexion, "REC-IT-1", "Sala azul", CAT_UNO, true);
            insertarRecurso(conexion, "REC-IT-2", "Cámara web", CAT_UNO, true);

            assertEquals(List.of("REC-IT-1"), ids(datos.filtrar(conexion, CAT_UNO, "AZU")));
            assertEquals(List.of("REC-IT-2"), ids(datos.filtrar(conexion, CAT_UNO, "cámara")));
            assertTrue(datos.filtrar(conexion, CAT_UNO, "camara").isEmpty());
        });
    }

    @Test
    void losComodinesDelUsuarioSeBuscanComoTexto() throws SQLException {
        enTransaccionDePrueba(conexion -> {
            insertarCategorias(conexion);
            insertarRecurso(conexion, "REC-IT-1", "Descuento 50%", CAT_UNO, true);
            insertarRecurso(conexion, "REC-IT-2", "Mesa 500 kg", CAT_UNO, true);
            insertarRecurso(conexion, "REC-IT-3", "cable_usb", CAT_UNO, true);
            insertarRecurso(conexion, "REC-IT-4", "cableXusb", CAT_UNO, true);
            insertarRecurso(conexion, "REC-IT-5", "Oferta!", CAT_UNO, true);
            insertarRecurso(conexion, "REC-IT-6", "C:\\datos", CAT_UNO, true);

            assertEquals(List.of("REC-IT-1"), ids(datos.filtrar(conexion, CAT_UNO, "50%")));
            assertEquals(List.of("REC-IT-3"), ids(datos.filtrar(conexion, CAT_UNO, "e_u")));
            assertEquals(List.of("REC-IT-5"), ids(datos.filtrar(conexion, CAT_UNO, "a!")));
            assertEquals(List.of("REC-IT-6"), ids(datos.filtrar(conexion, CAT_UNO, "C:\\")));
        });
    }

    @Test
    void filtrarConNullNoAplicaEseFiltro() throws SQLException {
        enTransaccionDePrueba(conexion -> {
            insertarCategorias(conexion);
            insertarRecurso(conexion, "REC-IT-1", "Sala azul", CAT_UNO, true);
            insertarRecurso(conexion, "REC-IT-2", "Sala de laptops", CAT_DOS, true);

            assertEquals(List.of("REC-IT-1", "REC-IT-2"), ids(soloDePrueba(datos.filtrar(conexion, null, "sala"))));
            assertEquals(List.of("REC-IT-2"), ids(datos.filtrar(conexion, CAT_DOS, null)));
        });
    }

    @Test
    void listarActivosExcluyeLosInactivos() throws SQLException {
        enTransaccionDePrueba(conexion -> {
            insertarCategorias(conexion);
            insertarRecurso(conexion, "REC-IT-1", "Activo", CAT_UNO, true);
            insertarRecurso(conexion, "REC-IT-2", "Inactivo", CAT_DOS, false);

            assertEquals(List.of("REC-IT-1"), ids(soloDePrueba(datos.listarActivos(conexion))));
        });
    }

    @Test
    void buscarActivoPorIdTrataAlInactivoComoNoEncontrado() throws SQLException {
        enTransaccionDePrueba(conexion -> {
            insertarCategorias(conexion);
            insertarRecurso(conexion, "REC-IT-1", "Activo", CAT_UNO, true);
            insertarRecurso(conexion, "REC-IT-2", "Inactivo", CAT_UNO, false);

            RecursoDTO activo = datos.buscarActivoPorId(conexion, "REC-IT-1");
            assertNotNull(activo);
            assertEquals("Activo", activo.getDescripcion());
            assertEquals(CAT_UNO, activo.getCategoria().getId());

            assertNull(datos.buscarActivoPorId(conexion, "REC-IT-2"));
            assertNull(datos.buscarActivoPorId(conexion, "REC-IT-9"));
        });
    }

    @Test
    void existeIdCuentaLosInactivos() throws SQLException {
        enTransaccionDePrueba(conexion -> {
            insertarCategorias(conexion);
            insertarRecurso(conexion, "REC-IT-2", "Inactivo", CAT_UNO, false);

            assertTrue(datos.existeId(conexion, "REC-IT-2"));
            assertFalse(datos.existeId(conexion, "REC-IT-9"));
        });
    }

    @Test
    void insertarGuardaUnRecursoActivoYLaBaseDaLosCodigosQueTraduceLaLogica() throws SQLException {
        enTransaccionDePrueba(conexion -> {
            insertarCategorias(conexion);
            datos.insertar(conexion, recurso("REC-IT-1", "Nuevo", CAT_UNO));

            assertEquals("Nuevo", datos.buscarActivoPorId(conexion, "REC-IT-1").getDescripcion());

            // ID repetido -> 1062 (RecursoLogica lo traduce a un mensaje)
            SQLException repetido = assertThrows(SQLException.class,
                    () -> datos.insertar(conexion, recurso("REC-IT-1", "Otro", CAT_UNO)));
            assertEquals(1062, repetido.getErrorCode());

            // categoria inexistente -> 1452
            SQLException sinCategoria = assertThrows(SQLException.class,
                    () -> datos.insertar(conexion, recurso("REC-IT-2", "Otro", "CAT-IT-99")));
            assertEquals(1452, sinCategoria.getErrorCode());
        });
    }

    @Test
    void actualizarCambiaSoloRecursosActivos() throws SQLException {
        enTransaccionDePrueba(conexion -> {
            insertarCategorias(conexion);
            insertarRecurso(conexion, "REC-IT-1", "Antes", CAT_UNO, true);
            insertarRecurso(conexion, "REC-IT-2", "Inactivo", CAT_UNO, false);

            assertTrue(datos.actualizar(conexion, recurso("REC-IT-1", "Despues", CAT_DOS)));
            RecursoDTO cambiado = datos.buscarActivoPorId(conexion, "REC-IT-1");
            assertEquals("Despues", cambiado.getDescripcion());
            assertEquals(CAT_DOS, cambiado.getCategoria().getId());

            // mismos valores: Connector/J cuenta filas encontradas, no cambiadas
            assertTrue(datos.actualizar(conexion, recurso("REC-IT-1", "Despues", CAT_DOS)));

            assertFalse(datos.actualizar(conexion, recurso("REC-IT-2", "Nuevo", CAT_UNO)));
            assertFalse(datos.actualizar(conexion, recurso("REC-IT-9", "Nuevo", CAT_UNO)));

            SQLException sinCategoria = assertThrows(SQLException.class,
                    () -> datos.actualizar(conexion, recurso("REC-IT-1", "X", "CAT-IT-99")));
            assertEquals(1452, sinCategoria.getErrorCode());
        });
    }

    @Test
    void desactivarEsUnaBajaLogica() throws SQLException {
        enTransaccionDePrueba(conexion -> {
            insertarCategorias(conexion);
            insertarRecurso(conexion, "REC-IT-1", "Activo", CAT_UNO, true);

            assertTrue(datos.desactivar(conexion, "REC-IT-1"));
            assertFalse(datos.desactivar(conexion, "REC-IT-1"));     // ya estaba inactivo

            assertNull(datos.buscarActivoPorId(conexion, "REC-IT-1"));
            assertTrue(datos.existeId(conexion, "REC-IT-1"));        // la fila sigue ahi
            assertFalse(datos.desactivar(conexion, "REC-IT-9"));
        });
    }

    @Test
    void categoriaTieneRecursosCuentaLosInactivos() throws SQLException {
        enTransaccionDePrueba(conexion -> {
            insertarCategorias(conexion);
            insertarRecurso(conexion, "REC-IT-1", "Inactivo", CAT_UNO, false);

            assertTrue(datos.categoriaTieneRecursos(conexion, CAT_UNO));
            assertFalse(datos.categoriaTieneRecursos(conexion, CAT_DOS));
        });
    }

    @Test
    void lasVersionesQueAbrenSuPropiaConexionFuncionan() {
        // no insertan nada: solo prueban el camino que abre y cierra su conexion
        assertNull(datos.buscarActivoPorId("REC-IT-9"));
        assertFalse(datos.existeId("REC-IT-9"));
        assertTrue(datos.filtrarPorCategoria("CAT-IT-99").isEmpty());
        assertFalse(datos.categoriaTieneRecursos("CAT-IT-99"));
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

    private static void insertarCategorias(Connection conexion) throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement(
                "INSERT INTO categoria (id_categoria, descripcion) VALUES (?, ?), (?, ?)")) {
            sentencia.setString(1, CAT_UNO);
            sentencia.setString(2, "Categoria IT uno");
            sentencia.setString(3, CAT_DOS);
            sentencia.setString(4, "Categoria IT dos");
            sentencia.executeUpdate();
        }
    }

    private static void insertarRecurso(Connection conexion, String id, String descripcion,
                                        String idCategoria, boolean activo) throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement(
                "INSERT INTO recurso (id_recurso, descripcion, id_categoria, activo) VALUES (?, ?, ?, ?)")) {
            sentencia.setString(1, id);
            sentencia.setString(2, descripcion);
            sentencia.setString(3, idCategoria);
            sentencia.setBoolean(4, activo);
            sentencia.executeUpdate();
        }
    }

    private static RecursoDTO recurso(String id, String descripcion, String idCategoria) {
        return new RecursoDTO(id, descripcion, new CategoriaDTO(idCategoria, null));
    }

    // las consultas sin categoria pueden ver otras filas de reservas_test; solo cuentan las de esta prueba
    private static List<RecursoDTO> soloDePrueba(List<RecursoDTO> recursos) {
        return recursos.stream().filter(r -> r.getId().startsWith("REC-IT-")).toList();
    }

    private static List<String> ids(List<RecursoDTO> recursos) {
        return recursos.stream().map(RecursoDTO::getId).toList();
    }
}
