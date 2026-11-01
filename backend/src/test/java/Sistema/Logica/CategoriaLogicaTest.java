package Sistema.Logica;

import Sistema.DTO.CategoriaDTO;
import Sistema.DTO.ResultadoOperacionDTO;
import Sistema.Datos.CategoriaDatos;
import Sistema.Datos.RecursoDatos;
import Sistema.Datos.UsuarioDatos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas de unidad de CategoriaLogica: reglas de negocio y manejo de las
 * transacciones, SIN MySQL. Sus tres dependencias se reemplazan por dobles
 * (se pasan por el constructor de pruebas):
 *   - CategoriaDatosFake: las categorias viven en un mapa en memoria;
 *   - RecursoDatosFake: responde si una categoria tiene recursos;
 *   - ConexionFalsa: anota setAutoCommit, commit, rollback y close.
 * Los dobles anotan si cada llamada con conexion se hizo con
 * CANDADO_CATEGORIAS tomado. Las consultas SQL reales se prueban en
 * CategoriaDatosIT.
 */
class CategoriaLogicaTest {

    // --- dobles de prueba ---

    /**
     * CategoriaDatos en memoria. Sobrescribe solo lo que usa CategoriaLogica;
     * nunca abre conexiones. existeDescripcion compara con equalsIgnoreCase,
     * como la collation de MySQL. Si errorAlEscribir no es null, insertar,
     * actualizar y eliminar lo lanzan (simula 1062, 1451, etc.).
     */
    static class CategoriaDatosFake extends CategoriaDatos {
        final Map<String, String> categorias = new LinkedHashMap<>();
        String proximoId = "CAT-000008";
        SQLException errorAlEscribir;
        boolean baseCaida = false;

        // lo que la Logica le paso
        final List<Connection> conexionesRecibidas = new ArrayList<>();
        boolean todoConElCandadoTomado = true;
        boolean seLlamoInsertar = false;
        boolean seLlamoEliminar = false;
        String descripcionBuscada = "sin llamar";

        private void anotar(Connection conexion) {
            conexionesRecibidas.add(conexion);
            todoConElCandadoTomado &= Thread.holdsLock(CategoriaLogica.CANDADO_CATEGORIAS);
        }

        @Override
        public List<CategoriaDTO> listarTodos() {
            if (baseCaida) throw new IllegalStateException(UsuarioDatos.MENSAJE_ERROR_BD);
            return categorias.entrySet().stream().map(e -> new CategoriaDTO(e.getKey(), e.getValue())).toList();
        }

        @Override
        public List<CategoriaDTO> buscar(String descripcion) {
            descripcionBuscada = descripcion;
            return listarTodos();
        }

        @Override
        public CategoriaDTO buscarPorId(Connection conexion, String id) {
            anotar(conexion);
            return categorias.containsKey(id) ? new CategoriaDTO(id, categorias.get(id)) : null;
        }

        @Override
        public boolean existeDescripcion(Connection conexion, String descripcion, String idAExcluir) {
            anotar(conexion);
            return categorias.entrySet().stream().anyMatch(e ->
                    e.getValue().equalsIgnoreCase(descripcion) && !e.getKey().equals(idAExcluir));
        }

        @Override
        public String siguienteId(Connection conexion) {
            anotar(conexion);
            return proximoId;
        }

        @Override
        public void insertar(Connection conexion, CategoriaDTO categoria) throws SQLException {
            anotar(conexion);
            seLlamoInsertar = true;
            if (errorAlEscribir != null) throw errorAlEscribir;
            categorias.put(categoria.getId(), categoria.getDescripcion());
        }

        @Override
        public boolean actualizar(Connection conexion, CategoriaDTO categoria) throws SQLException {
            anotar(conexion);
            if (errorAlEscribir != null) throw errorAlEscribir;
            if (!categorias.containsKey(categoria.getId())) return false;
            categorias.put(categoria.getId(), categoria.getDescripcion());
            return true;
        }

        @Override
        public boolean eliminar(Connection conexion, String id) throws SQLException {
            anotar(conexion);
            seLlamoEliminar = true;
            if (errorAlEscribir != null) throw errorAlEscribir;
            return categorias.remove(id) != null;
        }
    }

    /** RecursoDatos falso: solo la version con conexion de categoriaTieneRecursos. */
    static class RecursoDatosFake extends RecursoDatos {
        final Set<String> categoriasConRecursos = new HashSet<>();
        boolean seConsulto = false;
        boolean consultoConElCandadoTomado = false;
        Connection conexionRecibida;

        @Override
        public boolean categoriaTieneRecursos(Connection conexion, String idCategoria) {
            seConsulto = true;
            conexionRecibida = conexion;
            consultoConElCandadoTomado = Thread.holdsLock(CategoriaLogica.CANDADO_CATEGORIAS);
            return categoriasConRecursos.contains(idCategoria);
        }
    }

    // --- preparacion ---

    private CategoriaDatosFake datos;
    private RecursoDatosFake recursos;
    private ConexionFalsa conexionFalsa;
    private int conexionesAbiertas;
    private boolean abrioConElCandadoTomado;
    private CategoriaLogica logica;

    private static final List<String> COMMIT = List.of("setAutoCommit(false)", "commit", "close");
    private static final List<String> ROLLBACK = List.of("setAutoCommit(false)", "rollback", "close");

    @BeforeEach
    void setUp() {
        datos = new CategoriaDatosFake();
        datos.categorias.put("CAT-000001", "Sala de estudio");
        datos.categorias.put("CAT-000002", "Laptop");
        recursos = new RecursoDatosFake();
        conexionFalsa = new ConexionFalsa();
        conexionesAbiertas = 0;
        // la FuenteConexion es una lambda: anota si se abrio dentro del candado
        logica = new CategoriaLogica(datos, recursos, () -> {
            conexionesAbiertas++;
            abrioConElCandadoTomado = Thread.holdsLock(CategoriaLogica.CANDADO_CATEGORIAS);
            return conexionFalsa.conexion;
        });
    }

    private static SQLException errorMySQL(int codigo) {
        return new SQLException("error simulado", "23000", codigo);
    }

    private static void assertFallo(String mensaje, ResultadoOperacionDTO resultado) {
        assertFalse(resultado.isExito());
        assertEquals(mensaje, resultado.getMensaje());
    }

    // --- registrar ---

    @Test
    void registrarValidoInsertaConElIdGeneradoHaceCommitYAsignaElId() {
        CategoriaDTO nueva = new CategoriaDTO(null, "Proyector");

        ResultadoOperacionDTO resultado = logica.registrar(nueva);

        assertTrue(resultado.isExito());
        assertNull(resultado.getMensaje());
        assertEquals("Proyector", datos.categorias.get("CAT-000008"));
        assertEquals("CAT-000008", nueva.getId());
        assertEquals(COMMIT, conexionFalsa.llamadas);
        assertEquals(1, conexionesAbiertas);
        // todos los pasos con la MISMA conexion y con el candado tomado
        assertTrue(datos.conexionesRecibidas.stream().allMatch(c -> c == conexionFalsa.conexion));
        assertTrue(datos.todoConElCandadoTomado);
        assertFalse(Thread.holdsLock(CategoriaLogica.CANDADO_CATEGORIAS));     // al terminar lo suelta
    }

    @Test
    void laConexionSeAbreDespuesDeTomarElCandado() {
        logica.registrar(new CategoriaDTO(null, "Proyector"));

        assertTrue(abrioConElCandadoTomado);
    }

    @Test
    void registrarGuardaLaDescripcionSinEspaciosAlInicioNiAlFinal() {
        CategoriaDTO nueva = new CategoriaDTO(null, "  Proyector  ");

        assertTrue(logica.registrar(nueva).isExito());

        assertEquals("Proyector", datos.categorias.get("CAT-000008"));
        assertEquals("  Proyector  ", nueva.getDescripcion());     // no cambia la descripcion recibida
    }

    @Test
    void registrarNuloFallaSinAbrirConexion() {
        assertFallo(CategoriaLogica.MENSAJE_NULA, logica.registrar(null));
        assertEquals(0, conexionesAbiertas);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "", "   " })
    void registrarConDescripcionVaciaFallaSinAbrirConexion(String descripcion) {
        assertFallo("La descripcion no puede estar vacia.", logica.registrar(new CategoriaDTO(null, descripcion)));
        assertEquals(0, conexionesAbiertas);
    }

    @Test
    void registrarAceptaHasta100CaracteresSinContarLosEspaciosDeLosExtremos() {
        assertTrue(logica.registrar(new CategoriaDTO(null, "  " + "a".repeat(100) + "  ")).isExito());

        assertFallo("La descripcion no puede tener mas de 100 caracteres.",
                logica.registrar(new CategoriaDTO(null, "b".repeat(101))));
    }

    @Test
    void registrarConDescripcionRepetidaSinImportarMayusculasHaceRollback() {
        CategoriaDTO nueva = new CategoriaDTO(null, "LAPTOP");

        assertFallo("Ya existe una categoria con esa descripcion.", logica.registrar(nueva));

        assertFalse(datos.seLlamoInsertar);
        assertNull(nueva.getId());
        assertEquals(ROLLBACK, conexionFalsa.llamadas);
    }

    @Test
    void registrarTraduceElError1062AlMensajeDeDescripcionRepetida() {
        datos.errorAlEscribir = errorMySQL(1062);
        CategoriaDTO nueva = new CategoriaDTO(null, "Proyector");

        assertFallo("Ya existe una categoria con esa descripcion.", logica.registrar(nueva));

        assertNull(nueva.getId());
        assertEquals(ROLLBACK, conexionFalsa.llamadas);
    }

    @Test
    void registrarConOtroErrorDeBaseHaceRollbackYDaElMensajeGenerico() {
        datos.errorAlEscribir = errorMySQL(1205);

        assertFallo(UsuarioDatos.MENSAJE_ERROR_BD, logica.registrar(new CategoriaDTO(null, "Proyector")));
        assertEquals(ROLLBACK, conexionFalsa.llamadas);
    }

    @Test
    void registrarSinPoderAbrirLaConexionDaElMensajeGenerico() {
        CategoriaLogica sinBase = new CategoriaLogica(datos, recursos, () -> {
            throw new SQLException("Communications link failure");
        });

        assertFallo(UsuarioDatos.MENSAJE_ERROR_BD, sinBase.registrar(new CategoriaDTO(null, "Proyector")));
    }

    // --- modificar ---

    @Test
    void modificarValidoCambiaLaDescripcionRecortadaYHaceCommit() {
        ResultadoOperacionDTO resultado = logica.modificar(new CategoriaDTO("CAT-000001", "  Sala grande  "));

        assertTrue(resultado.isExito());
        assertEquals("Sala grande", datos.categorias.get("CAT-000001"));
        assertEquals(COMMIT, conexionFalsa.llamadas);
        assertTrue(datos.todoConElCandadoTomado);
    }

    @Test
    void modificarPuedeCambiarSoloLasMayusculasDeLaPropiaCategoria() {
        assertTrue(logica.modificar(new CategoriaDTO("CAT-000002", "LAPTOP")).isExito());
        assertEquals("LAPTOP", datos.categorias.get("CAT-000002"));
    }

    @Test
    void modificarConLaDescripcionDeOtraCategoriaHaceRollback() {
        assertFallo("Ya existe otra categoria con esa descripcion.",
                logica.modificar(new CategoriaDTO("CAT-000001", "laptop")));

        assertEquals("Sala de estudio", datos.categorias.get("CAT-000001"));
        assertEquals(ROLLBACK, conexionFalsa.llamadas);
    }

    @Test
    void modificarTraduceElError1062AlMensajeDeOtraCategoria() {
        datos.errorAlEscribir = errorMySQL(1062);

        assertFallo("Ya existe otra categoria con esa descripcion.",
                logica.modificar(new CategoriaDTO("CAT-000001", "Sala nueva")));
        assertEquals(ROLLBACK, conexionFalsa.llamadas);
    }

    @Test
    void modificarUnaCategoriaInexistenteHaceRollback() {
        assertFallo("La categoria no existe.", logica.modificar(new CategoriaDTO("CAT-999999", "Lo que sea")));
        assertEquals(ROLLBACK, conexionFalsa.llamadas);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "", "  " })
    void modificarSinIdFallaSinAbrirConexion(String id) {
        assertFallo("La categoria no existe.", logica.modificar(new CategoriaDTO(id, "Sala")));
        assertEquals(0, conexionesAbiertas);
    }

    @Test
    void modificarNuloOConDescripcionVaciaFallaSinAbrirConexion() {
        assertFallo(CategoriaLogica.MENSAJE_NULA, logica.modificar(null));
        assertFallo("La descripcion no puede estar vacia.", logica.modificar(new CategoriaDTO("CAT-000001", " ")));
        assertEquals(0, conexionesAbiertas);
    }

    // --- eliminar ---

    @Test
    void eliminarSinRecursosBorraYHaceCommitDentroDelCandado() {
        assertTrue(logica.eliminar("CAT-000001").isExito());

        assertFalse(datos.categorias.containsKey("CAT-000001"));
        assertEquals(COMMIT, conexionFalsa.llamadas);
        // la revision de recursos usa la MISMA conexion, con el candado tomado
        assertSame(conexionFalsa.conexion, recursos.conexionRecibida);
        assertTrue(recursos.consultoConElCandadoTomado);
        assertTrue(datos.todoConElCandadoTomado);
    }

    @Test
    void eliminarConRecursosHaceRollbackYNoBorra() {
        recursos.categoriasConRecursos.add("CAT-000001");

        assertFallo("No se puede eliminar la categoria porque tiene recursos asociados"
                        + " (tambien cuentan los recursos dados de baja).",
                logica.eliminar("CAT-000001"));

        assertFalse(datos.seLlamoEliminar);
        assertTrue(datos.categorias.containsKey("CAT-000001"));
        assertEquals(ROLLBACK, conexionFalsa.llamadas);
    }

    @Test
    void eliminarTraduceElError1451AlMismoMensajeDeRecursos() {
        datos.errorAlEscribir = errorMySQL(1451);

        assertFallo(CategoriaLogica.MENSAJE_TIENE_RECURSOS, logica.eliminar("CAT-000001"));
        assertEquals(ROLLBACK, conexionFalsa.llamadas);
    }

    @Test
    void eliminarUnaCategoriaInexistenteNoConsultaRecursos() {
        assertFallo("La categoria no existe.", logica.eliminar("CAT-999999"));

        assertFalse(recursos.seConsulto);
        assertEquals(ROLLBACK, conexionFalsa.llamadas);
    }

    @Test
    void eliminarConErrorDeBaseHaceRollbackYDaElMensajeGenerico() {
        datos.errorAlEscribir = errorMySQL(1205);

        assertFallo(UsuarioDatos.MENSAJE_ERROR_BD, logica.eliminar("CAT-000001"));
        assertEquals(ROLLBACK, conexionFalsa.llamadas);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "", "  " })
    void eliminarSinIdFallaSinAbrirConexion(String id) {
        assertFallo("La categoria no existe.", logica.eliminar(id));
        assertEquals(0, conexionesAbiertas);
    }

    // --- buscar y listar ---

    @Test
    void buscarRecortaElTextoYConTextoVacioNoFiltra() {
        logica.buscar("  lap ");
        assertEquals("lap", datos.descripcionBuscada);

        logica.buscar("   ");
        assertNull(datos.descripcionBuscada);

        logica.buscar(null);
        assertNull(datos.descripcionBuscada);
    }

    @Test
    void listarConLaBaseCaidaDejaPasarElErrorGenerico() {
        datos.baseCaida = true;

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> logica.listarTodos());
        assertEquals(UsuarioDatos.MENSAJE_ERROR_BD, ex.getMessage());
        assertEquals(0, conexionesAbiertas);
    }
}
