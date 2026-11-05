package Sistema.Red;

import Sistema.DTO.CategoriaDTO;
import Sistema.DTO.RecursoDTO;
import Sistema.DTO.ResultadoOperacionDTO;
import Sistema.Service.IRecursoService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static Sistema.Red.ApoyoManejadores.*;
import static org.junit.jupiter.api.Assertions.*;

/** Pruebas de unidad de ManejadorRecursos (sin red ni MySQL). */
class ManejadorRecursosTest {

    private static RecursoDTO proyector() {
        return new RecursoDTO("123456", "Proyector Epson", new CategoriaDTO("CAT-000001", "Salas"));
    }

    /** Service falso: devuelve lo configurado y anota que metodo se llamo y con que. */
    static class RecursoServiceFalso implements IRecursoService {
        ResultadoOperacionDTO resultado = ResultadoOperacionDTO.exito();
        List<RecursoDTO> lista = List.of(proyector());
        RuntimeException errorAlBuscar;
        int llamadas;
        String metodoLlamado;
        RecursoDTO recursoRecibido;
        String idRecibido;
        String categoriaRecibida;
        String descripcionRecibida;

        @Override
        public ResultadoOperacionDTO registrarRecurso(RecursoDTO recurso) {
            anotar("registrarRecurso");
            recursoRecibido = recurso;
            return resultado;
        }

        @Override
        public ResultadoOperacionDTO modificarRecurso(RecursoDTO recurso) {
            anotar("modificarRecurso");
            recursoRecibido = recurso;
            return resultado;
        }

        @Override
        public ResultadoOperacionDTO eliminarRecurso(String id) {
            anotar("eliminarRecurso");
            idRecibido = id;
            return resultado;
        }

        @Override
        public RecursoDTO buscarRecursoID(String id) {
            anotar("buscarRecursoID");
            idRecibido = id;
            if (errorAlBuscar != null) throw errorAlBuscar;
            return proyector();
        }

        @Override
        public List<RecursoDTO> filtrarRecursos(String idCategoria) {
            anotar("filtrarRecursos(1)");
            categoriaRecibida = idCategoria;
            return lista;
        }

        @Override
        public List<RecursoDTO> filtrarRecursos(String idCategoria, String descripcion) {
            anotar("filtrarRecursos(2)");
            categoriaRecibida = idCategoria;
            descripcionRecibida = descripcion;
            return lista;
        }

        @Override
        public List<RecursoDTO> listarRecursos() {
            anotar("listarRecursos");
            return lista;
        }

        @Override
        public String generarNuevoId() {
            anotar("generarNuevoId");
            return "482913";
        }

        private void anotar(String metodo) {
            llamadas++;
            metodoLlamado = metodo;
        }
    }

    private RecursoServiceFalso servicio;
    private DespachadorPeticiones despachador;

    @BeforeEach
    void preparar() {
        servicio = new RecursoServiceFalso();
        despachador = new DespachadorPeticiones();
        new ManejadorRecursos(servicio).registrarEn(despachador);
    }

    private MensajeRed comoAdmin(String operacion, Object datos) {
        return enviar(despachador, operacion, datos, administrador());
    }

    // --- permisos ---

    @ParameterizedTest
    @ValueSource(strings = {Operaciones.REC_CREAR, Operaciones.REC_EDITAR, Operaciones.REC_ELIMINAR,
            Operaciones.REC_BUSCAR, Operaciones.REC_NUEVO_ID})
    void unFuncionarioNoPuedeUsarLasOperacionesDeAdministrador(String operacion) {
        MensajeRed respuesta = enviar(despachador, operacion, Map.of("idRecurso", "123456"), funcionario());

        assertError(SIN_PERMISO, respuesta);
        assertEquals(0, servicio.llamadas);
    }

    @Test
    void unFuncionarioSiPuedeFiltrarRecursosPorCategoria() {
        // la usa Calendarizacion
        MensajeRed respuesta = enviar(despachador, Operaciones.REC_LISTAR,
                Map.of("idCategoria", "CAT-000001"), funcionario());

        assertExito(respuesta);
        assertEquals("filtrarRecursos(2)", servicio.metodoLlamado);
    }

    @Test
    void sinSesionNiSiquieraSePuedeListar() {
        assertError(SIN_SESION, enviar(despachador, Operaciones.REC_LISTAR, null, sinSesion()));
        assertEquals(0, servicio.llamadas);
    }

    // --- crear, editar y eliminar ---

    @Test
    void crearPasaElRecursoConSuCategoriaYRespondeExitoSinDatos() {
        MensajeRed respuesta = comoAdmin(Operaciones.REC_CREAR, proyector());

        assertExitoSinDatos(respuesta);
        assertEquals("registrarRecurso", servicio.metodoLlamado);
        assertEquals("123456", servicio.recursoRecibido.getId());
        assertEquals("Proyector Epson", servicio.recursoRecibido.getDescripcion());
        assertEquals("CAT-000001", servicio.recursoRecibido.getCategoria().getId());
    }

    @Test
    void crearConFalloDeLaLogicaRespondeErrorConSuMensaje() {
        servicio.resultado = ResultadoOperacionDTO.fallo("Ya existe un recurso con el ID 123456 (puede estar dado de baja)");

        assertError("Ya existe un recurso con el ID 123456 (puede estar dado de baja)",
                comoAdmin(Operaciones.REC_CREAR, proyector()));
    }

    @Test
    void editarPasaElRecursoAlService() {
        assertExitoSinDatos(comoAdmin(Operaciones.REC_EDITAR, proyector()));
        assertEquals("modificarRecurso", servicio.metodoLlamado);
    }

    @Test
    void crearOEditarSinDatosOConPropiedadDesconocidaNoEsValido() {
        assertError(DATOS_INVALIDOS, comoAdmin(Operaciones.REC_CREAR, null));
        assertError(DATOS_INVALIDOS, comoAdmin(Operaciones.REC_EDITAR, null));
        assertError(DATOS_INVALIDOS, comoAdmin(Operaciones.REC_CREAR,
                Map.of("id", "123456", "descripcion", "x", "activo", true)));
        assertEquals(0, servicio.llamadas);
    }

    @Test
    void eliminarPasaElIdYRespondeElFalloDeLaLogica() {
        assertExitoSinDatos(comoAdmin(Operaciones.REC_ELIMINAR, Map.of("idRecurso", "123456")));
        assertEquals("123456", servicio.idRecibido);

        servicio.resultado = ResultadoOperacionDTO.fallo("No se puede eliminar el recurso porque tiene reservas futuras activas");
        assertError("No se puede eliminar el recurso porque tiene reservas futuras activas",
                comoAdmin(Operaciones.REC_ELIMINAR, Map.of("idRecurso", "123456")));
    }

    @Test
    void eliminarOBuscarSinIdNoEsValido() {
        assertError(DATOS_INVALIDOS, comoAdmin(Operaciones.REC_ELIMINAR, null));
        assertError(DATOS_INVALIDOS, comoAdmin(Operaciones.REC_BUSCAR, Map.of("id", "123456")));
        assertEquals(0, servicio.llamadas);
    }

    // --- buscar por ID ---

    @Test
    void buscarDevuelveElRecursoConSuCategoria() {
        MensajeRed respuesta = comoAdmin(Operaciones.REC_BUSCAR, Map.of("idRecurso", "123456"));

        assertExito(respuesta);
        assertEquals("123456", servicio.idRecibido);
        JsonNode recurso = respuesta.getDatos();
        assertEquals("123456", recurso.get("id").asText());
        assertEquals("Proyector Epson", recurso.get("descripcion").asText());
        assertEquals("CAT-000001", recurso.get("categoria").get("id").asText());
        assertEquals("Salas", recurso.get("categoria").get("descripcion").asText());
    }

    @Test
    void buscarUnRecursoQueNoExisteRespondeErrorConElMensajeDeLaLogica() {
        servicio.errorAlBuscar = new IllegalArgumentException("El recurso solicitado no fue encontrado");

        assertError("El recurso solicitado no fue encontrado",
                comoAdmin(Operaciones.REC_BUSCAR, Map.of("idRecurso", "999999")));
    }

    // --- listar con filtros opcionales ---

    @Test
    void listarSinFiltrosLlamaAListarRecursos() {
        MensajeRed respuesta = comoAdmin(Operaciones.REC_LISTAR, null);

        assertExito(respuesta);
        assertEquals("listarRecursos", servicio.metodoLlamado);
        assertTrue(respuesta.getDatos().isArray());
        assertEquals("CAT-000001", respuesta.getDatos().get(0).get("categoria").get("id").asText());

        assertExito(comoAdmin(Operaciones.REC_LISTAR, Map.of()));
        assertEquals("listarRecursos", servicio.metodoLlamado);
    }

    @Test
    void listarConCategoriaFiltraSinDescripcion() {
        assertExito(comoAdmin(Operaciones.REC_LISTAR, Map.of("idCategoria", "CAT-000001")));

        assertEquals("filtrarRecursos(2)", servicio.metodoLlamado);
        assertEquals("CAT-000001", servicio.categoriaRecibida);
        assertNull(servicio.descripcionRecibida);
    }

    @Test
    void listarConLosDosFiltrosLosPasaAlService() {
        assertExito(comoAdmin(Operaciones.REC_LISTAR, Map.of("idCategoria", "CAT-000001", "descripcion", "epson")));

        assertEquals("filtrarRecursos(2)", servicio.metodoLlamado);
        assertEquals("CAT-000001", servicio.categoriaRecibida);
        assertEquals("epson", servicio.descripcionRecibida);
    }

    @Test
    void listarSoloConDescripcionFiltraEnTodasLasCategorias() {
        assertExito(comoAdmin(Operaciones.REC_LISTAR, Map.of("descripcion", "epson")));

        assertEquals("filtrarRecursos(2)", servicio.metodoLlamado);
        assertNull(servicio.categoriaRecibida);
        assertEquals("epson", servicio.descripcionRecibida);
    }

    // --- nuevo ID ---

    @Test
    void nuevoIdDevuelveElTexto() {
        MensajeRed respuesta = comoAdmin(Operaciones.REC_NUEVO_ID, null);

        assertExito(respuesta);
        assertTrue(respuesta.getDatos().isTextual());
        assertEquals("482913", respuesta.getDatos().asText());
    }
}
