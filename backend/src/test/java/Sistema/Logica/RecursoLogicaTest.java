package Sistema.Logica;

import Sistema.DTO.CategoriaDTO;
import Sistema.DTO.RecursoDTO;
import Sistema.Datos.RecursoDatos;
import Sistema.Datos.UsuarioDatos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
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
 * Pruebas de unidad de RecursoLogica: reglas de negocio y manejo de la
 * transaccion de la baja, SIN MySQL. Para eso se reemplazan sus tres
 * dependencias por dobles de prueba (se pasan por el constructor de pruebas):
 *   - RecursoDatosFake: los recursos viven en un mapa en memoria;
 *   - ReservaLogicaFake: responde si hay reservas futuras y anota si se le
 *     pregunto con el candado tomado;
 *   - ConexionFalsa: una java.sql.Connection hecha con Proxy que anota que
 *     metodos le llamaron (setAutoCommit, commit, rollback, close).
 * Las consultas SQL reales se prueban aparte en RecursoDatosIT.
 */
class RecursoLogicaTest {

    // --- dobles de prueba ---

    /**
     * RecursoDatos en memoria. Sobrescribe solo los metodos que usa
     * RecursoLogica; nunca abre conexiones. Si errorAlEscribir no es null,
     * insertar/actualizar/desactivar lo lanzan (simula errores de MySQL como
     * 1062 o 1452).
     */
    static class RecursoDatosFake extends RecursoDatos {
        final Map<String, RecursoDTO> recursos = new LinkedHashMap<>();
        final Set<String> inactivos = new HashSet<>();
        SQLException errorAlEscribir;

        // para generarNuevoId: cuantas consultas a existeId responden "ya existe"
        int idsUsadosAntesDeUnoLibre = 0;
        final List<String> idsConsultados = new ArrayList<>();

        // para las pruebas de filtrar
        String categoriaRecibida;
        String descripcionRecibida;

        // para las pruebas de escritura
        Connection conexionRecibida;
        boolean seLlamoDesactivar = false;

        void agregar(String id, String descripcion, boolean activo) {
            recursos.put(id, new RecursoDTO(id, descripcion, new CategoriaDTO("CAT-000001", "Salas")));
            if (!activo) inactivos.add(id);
        }

        boolean activo(String id) { return recursos.containsKey(id) && !inactivos.contains(id); }

        @Override
        public List<RecursoDTO> listarActivos() {
            return recursos.values().stream().filter(r -> activo(r.getId())).toList();
        }

        @Override
        public List<RecursoDTO> filtrarPorCategoria(String idCategoria) {
            categoriaRecibida = idCategoria;
            return listarActivos();
        }

        @Override
        public List<RecursoDTO> filtrar(String idCategoria, String descripcion) {
            categoriaRecibida = idCategoria;
            descripcionRecibida = descripcion;
            return listarActivos();
        }

        @Override
        public RecursoDTO buscarActivoPorId(String id) { return activo(id) ? recursos.get(id) : null; }

        @Override
        public RecursoDTO buscarActivoPorId(Connection conexion, String id) {
            conexionRecibida = conexion;
            return buscarActivoPorId(id);
        }

        @Override
        public boolean existeId(String id) {
            idsConsultados.add(id);
            return idsConsultados.size() <= idsUsadosAntesDeUnoLibre || recursos.containsKey(id);
        }

        @Override
        public void insertar(Connection conexion, RecursoDTO recurso) throws SQLException {
            conexionRecibida = conexion;
            if (errorAlEscribir != null) throw errorAlEscribir;
            recursos.put(recurso.getId(), recurso);
        }

        @Override
        public boolean actualizar(Connection conexion, RecursoDTO recurso) throws SQLException {
            conexionRecibida = conexion;
            if (errorAlEscribir != null) throw errorAlEscribir;
            if (!activo(recurso.getId())) return false;
            recursos.put(recurso.getId(), recurso);
            return true;
        }

        @Override
        public boolean desactivar(Connection conexion, String id) throws SQLException {
            conexionRecibida = conexion;
            seLlamoDesactivar = true;
            if (errorAlEscribir != null) throw errorAlEscribir;
            return activo(id) && inactivos.add(id);
        }
    }

    /**
     * ReservaLogica falsa: super(null, null) usa el constructor de pruebas de
     * Kristel, que no crea ReservaDatos ni archivos. Solo se sobrescribe la
     * version con conexion de tieneReservasFuturasActivas, que es la que usa
     * eliminarRecurso dentro de su transaccion.
     */
    static class ReservaLogicaFake extends ReservaLogica {
        boolean tieneReservas = false;
        boolean seConsulto = false;
        boolean consultoConElCandadoTomado = false;
        Connection conexionRecibida;

        ReservaLogicaFake() { super(null, null); }

        @Override
        public boolean tieneReservasFuturasActivas(Connection conexion, String idRecurso) {
            seConsulto = true;
            conexionRecibida = conexion;
            // Thread.holdsLock dice si ESTE hilo esta dentro de synchronized (candado)
            consultoConElCandadoTomado = Thread.holdsLock(ReservaLogica.CANDADO_RESERVAS);
            return tieneReservas;
        }
    }

    /**
     * Connection falsa hecha con java.lang.reflect.Proxy.
     *
     * Que es: java.sql.Connection es una interfaz con mas de 50 metodos.
     * Escribir una clase que los implemente todos seria enorme, y Mockito no
     * esta en el proyecto. Proxy.newProxyInstance crea, mientras corre el
     * programa, un objeto que SI implementa Connection, y manda CADA llamada
     * a un unico metodo: invoke(...) de esta clase (el InvocationHandler).
     *
     * Como funciona: si RecursoLogica llama conexion.commit(), Java ejecuta
     * invoke(proxy, <el Method "commit">, null). Aqui se anota el nombre en
     * la lista "llamadas" y se responde lo que haria una conexion real:
     * nada para commit/rollback/close, el valor guardado para
     * getAutoCommit, etc. Un metodo no previsto (por ejemplo prepareStatement)
     * lanza UnsupportedOperationException: asi una prueba falla si la Logica
     * intentara usar SQL directo en vez de pasar por RecursoDatos.
     *
     * Por que existe: permite comprobar sin MySQL que la baja apaga el
     * autocommit, hace commit si todo sale bien, rollback si algo falla, y
     * siempre cierra la conexion.
     */
    static class ConexionFalsa implements InvocationHandler {
        final List<String> llamadas = new ArrayList<>();
        private boolean autoCommit = true;
        private boolean cerrada = false;

        // el objeto que se le entrega a RecursoLogica a traves de FuenteConexion
        final Connection conexion = (Connection) Proxy.newProxyInstance(
                RecursoLogicaTest.class.getClassLoader(),
                new Class<?>[] { Connection.class },
                this);

        @Override
        public Object invoke(Object proxy, Method metodo, Object[] argumentos) {
            String nombre = metodo.getName();
            switch (nombre) {
                case "setAutoCommit" -> {
                    autoCommit = (Boolean) argumentos[0];
                    llamadas.add("setAutoCommit(" + autoCommit + ")");
                    return null;
                }
                case "getAutoCommit" -> { return autoCommit; }
                case "commit", "rollback" -> {
                    llamadas.add(nombre);
                    return null;
                }
                case "close" -> {
                    cerrada = true;
                    llamadas.add(nombre);
                    return null;
                }
                case "isClosed" -> { return cerrada; }
                // metodos de Object, por si una asercion imprime o compara la conexion
                case "toString" -> { return "ConexionFalsa" + llamadas; }
                case "hashCode" -> { return System.identityHashCode(proxy); }
                case "equals" -> { return proxy == argumentos[0]; }
                default -> throw new UnsupportedOperationException("La conexion falsa no implementa " + nombre);
            }
        }
    }

    // --- preparacion ---

    private RecursoDatosFake datos;
    private ReservaLogicaFake reservas;
    private ConexionFalsa conexionFalsa;
    private int conexionesAbiertas;
    private RecursoLogica logica;

    @BeforeEach
    void setUp() {
        datos = new RecursoDatosFake();
        reservas = new ReservaLogicaFake();
        conexionFalsa = new ConexionFalsa();
        conexionesAbiertas = 0;
        // la FuenteConexion es una lambda: cada "abrir" entrega la conexion falsa
        logica = new RecursoLogica(datos, reservas, () -> {
            conexionesAbiertas++;
            return conexionFalsa.conexion;
        });
    }

    private static RecursoDTO recurso(String id, String descripcion) {
        return new RecursoDTO(id, descripcion, new CategoriaDTO("CAT-000001", "Salas"));
    }

    private static SQLException errorMySQL(int codigo) {
        return new SQLException("error simulado", "23000", codigo);
    }

    // --- registrar ---

    @Test
    void registrarValidoInsertaDevuelveUnoYCierraLaConexion() {
        assertEquals(1, logica.registrarRecurso(recurso("123456", "Proyector")));

        assertEquals("Proyector", datos.recursos.get("123456").getDescripcion());
        assertSame(conexionFalsa.conexion, datos.conexionRecibida);
        assertEquals(1, conexionesAbiertas);
        assertEquals(List.of("close"), conexionFalsa.llamadas);
    }

    @Test
    void registrarGuardaLaDescripcionSinEspaciosAlInicioNiAlFinal() {
        RecursoDTO original = recurso("123456", "  Proyector  ");

        logica.registrarRecurso(original);

        assertEquals("Proyector", datos.recursos.get("123456").getDescripcion());
        assertEquals("  Proyector  ", original.getDescripcion());     // no modifica el DTO recibido
    }

    @Test
    void registrarNuloLanzaExcepcion() {
        assertThrows(IllegalArgumentException.class, () -> logica.registrarRecurso(null));
    }

    @Test
    void registrarSinCategoriaLanzaExcepcionSinAbrirConexion() {
        IllegalArgumentException sinCategoria = assertThrows(IllegalArgumentException.class,
                () -> logica.registrarRecurso(new RecursoDTO("123456", "Proyector", null)));
        assertEquals("Debe seleccionar una categoría", sinCategoria.getMessage());

        assertThrows(IllegalArgumentException.class,
                () -> logica.registrarRecurso(new RecursoDTO("123456", "Proyector", new CategoriaDTO(" ", "x"))));
        assertEquals(0, conexionesAbiertas);
    }

    @Test
    void registrarConDescripcionVaciaLanzaExcepcion() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> logica.registrarRecurso(recurso("123456", "   ")));
        assertEquals("La descripción no puede estar vacía", ex.getMessage());
    }

    @Test
    void registrarConDescripcionDeMasDe100CaracteresLanzaExcepcion() {
        assertEquals(1, logica.registrarRecurso(recurso("123456", "a".repeat(100))));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> logica.registrarRecurso(recurso("654321", "a".repeat(101))));
        assertEquals("La descripción no puede tener más de 100 caracteres", ex.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = { "12345", "1234567", "12a456", "REC-01", " 12345", "١٢٣٤٥٦" })
    void registrarConIdQueNoEsDeSeisDigitosLanzaExcepcion(String id) {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> logica.registrarRecurso(recurso(id, "Proyector")));
        assertEquals("El ID del recurso debe tener exactamente 6 dígitos", ex.getMessage());
        assertEquals(0, conexionesAbiertas);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "", "   " })
    void registrarSinIdLanzaExcepcion(String id) {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> logica.registrarRecurso(recurso(id, "Proyector")));
        assertEquals("El recurso no tiene un ID asignado", ex.getMessage());
    }

    @Test
    void registrarConIdRepetidoTraduceElError1062() {
        datos.errorAlEscribir = errorMySQL(1062);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> logica.registrarRecurso(recurso("123456", "Proyector")));
        assertEquals("Ya existe un recurso con el ID 123456 (puede estar dado de baja)", ex.getMessage());
        assertTrue(conexionFalsa.llamadas.contains("close"));
    }

    @Test
    void registrarConCategoriaInexistenteTraduceElError1452() {
        datos.errorAlEscribir = errorMySQL(1452);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> logica.registrarRecurso(recurso("123456", "Proyector")));
        assertEquals("La categoría seleccionada no existe", ex.getMessage());
    }

    @Test
    void registrarConOtroErrorDeBaseDaElMensajeGenericoYGuardaLaCausa() {
        SQLException original = errorMySQL(1205);
        datos.errorAlEscribir = original;

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> logica.registrarRecurso(recurso("123456", "Proyector")));
        assertEquals(UsuarioDatos.MENSAJE_ERROR_BD, ex.getMessage());
        assertSame(original, ex.getCause());
    }

    @Test
    void registrarSinPoderAbrirLaConexionDaElMensajeGenerico() {
        RecursoLogica sinBase = new RecursoLogica(datos, reservas, () -> {
            throw new SQLException("Communications link failure");
        });

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> sinBase.registrarRecurso(recurso("123456", "Proyector")));
        assertEquals(UsuarioDatos.MENSAJE_ERROR_BD, ex.getMessage());
    }

    // --- buscar, filtrar y listar ---

    @Test
    void buscarDevuelveElRecursoActivo() {
        datos.agregar("123456", "Proyector", true);

        assertEquals("Proyector", logica.buscarRecursoID("123456").getDescripcion());
    }

    @Test
    void buscarUnRecursoInactivoLoTrataComoNoEncontrado() {
        datos.agregar("123456", "Proyector", false);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> logica.buscarRecursoID("123456"));
        assertEquals("El recurso solicitado no fue encontrado", ex.getMessage());
    }

    @Test
    void filtrarRecortaLosTextosYNoPasaAMinusculas() {
        logica.filtrarRecursos("  CAT-000001 ", "  Sala Azul ");

        assertEquals("CAT-000001", datos.categoriaRecibida);
        assertEquals("Sala Azul", datos.descripcionRecibida);
    }

    @Test
    void filtrarConTextosVaciosNoAplicaEsosFiltros() {
        logica.filtrarRecursos("  ", "");

        assertNull(datos.categoriaRecibida);
        assertNull(datos.descripcionRecibida);
    }

    @Test
    void filtrarPorCategoriaDevuelveSoloLosActivos() {
        datos.agregar("111111", "Activo", true);
        datos.agregar("222222", "Inactivo", false);

        List<RecursoDTO> resultado = logica.filtrarRecursos("CAT-000001");

        assertEquals(List.of("111111"), resultado.stream().map(RecursoDTO::getId).toList());
        assertEquals("CAT-000001", datos.categoriaRecibida);
    }

    @Test
    void listarDevuelveSoloLosActivos() {
        datos.agregar("111111", "Activo", true);
        datos.agregar("222222", "Inactivo", false);

        assertEquals(List.of("111111"), logica.listarRecursos().stream().map(RecursoDTO::getId).toList());
    }

    // --- generar ID ---

    @Test
    void generarNuevoIdSigueBuscandoMientrasElIdEsteUsado() {
        datos.idsUsadosAntesDeUnoLibre = 3;

        String id = logica.generarNuevoId();

        assertEquals(4, datos.idsConsultados.size());
        assertEquals(datos.idsConsultados.get(3), id);
        assertTrue(id.matches("[0-9]{6}"));
    }

    // --- modificar ---

    @Test
    void modificarUnRecursoActivoLoCambiaYDevuelveTrue() {
        datos.agregar("123456", "Antes", true);

        assertTrue(logica.modificarRecurso(recurso("123456", " Despues ")));

        assertEquals("Despues", datos.recursos.get("123456").getDescripcion());
        assertTrue(conexionFalsa.llamadas.contains("close"));
    }

    @Test
    void modificarUnRecursoInactivoOInexistenteLanzaExcepcion() {
        datos.agregar("123456", "Antes", false);

        IllegalArgumentException inactivo = assertThrows(IllegalArgumentException.class,
                () -> logica.modificarRecurso(recurso("123456", "Despues")));
        assertEquals("El recurso que se intenta modificar no existe o fue dado de baja", inactivo.getMessage());

        assertThrows(IllegalArgumentException.class, () -> logica.modificarRecurso(recurso("999999", "Despues")));
    }

    @Test
    void modificarConCategoriaInexistenteTraduceElError1452() {
        datos.agregar("123456", "Antes", true);
        datos.errorAlEscribir = errorMySQL(1452);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> logica.modificarRecurso(recurso("123456", "Despues")));
        assertEquals("La categoría seleccionada no existe", ex.getMessage());
    }

    @Test
    void modificarNuloOConIdInvalidoLanzaExcepcion() {
        assertThrows(IllegalArgumentException.class, () -> logica.modificarRecurso(null));
        assertThrows(IllegalArgumentException.class, () -> logica.modificarRecurso(recurso("abc", "Despues")));
        assertEquals(0, conexionesAbiertas);
    }

    // --- eliminar (baja logica en transaccion, dentro del candado) ---

    @Test
    void eliminarSinReservasFuturasDesactivaYHaceCommitDentroDelCandado() {
        datos.agregar("123456", "Proyector", true);

        assertTrue(logica.eliminarRecurso("123456"));

        assertTrue(datos.inactivos.contains("123456"));
        assertEquals(List.of("setAutoCommit(false)", "commit", "close"), conexionFalsa.llamadas);
        assertTrue(reservas.consultoConElCandadoTomado);
        // la consulta de reservas usa la MISMA conexion de la transaccion
        assertSame(conexionFalsa.conexion, reservas.conexionRecibida);
        assertSame(conexionFalsa.conexion, datos.conexionRecibida);
        assertFalse(Thread.holdsLock(ReservaLogica.CANDADO_RESERVAS));     // al terminar lo suelta
    }

    @Test
    void eliminarConReservasFuturasHaceRollbackYNoDesactiva() {
        datos.agregar("123456", "Proyector", true);
        reservas.tieneReservas = true;

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> logica.eliminarRecurso("123456"));

        assertEquals("No se puede eliminar el recurso porque tiene reservas futuras activas", ex.getMessage());
        assertFalse(datos.seLlamoDesactivar);
        assertEquals(List.of("setAutoCommit(false)", "rollback", "close"), conexionFalsa.llamadas);
    }

    @Test
    void eliminarUnRecursoInexistenteOInactivoHaceRollbackSinConsultarReservas() {
        datos.agregar("123456", "Proyector", false);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> logica.eliminarRecurso("123456"));

        assertEquals("El recurso que se intenta eliminar no existe", ex.getMessage());
        assertFalse(reservas.seConsulto);
        assertEquals(List.of("setAutoCommit(false)", "rollback", "close"), conexionFalsa.llamadas);
    }

    @Test
    void eliminarConErrorDeBaseHaceRollbackYDaElMensajeGenerico() {
        datos.agregar("123456", "Proyector", true);
        datos.errorAlEscribir = errorMySQL(1205);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> logica.eliminarRecurso("123456"));

        assertEquals(UsuarioDatos.MENSAJE_ERROR_BD, ex.getMessage());
        assertEquals(List.of("setAutoCommit(false)", "rollback", "close"), conexionFalsa.llamadas);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "", "  " })
    void eliminarSinIdLanzaExcepcionSinAbrirConexion(String id) {
        assertThrows(IllegalArgumentException.class, () -> logica.eliminarRecurso(id));
        assertEquals(0, conexionesAbiertas);
    }
}
