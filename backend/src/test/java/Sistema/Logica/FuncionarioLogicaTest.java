package Sistema.Logica;

import Sistema.DTO.Funcionario;
import Sistema.DTO.ResultadoOperacionDTO;
import Sistema.DTO.Rol;
import Sistema.Datos.FuenteConexion;
import Sistema.Datos.FuncionarioDatos;
import Sistema.Datos.UsuarioDatos;
import Sistema.Datos.UsuarioRegistro;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas de unidad de FuncionarioLogica, sin MySQL: Datos y ReservaLogica
 * falsos en memoria y la ConexionFalsa (revisa setAutoCommit, commit,
 * rollback y close). El SQL real se prueba en FuncionarioDatosIT y UsuarioDatosIT.
 *
 * Reemplazan a las 4 pruebas @Disabled de eliminar que tenia
 * FuncionarioServiceTest (ver "eliminar" mas abajo).
 */
class FuncionarioLogicaTest {

    private static final String ERROR_BD = UsuarioDatos.MENSAJE_ERROR_BD;

    // --- dobles de prueba ---

    /** Funcionarios en memoria; "pasos" es compartido para ver el orden de las escrituras. */
    static class FuncionarioDatosFake extends FuncionarioDatos {
        final Map<String, Funcionario> funcionarios = new LinkedHashMap<>();
        final List<String> inactivos = new ArrayList<>();
        final List<String> pasos;
        SQLException errorAlInsertar;
        SQLException errorAlActualizar;
        RuntimeException errorAlListar;
        Funcionario insertado;
        Funcionario actualizado;
        String idBuscado;
        String nombreBuscado;

        FuncionarioDatosFake(List<String> pasos) {
            this.pasos = pasos;
        }

        void agregar(String id, String nombre, String telefono, boolean activo) {
            funcionarios.put(id, new Funcionario(id, nombre, telefono));
            if (!activo) inactivos.add(id);
        }

        boolean activo(String id) {
            return funcionarios.containsKey(id) && !inactivos.contains(id);
        }

        @Override
        public Funcionario buscarActivoPorId(Connection conexion, String id) {
            return activo(id) ? funcionarios.get(id) : null;
        }

        @Override
        public void insertar(Connection conexion, Funcionario funcionario) throws SQLException {
            pasos.add("insertar funcionario");
            if (errorAlInsertar != null) throw errorAlInsertar;
            insertado = funcionario;
            funcionarios.put(funcionario.getId(), funcionario);
        }

        @Override
        public boolean actualizar(Connection conexion, Funcionario funcionario) throws SQLException {
            if (errorAlActualizar != null) throw errorAlActualizar;
            if (!activo(funcionario.getId())) return false;
            actualizado = funcionario;
            funcionarios.put(funcionario.getId(), funcionario);
            return true;
        }

        @Override
        public boolean desactivar(Connection conexion, String id) {
            pasos.add("desactivar funcionario");
            if (!activo(id)) return false;
            inactivos.add(id);
            return true;
        }

        @Override
        public List<Funcionario> buscar(String idParcial, String nombreParcial) {
            idBuscado = idParcial;
            nombreBuscado = nombreParcial;
            return listarActivos();
        }

        @Override
        public List<Funcionario> listarActivos() {
            if (errorAlListar != null) throw errorAlListar;
            return funcionarios.values().stream().filter(f -> activo(f.getId())).toList();
        }
    }

    /** Usuarios en memoria; busca sin distinguir mayusculas, como la collation. */
    static class UsuarioDatosFake extends UsuarioDatos {
        final Map<String, UsuarioRegistro> usuarios = new LinkedHashMap<>();
        final List<String> pasos;
        SQLException errorAlInsertar;
        SQLException errorAlDesactivar;
        String idInsertado;
        String hashInsertado;
        Rol rolInsertado;

        UsuarioDatosFake(List<String> pasos) {
            this.pasos = pasos;
        }

        void agregar(String id, Rol rol, boolean activo) {
            usuarios.put(id, new UsuarioRegistro(id, "hash", rol, activo));
        }

        @Override
        public UsuarioRegistro buscarPorId(Connection conexion, String id) {
            return usuarios.values().stream().filter(u -> u.id().equalsIgnoreCase(id)).findFirst().orElse(null);
        }

        @Override
        public void insertar(Connection conexion, String id, String claveHash, Rol rol) throws SQLException {
            pasos.add("insertar usuario");
            if (errorAlInsertar != null) throw errorAlInsertar;
            idInsertado = id;
            hashInsertado = claveHash;
            rolInsertado = rol;
            usuarios.put(id, new UsuarioRegistro(id, claveHash, rol, true));
        }

        @Override
        public boolean desactivarFuncionario(Connection conexion, String id) throws SQLException {
            pasos.add("desactivar usuario");
            if (errorAlDesactivar != null) throw errorAlDesactivar;
            UsuarioRegistro usuario = usuarios.get(id);
            if (usuario == null || usuario.rol() != Rol.FUNCIONARIO) return false;
            usuarios.put(id, new UsuarioRegistro(id, usuario.claveHash(), usuario.rol(), false));
            return true;
        }
    }

    /** Responde si hay reservas futuras y anota con que conexion y si tenia el candado. */
    static class ReservaLogicaFake extends ReservaLogica {
        boolean tieneReservas;
        SQLException error;
        Connection conexionRecibida;
        Boolean candadoTomado;

        ReservaLogicaFake() {
            super(null, null, null);
        }

        @Override
        public boolean tieneReservasFuturas(Connection conexion, String idFuncionario) throws SQLException {
            conexionRecibida = conexion;
            candadoTomado = Thread.holdsLock(ReservaLogica.CANDADO_RESERVAS);
            if (error != null) throw error;
            return tieneReservas;
        }
    }

    // --- preparacion ---

    private final List<String> pasos = new ArrayList<>();
    private FuncionarioDatosFake funcionarioDatos;
    private UsuarioDatosFake usuarioDatos;
    private ReservaLogicaFake reservaLogica;
    private ConexionFalsa conexionFalsa;
    private int aperturas;
    private Boolean candadoAlAbrir;
    private boolean fallarAlAbrir;
    private FuncionarioLogica logica;

    @BeforeEach
    void preparar() {
        funcionarioDatos = new FuncionarioDatosFake(pasos);
        usuarioDatos = new UsuarioDatosFake(pasos);
        reservaLogica = new ReservaLogicaFake();
        conexionFalsa = new ConexionFalsa();

        usuarioDatos.agregar("admin", Rol.ADMINISTRADOR, true);
        usuarioDatos.agregar("44444", Rol.FUNCIONARIO, true);
        funcionarioDatos.agregar("44444", "Ana Perez", "8888-9999", true);
        usuarioDatos.agregar("55555", Rol.FUNCIONARIO, false);
        funcionarioDatos.agregar("55555", "Beto Soto", "8777-6666", false);

        FuenteConexion fuente = () -> {
            aperturas++;
            candadoAlAbrir = Thread.holdsLock(ReservaLogica.CANDADO_RESERVAS);
            if (fallarAlAbrir) throw new SQLException("sin conexion");
            return conexionFalsa.conexion;
        };
        logica = new FuncionarioLogica(funcionarioDatos, usuarioDatos, reservaLogica, fuente);
    }

    private static SQLException errorSql(int codigo) {
        return new SQLException("error de prueba", "HY000", codigo);
    }

    private static void assertFallo(String mensaje, ResultadoOperacionDTO resultado) {
        assertFalse(resultado.isExito());
        assertEquals(mensaje, resultado.getMensaje());
    }

    // ================= registrar =================

    @Test
    void registrarNuloNoAbreConexion() {
        assertFallo(FuncionarioLogica.MENSAJE_NULO, logica.registrar(null));
        assertEquals(0, aperturas);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void registrarConIdVacioNoAbreConexion(String id) {
        assertFallo("El ID no puede estar vacio.", logica.registrar(new Funcionario(id, "Carla", "8555-1111")));
        assertEquals(0, aperturas);
    }

    @Test
    void registrarConIdDeMasDe20Caracteres() {
        assertFallo(FuncionarioLogica.MENSAJE_ID_LARGO,
                logica.registrar(new Funcionario("1".repeat(21), "Carla", "8555-1111")));
        assertEquals(0, aperturas);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void registrarConNombreVacio(String nombre) {
        assertFallo("El nombre no puede estar vacio.", logica.registrar(new Funcionario("66666", nombre, "8555-1111")));
        assertEquals(0, aperturas);
    }

    @Test
    void registrarConNombreDeMasDe100Caracteres() {
        assertFallo(FuncionarioLogica.MENSAJE_NOMBRE_LARGO,
                logica.registrar(new Funcionario("66666", "a".repeat(101), "8555-1111")));
        assertEquals(0, aperturas);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void registrarConTelefonoVacio(String telefono) {
        assertFallo("El telefono no puede estar vacio.", logica.registrar(new Funcionario("66666", "Carla", telefono)));
        assertEquals(0, aperturas);
    }

    @Test
    void registrarConTelefonoDeMasDe20Caracteres() {
        assertFallo(FuncionarioLogica.MENSAJE_TELEFONO_LARGO,
                logica.registrar(new Funcionario("66666", "Carla", "8".repeat(21))));
        assertEquals(0, aperturas);
    }

    @Test
    void registrarConElIdDelAdministradorSeRechazaSinInsertar() {
        assertFallo("Ya existe un usuario con ese ID (por ejemplo, el administrador).",
                logica.registrar(new Funcionario("admin", "Carla", "8555-1111")));
        assertTrue(pasos.isEmpty());
        assertEquals(List.of("setAutoCommit(false)", "rollback", "close"), conexionFalsa.llamadas);
    }

    @Test
    void registrarConElIdDeUnFuncionarioActivoSeRechaza() {
        assertFallo("Ya existe un funcionario con ese ID.",
                logica.registrar(new Funcionario("44444", "Carla", "8555-1111")));
        assertTrue(pasos.isEmpty());
    }

    @Test
    void registrarConElIdDeUnFuncionarioDadoDeBajaSeRechazaConMensajeClaro() {
        // decision 3 del equipo
        assertFallo("Ya existe un funcionario con ese ID, dado de baja. No se puede reutilizar el ID.",
                logica.registrar(new Funcionario("55555", "Carla", "8555-1111")));
        assertTrue(pasos.isEmpty());
        assertTrue(conexionFalsa.llamadas.contains("rollback"));
    }

    @Test
    void registrarConUnIdQueSoloCambiaMayusculasSeRechaza() {
        assertFallo(FuncionarioLogica.MENSAJE_ID_USUARIO,
                logica.registrar(new Funcionario("ADMIN", "Carla", "8555-1111")));
    }

    @Test
    void registrarCreaUsuarioYFuncionarioEnUnaTransaccion() {
        ResultadoOperacionDTO resultado = logica.registrar(new Funcionario("66666", "Carla Ruiz", "8555-1111"));

        assertTrue(resultado.isExito());
        assertNull(resultado.getMensaje());
        // primero el usuario (la FK de funcionario lo exige) y despues el funcionario
        assertEquals(List.of("insertar usuario", "insertar funcionario"), pasos);
        assertEquals("66666", usuarioDatos.idInsertado);
        assertEquals(Rol.FUNCIONARIO, usuarioDatos.rolInsertado);
        // clave inicial = ID, guardada como hash
        assertNotEquals("66666", usuarioDatos.hashInsertado);
        assertTrue(HashClave.verificar("66666", usuarioDatos.hashInsertado));
        assertEquals(List.of("setAutoCommit(false)", "commit", "close"), conexionFalsa.llamadas);
    }

    @Test
    void registrarNoUsaElCandadoDeReservas() {
        // decision D5: basta la PK de usuario (error 1062)
        assertTrue(logica.registrar(new Funcionario("66666", "Carla Ruiz", "8555-1111")).isExito());
        assertFalse(candadoAlAbrir);
    }

    @Test
    void registrarRecortaLosTresCamposSinModificarElDtoRecibido() {
        Funcionario recibido = new Funcionario("  66666 ", "  Carla Ruiz ", " 8555-1111  ");

        assertTrue(logica.registrar(recibido).isExito());

        assertEquals("66666", usuarioDatos.idInsertado);
        assertEquals("66666", funcionarioDatos.insertado.getId());
        assertEquals("Carla Ruiz", funcionarioDatos.insertado.getNombre());
        assertEquals("8555-1111", funcionarioDatos.insertado.getTelefono());
        assertEquals("  66666 ", recibido.getId());
        assertEquals("  Carla Ruiz ", recibido.getNombre());
    }

    @Test
    void siFallaElSegundoInsertSeDeshaceTodo() {
        funcionarioDatos.errorAlInsertar = errorSql(1048);

        assertFallo(ERROR_BD, logica.registrar(new Funcionario("66666", "Carla Ruiz", "8555-1111")));

        assertEquals(List.of("insertar usuario", "insertar funcionario"), pasos);
        assertEquals(List.of("setAutoCommit(false)", "rollback", "close"), conexionFalsa.llamadas);
    }

    @Test
    void unIdRepetidoQueLlegaHastaLaPkDaElMensajeDeIdRepetido() {
        // otro administrador lo registro entre la revision y el INSERT
        usuarioDatos.errorAlInsertar = errorSql(1062);

        assertFallo("Ya existe un usuario o funcionario con ese ID (puede estar dado de baja).",
                logica.registrar(new Funcionario("66666", "Carla Ruiz", "8555-1111")));
        assertEquals(List.of("setAutoCommit(false)", "rollback", "close"), conexionFalsa.llamadas);
    }

    @Test
    void registrarSinConexionDaElMensajeDeBaseDeDatos() {
        fallarAlAbrir = true;

        assertFallo(ERROR_BD, logica.registrar(new Funcionario("66666", "Carla Ruiz", "8555-1111")));
        assertTrue(pasos.isEmpty());
    }

    // ================= modificar =================

    @Test
    void modificarNulo() {
        assertFallo(FuncionarioLogica.MENSAJE_NULO, logica.modificar(null));
        assertEquals(0, aperturas);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void modificarSinIdDaNoExiste(String id) {
        assertFallo(FuncionarioLogica.MENSAJE_NO_EXISTE, logica.modificar(new Funcionario(id, "Ana", "8888-9999")));
        assertEquals(0, aperturas);
    }

    @Test
    void modificarConNombreVacio() {
        assertFallo(FuncionarioLogica.MENSAJE_NOMBRE_VACIO, logica.modificar(new Funcionario("44444", " ", "8888-9999")));
        assertEquals(0, aperturas);
    }

    @Test
    void modificarConTelefonoDeMasDe20Caracteres() {
        assertFallo(FuncionarioLogica.MENSAJE_TELEFONO_LARGO,
                logica.modificar(new Funcionario("44444", "Ana", "8".repeat(21))));
        assertEquals(0, aperturas);
    }

    @Test
    void modificarCambiaNombreYTelefonoRecortados() {
        ResultadoOperacionDTO resultado = logica.modificar(new Funcionario("44444", " Ana Maria ", " 8000-0000 "));

        assertTrue(resultado.isExito());
        assertEquals("44444", funcionarioDatos.actualizado.getId());
        assertEquals("Ana Maria", funcionarioDatos.actualizado.getNombre());
        assertEquals("8000-0000", funcionarioDatos.actualizado.getTelefono());
        // una sola sentencia: sin transaccion
        assertEquals(List.of("close"), conexionFalsa.llamadas);
    }

    @Test
    void modificarUnFuncionarioDadoDeBajaSeRechaza() {
        assertFallo(FuncionarioLogica.MENSAJE_NO_EXISTE, logica.modificar(new Funcionario("55555", "Beto", "8777-6666")));
    }

    @Test
    void modificarUnFuncionarioInexistenteSeRechaza() {
        assertFallo(FuncionarioLogica.MENSAJE_NO_EXISTE, logica.modificar(new Funcionario("00000", "Nadie", "8000-0000")));
    }

    @Test
    void modificarConErrorDeBaseDeDatos() {
        funcionarioDatos.errorAlActualizar = errorSql(2013);

        assertFallo(ERROR_BD, logica.modificar(new Funcionario("44444", "Ana", "8888-9999")));
        assertEquals(List.of("close"), conexionFalsa.llamadas);
    }

    // ================= eliminar (baja logica) =================
    // El criterio "futura y activa" (una reserva cancelada o ya pasada no
    // cuenta) no se prueba aqui: lo prueba ReservaDatosIT de Kristel. Aqui
    // solo importa que hace la Logica con la respuesta de tieneReservasFuturas.

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void eliminarSinIdNoAbreConexion(String id) {
        assertFallo(FuncionarioLogica.MENSAJE_NO_EXISTE, logica.eliminar(id));
        assertEquals(0, aperturas);
    }

    @Test
    void eliminarUnFuncionarioInexistente() {
        assertFallo("El funcionario no existe o fue dado de baja.", logica.eliminar("00000"));
        assertEquals(List.of("setAutoCommit(false)", "rollback", "close"), conexionFalsa.llamadas);
    }

    @Test
    void eliminarUnFuncionarioYaDadoDeBaja() {
        assertFallo(FuncionarioLogica.MENSAJE_NO_EXISTE, logica.eliminar("55555"));
        assertTrue(pasos.isEmpty());
    }

    @Test
    void eliminarConReservasFuturasActivasSeRechazaYNoDesactivaNada() {
        // reemplaza a eliminarFallaSiTieneReservasFuturasActivas (@Disabled)
        reservaLogica.tieneReservas = true;

        assertFallo("No se puede eliminar el funcionario porque tiene reservas futuras activas.",
                logica.eliminar("44444"));

        assertTrue(pasos.isEmpty());
        assertTrue(funcionarioDatos.activo("44444"));
        assertTrue(usuarioDatos.usuarios.get("44444").activo());
        assertEquals(List.of("setAutoCommit(false)", "rollback", "close"), conexionFalsa.llamadas);
    }

    @Test
    void eliminarSinReservasFuturasDesactivaFuncionarioYUsuario() {
        // reemplaza a eliminarFuncionaSinReservasFuturasYBorraElUsuarioAsociado y, en la
        // Logica, a los casos de "solo cancelada" y "solo pasada" (tieneReservasFuturas = false)
        ResultadoOperacionDTO resultado = logica.eliminar("44444");

        assertTrue(resultado.isExito());
        assertEquals(List.of("desactivar funcionario", "desactivar usuario"), pasos);
        assertFalse(funcionarioDatos.activo("44444"));
        assertFalse(usuarioDatos.usuarios.get("44444").activo());   // ya no puede iniciar sesion
        assertTrue(logica.listarTodos().stream().noneMatch(f -> f.getId().equals("44444")));
        assertEquals(List.of("setAutoCommit(false)", "commit", "close"), conexionFalsa.llamadas);
    }

    @Test
    void eliminarConsultaLasReservasConElCandadoTomadoYLaMismaConexion() {
        assertTrue(logica.eliminar("44444").isExito());

        assertTrue(candadoAlAbrir, "la conexion se abre despues de tomar el candado");
        assertTrue(reservaLogica.candadoTomado, "tieneReservasFuturas corre dentro del candado");
        assertSame(conexionFalsa.conexion, reservaLogica.conexionRecibida);
        assertFalse(Thread.holdsLock(ReservaLogica.CANDADO_RESERVAS));
    }

    @Test
    void siLaConsultaDeReservasFallaSeDeshaceYNoSeDesactiva() {
        reservaLogica.error = errorSql(2013);

        assertFallo(ERROR_BD, logica.eliminar("44444"));
        assertTrue(pasos.isEmpty());
        assertEquals(List.of("setAutoCommit(false)", "rollback", "close"), conexionFalsa.llamadas);
    }

    @Test
    void siFallaLaBajaDelUsuarioSeDeshaceTambienLaDelFuncionario() {
        usuarioDatos.errorAlDesactivar = errorSql(2013);

        assertFallo(ERROR_BD, logica.eliminar("44444"));
        assertEquals(List.of("desactivar funcionario", "desactivar usuario"), pasos);
        assertEquals(List.of("setAutoCommit(false)", "rollback", "close"), conexionFalsa.llamadas);
    }

    @Test
    void siNoSeEncuentraElUsuarioSeDeshaceLaBaja() {
        usuarioDatos.usuarios.remove("44444");   // dato inconsistente: funcionario sin usuario

        assertFallo(FuncionarioLogica.MENSAJE_NO_EXISTE, logica.eliminar("44444"));
        assertEquals(List.of("setAutoCommit(false)", "rollback", "close"), conexionFalsa.llamadas);
    }

    @Test
    void eliminarSinConexionDaElMensajeDeBaseDeDatos() {
        fallarAlAbrir = true;

        assertFallo(ERROR_BD, logica.eliminar("44444"));
        assertTrue(candadoAlAbrir);
    }

    // ================= buscar y listar =================

    @Test
    void buscarConvierteLosBlancosEnSinFiltroYRecorta() {
        logica.buscar("   ", " ana ");
        assertNull(funcionarioDatos.idBuscado);
        assertEquals("ana", funcionarioDatos.nombreBuscado);

        logica.buscar(" 44 ", null);
        assertEquals("44", funcionarioDatos.idBuscado);
        assertNull(funcionarioDatos.nombreBuscado);
    }

    @Test
    void listarTodosDevuelveSoloLosActivos() {
        List<Funcionario> lista = logica.listarTodos();

        assertEquals(1, lista.size());
        assertEquals("44444", lista.get(0).getId());
    }

    @Test
    void unErrorDeLaBaseAlListarSePropaga() {
        funcionarioDatos.errorAlListar = new IllegalStateException(ERROR_BD);

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> logica.listarTodos());
        assertEquals(ERROR_BD, error.getMessage());
    }
}
