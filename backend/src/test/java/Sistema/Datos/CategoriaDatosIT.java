package Sistema.Datos;

import Sistema.DTO.CategoriaDTO;
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
 * Prueba de integracion de CategoriaDatos contra MySQL (base reservas_test).
 * Mismo patron que RecursoDatosIT: se salta si no existe
 * servidor-test.properties, comprueba que la base sea reservas_test antes de
 * tocar datos y cada prueba termina con rollback (usa las versiones que
 * reciben la conexion). Inserta sus propias categorias con IDs CAT-IT-...
 */
public class CategoriaDatosIT {

    private static final String CAT_UNO = "CAT-IT-01";
    private static final String CAT_DOS = "CAT-IT-02";
    private static final String CAT_TRES = "CAT-IT-03";

    private final CategoriaDatos datos = new CategoriaDatos();

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
    void listarTodosOrdenaPorId() throws SQLException {
        enTransaccionDePrueba(conexion -> {
            insertarCategoria(conexion, CAT_TRES, "Tercera");
            insertarCategoria(conexion, CAT_UNO, "Primera");
            insertarCategoria(conexion, CAT_DOS, "Segunda");

            List<CategoriaDTO> categorias = soloDePrueba(datos.listarTodos(conexion));

            assertEquals(List.of(CAT_UNO, CAT_DOS, CAT_TRES), ids(categorias));
            assertEquals("Primera", categorias.get(0).getDescripcion());
        });
    }

    @Test
    void buscarEsContieneSinDistinguirMayusculasPeroSiTildes() throws SQLException {
        enTransaccionDePrueba(conexion -> {
            insertarCategoria(conexion, CAT_UNO, "Sala azul");
            insertarCategoria(conexion, CAT_DOS, "Cámara web");

            assertEquals(List.of(CAT_UNO), ids(soloDePrueba(datos.buscar(conexion, "AZU"))));
            assertEquals(List.of(CAT_DOS), ids(soloDePrueba(datos.buscar(conexion, "cámara"))));
            assertTrue(soloDePrueba(datos.buscar(conexion, "camara")).isEmpty());
        });
    }

    @Test
    void losComodinesDelUsuarioSeBuscanComoTexto() throws SQLException {
        enTransaccionDePrueba(conexion -> {
            insertarCategoria(conexion, "CAT-IT-01", "Descuento 50%");
            insertarCategoria(conexion, "CAT-IT-02", "Mesa 500 kg");
            insertarCategoria(conexion, "CAT-IT-03", "cable_usb");
            insertarCategoria(conexion, "CAT-IT-04", "cableXusb");
            insertarCategoria(conexion, "CAT-IT-05", "Oferta!");
            insertarCategoria(conexion, "CAT-IT-06", "C:\\datos");

            assertEquals(List.of("CAT-IT-01"), ids(soloDePrueba(datos.buscar(conexion, "50%"))));
            assertEquals(List.of("CAT-IT-03"), ids(soloDePrueba(datos.buscar(conexion, "e_u"))));
            assertEquals(List.of("CAT-IT-05"), ids(soloDePrueba(datos.buscar(conexion, "a!"))));
            assertEquals(List.of("CAT-IT-06"), ids(soloDePrueba(datos.buscar(conexion, "C:\\"))));
        });
    }

    @Test
    void buscarConNullDevuelveTodas() throws SQLException {
        enTransaccionDePrueba(conexion -> {
            insertarCategoria(conexion, CAT_UNO, "Primera");
            insertarCategoria(conexion, CAT_DOS, "Segunda");

            assertEquals(List.of(CAT_UNO, CAT_DOS), ids(soloDePrueba(datos.buscar(conexion, null))));
        });
    }

    @Test
    void buscarPorIdDevuelveLaCategoriaONull() throws SQLException {
        enTransaccionDePrueba(conexion -> {
            insertarCategoria(conexion, CAT_UNO, "Primera");

            CategoriaDTO encontrada = datos.buscarPorId(conexion, CAT_UNO);
            assertNotNull(encontrada);
            assertEquals("Primera", encontrada.getDescripcion());

            assertNull(datos.buscarPorId(conexion, "CAT-IT-99"));
        });
    }

    @Test
    void existeDescripcionUsaLaMismaComparacionQueElUnique() throws SQLException {
        enTransaccionDePrueba(conexion -> {
            insertarCategoria(conexion, CAT_UNO, "Laptop IT");

            assertTrue(datos.existeDescripcion(conexion, "LAPTOP it", null));     // sin mayusculas
            assertFalse(datos.existeDescripcion(conexion, "Láptop IT", null));    // con tildes si distingue
            assertFalse(datos.existeDescripcion(conexion, "Laptop IT ", null));   // NO PAD: el espacio cuenta

            // al modificar, la propia categoria no cuenta; otra si
            assertFalse(datos.existeDescripcion(conexion, "LAPTOP IT", CAT_UNO));
            assertTrue(datos.existeDescripcion(conexion, "LAPTOP IT", CAT_DOS));
        });
    }

    @Test
    void siguienteIdDaElNumeroSiguienteConFormatoCat() throws SQLException {
        enTransaccionDePrueba(conexion -> {
            // un numero alto para no depender de otras filas de reservas_test
            insertarCategoria(conexion, "CAT-900000", "Categoria IT alta");

            assertEquals("CAT-900001", datos.siguienteId(conexion));
        });
    }

    @Test
    void insertarGuardaYLaBaseDaLosCodigosQueTraduceLaLogica() throws SQLException {
        enTransaccionDePrueba(conexion -> {
            datos.insertar(conexion, new CategoriaDTO(CAT_UNO, "Laptop IT"));
            assertEquals("Laptop IT", datos.buscarPorId(conexion, CAT_UNO).getDescripcion());

            // descripcion repetida con otras mayusculas -> 1062 (UNIQUE)
            SQLException repetida = assertThrows(SQLException.class,
                    () -> datos.insertar(conexion, new CategoriaDTO(CAT_DOS, "laptop it")));
            assertEquals(1062, repetida.getErrorCode());

            // ID repetido -> 1062 (PK)
            SQLException idRepetido = assertThrows(SQLException.class,
                    () -> datos.insertar(conexion, new CategoriaDTO(CAT_UNO, "Otra IT")));
            assertEquals(1062, idRepetido.getErrorCode());

            // espacio al final -> 3819 (CHECK); la Logica recorta antes, esto es la ultima proteccion
            SQLException conEspacio = assertThrows(SQLException.class,
                    () -> datos.insertar(conexion, new CategoriaDTO(CAT_TRES, "Proyector IT ")));
            assertEquals(3819, conEspacio.getErrorCode());
        });
    }

    @Test
    void actualizarCambiaLaDescripcion() throws SQLException {
        enTransaccionDePrueba(conexion -> {
            insertarCategoria(conexion, CAT_UNO, "Antes");
            insertarCategoria(conexion, CAT_DOS, "Otra");

            assertTrue(datos.actualizar(conexion, new CategoriaDTO(CAT_UNO, "Despues")));
            assertEquals("Despues", datos.buscarPorId(conexion, CAT_UNO).getDescripcion());

            // mismos valores: Connector/J cuenta filas encontradas, no cambiadas
            assertTrue(datos.actualizar(conexion, new CategoriaDTO(CAT_UNO, "Despues")));

            assertFalse(datos.actualizar(conexion, new CategoriaDTO("CAT-IT-99", "Nueva")));

            SQLException repetida = assertThrows(SQLException.class,
                    () -> datos.actualizar(conexion, new CategoriaDTO(CAT_UNO, "OTRA")));
            assertEquals(1062, repetida.getErrorCode());
        });
    }

    @Test
    void eliminarEsUnBorradoFisico() throws SQLException {
        enTransaccionDePrueba(conexion -> {
            insertarCategoria(conexion, CAT_UNO, "Primera");

            assertTrue(datos.eliminar(conexion, CAT_UNO));
            assertNull(datos.buscarPorId(conexion, CAT_UNO));
            assertFalse(datos.eliminar(conexion, CAT_UNO));
        });
    }

    @Test
    void eliminarUnaCategoriaConRecursosInactivosDaElError1451() throws SQLException {
        enTransaccionDePrueba(conexion -> {
            insertarCategoria(conexion, CAT_UNO, "Primera");
            insertarRecursoInactivo(conexion, "REC-IT-1", CAT_UNO);

            SQLException conRecursos = assertThrows(SQLException.class, () -> datos.eliminar(conexion, CAT_UNO));
            assertEquals(1451, conRecursos.getErrorCode());
            assertNotNull(datos.buscarPorId(conexion, CAT_UNO));
        });
    }

    @Test
    void lasVersionesQueAbrenSuPropiaConexionFuncionan() {
        // no insertan nada: solo prueban el camino que abre y cierra su conexion
        assertNull(datos.buscarPorId("CAT-IT-99"));
        assertFalse(datos.existeDescripcion("Descripcion IT que no existe", null));
        assertTrue(soloDePrueba(datos.buscar("Descripcion IT que no existe")).isEmpty());
        assertNotNull(datos.listarTodos());
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

    private static void insertarCategoria(Connection conexion, String id, String descripcion) throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement(
                "INSERT INTO categoria (id_categoria, descripcion) VALUES (?, ?)")) {
            sentencia.setString(1, id);
            sentencia.setString(2, descripcion);
            sentencia.executeUpdate();
        }
    }

    private static void insertarRecursoInactivo(Connection conexion, String id, String idCategoria)
            throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement(
                "INSERT INTO recurso (id_recurso, descripcion, id_categoria, activo) VALUES (?, ?, ?, 0)")) {
            sentencia.setString(1, id);
            sentencia.setString(2, "Recurso IT inactivo");
            sentencia.setString(3, idCategoria);
            sentencia.executeUpdate();
        }
    }

    // las consultas pueden ver otras filas de reservas_test; solo cuentan las de esta prueba
    private static List<CategoriaDTO> soloDePrueba(List<CategoriaDTO> categorias) {
        return categorias.stream().filter(c -> c.getId().startsWith("CAT-IT-")).toList();
    }

    private static List<String> ids(List<CategoriaDTO> categorias) {
        return categorias.stream().map(CategoriaDTO::getId).toList();
    }
}
