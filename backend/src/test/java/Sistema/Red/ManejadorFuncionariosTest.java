package Sistema.Red;

import Sistema.DTO.Funcionario;
import Sistema.DTO.ResultadoOperacionDTO;
import Sistema.Service.IFuncionarioService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static Sistema.Red.ApoyoManejadores.*;
import static org.junit.jupiter.api.Assertions.*;

/** Pruebas de unidad de ManejadorFuncionarios (sin red ni MySQL). */
class ManejadorFuncionariosTest {

    /** Service falso: devuelve lo configurado, guarda lo recibido y cuenta las llamadas. */
    static class FuncionarioServiceFalso implements IFuncionarioService {
        ResultadoOperacionDTO resultado = ResultadoOperacionDTO.exito();
        List<Funcionario> lista = List.of(new Funcionario("44444", "Ana Perez", "8888-9999"));
        int llamadas;
        Funcionario funcionarioRecibido;
        String idRecibido;
        String idParcialRecibido;
        String nombreParcialRecibido;

        @Override
        public ResultadoOperacionDTO registrar(Funcionario funcionario) {
            llamadas++;
            funcionarioRecibido = funcionario;
            return resultado;
        }

        @Override
        public ResultadoOperacionDTO modificar(Funcionario funcionario) {
            llamadas++;
            funcionarioRecibido = funcionario;
            return resultado;
        }

        @Override
        public ResultadoOperacionDTO eliminar(String id) {
            llamadas++;
            idRecibido = id;
            return resultado;
        }

        @Override
        public List<Funcionario> buscar(String idParcial, String nombreParcial) {
            llamadas++;
            idParcialRecibido = idParcial;
            nombreParcialRecibido = nombreParcial;
            return lista;
        }

        @Override
        public List<Funcionario> listarTodos() {
            llamadas++;
            return lista;
        }
    }

    private FuncionarioServiceFalso servicio;
    private DespachadorPeticiones despachador;

    @BeforeEach
    void preparar() {
        servicio = new FuncionarioServiceFalso();
        despachador = new DespachadorPeticiones();
        new ManejadorFuncionarios(servicio).registrarEn(despachador);
    }

    private MensajeRed comoAdmin(String operacion, Object datos) {
        return enviar(despachador, operacion, datos, administrador());
    }

    // --- permisos ---

    @ParameterizedTest
    @ValueSource(strings = {Operaciones.FUNC_CREAR, Operaciones.FUNC_EDITAR, Operaciones.FUNC_BAJA,
            Operaciones.FUNC_BUSCAR, Operaciones.FUNC_LISTAR})
    void unFuncionarioNoPuedeUsarNingunaOperacionDeFuncionarios(String operacion) {
        // aunque mande datos validos, el permiso se revisa primero
        MensajeRed respuesta = enviar(despachador, operacion,
                Map.of("id", "1", "nombre", "x", "telefono", "1", "idFuncionario", "1"), funcionario());

        assertError(SIN_PERMISO, respuesta);
        assertEquals(0, servicio.llamadas);
    }

    @Test
    void unFuncionarioConDatosMalosTambienRecibeSinPermiso() {
        assertError(SIN_PERMISO, enviar(despachador, Operaciones.FUNC_CREAR, null, funcionario()));
    }

    @Test
    void sinSesionElDespachadorRechazaLaSolicitud() {
        assertError(SIN_SESION, enviar(despachador, Operaciones.FUNC_LISTAR, null, sinSesion()));
        assertEquals(0, servicio.llamadas);
    }

    // --- crear y editar ---

    @Test
    void crearPasaElFuncionarioAlServiceYRespondeExitoSinDatos() {
        MensajeRed respuesta = comoAdmin(Operaciones.FUNC_CREAR, new Funcionario("66666", "Carla Ruiz", "8555-1111"));

        assertExitoSinDatos(respuesta);
        assertEquals("66666", servicio.funcionarioRecibido.getId());
        assertEquals("Carla Ruiz", servicio.funcionarioRecibido.getNombre());
        assertEquals("8555-1111", servicio.funcionarioRecibido.getTelefono());
    }

    @Test
    void crearConFalloDeLaLogicaRespondeErrorConSuMensaje() {
        servicio.resultado = ResultadoOperacionDTO.fallo("Ya existe un funcionario con ese ID.");

        assertError("Ya existe un funcionario con ese ID.",
                comoAdmin(Operaciones.FUNC_CREAR, new Funcionario("44444", "Ana", "8888")));
    }

    @Test
    void editarPasaElFuncionarioAlService() {
        assertExitoSinDatos(comoAdmin(Operaciones.FUNC_EDITAR, new Funcionario("44444", "Ana Maria", "8000-0000")));
        assertEquals("Ana Maria", servicio.funcionarioRecibido.getNombre());
    }

    @Test
    void crearOEditarSinDatosNoEsValido() {
        assertError(DATOS_INVALIDOS, comoAdmin(Operaciones.FUNC_CREAR, null));
        assertError(DATOS_INVALIDOS, comoAdmin(Operaciones.FUNC_EDITAR, null));
        assertEquals(0, servicio.llamadas);
    }

    @Test
    void crearConUnaPropiedadDesconocidaNoEsValido() {
        assertError(DATOS_INVALIDOS, comoAdmin(Operaciones.FUNC_CREAR,
                Map.of("id", "66666", "nombre", "Carla", "telefono", "8555", "clave", "x")));
        assertEquals(0, servicio.llamadas);
    }

    // --- baja ---

    @Test
    void bajaPasaElIdAlService() {
        assertExitoSinDatos(comoAdmin(Operaciones.FUNC_BAJA, Map.of("idFuncionario", "44444")));
        assertEquals("44444", servicio.idRecibido);
    }

    @Test
    void bajaRechazadaPorLaLogicaRespondeErrorConSuMensaje() {
        servicio.resultado = ResultadoOperacionDTO.fallo(
                "No se puede eliminar el funcionario porque tiene reservas futuras activas.");

        assertError("No se puede eliminar el funcionario porque tiene reservas futuras activas.",
                comoAdmin(Operaciones.FUNC_BAJA, Map.of("idFuncionario", "44444")));
    }

    @Test
    void bajaSinIdNoEsValida() {
        assertError(DATOS_INVALIDOS, comoAdmin(Operaciones.FUNC_BAJA, null));
        assertError(DATOS_INVALIDOS, comoAdmin(Operaciones.FUNC_BAJA, Map.of("id", "44444")));
        assertEquals(0, servicio.llamadas);
    }

    // --- buscar y listar ---

    @Test
    void buscarPasaLosDosFiltros() {
        assertExito(comoAdmin(Operaciones.FUNC_BUSCAR, Map.of("idParcial", "44", "nombreParcial", "ana")));
        assertEquals("44", servicio.idParcialRecibido);
        assertEquals("ana", servicio.nombreParcialRecibido);
    }

    @Test
    void buscarSinFiltrosLosPasaComoNull() {
        assertExito(comoAdmin(Operaciones.FUNC_BUSCAR, null));
        assertNull(servicio.idParcialRecibido);
        assertNull(servicio.nombreParcialRecibido);

        assertExito(comoAdmin(Operaciones.FUNC_BUSCAR, Map.of("nombreParcial", "ana")));
        assertNull(servicio.idParcialRecibido);
        assertEquals("ana", servicio.nombreParcialRecibido);
    }

    @Test
    void listarDevuelveUnArregloDeFuncionariosSinReservas() {
        MensajeRed respuesta = comoAdmin(Operaciones.FUNC_LISTAR, null);

        assertExito(respuesta);
        JsonNode lista = respuesta.getDatos();
        assertTrue(lista.isArray());
        assertEquals(1, lista.size());
        assertEquals("44444", lista.get(0).get("id").asText());
        assertEquals("Ana Perez", lista.get(0).get("nombre").asText());
        assertEquals("8888-9999", lista.get(0).get("telefono").asText());
        assertFalse(lista.get(0).has("reservas"));   // @JsonIgnore
    }
}
