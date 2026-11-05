package Sistema.Red;

import Sistema.DTO.CategoriaDTO;
import Sistema.DTO.ResultadoOperacionDTO;
import Sistema.Service.ICategoriaService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static Sistema.Red.ApoyoManejadores.*;
import static org.junit.jupiter.api.Assertions.*;

/** Pruebas de unidad de ManejadorCategorias (sin red ni MySQL). */
class ManejadorCategoriasTest {

    /** Service falso: devuelve lo configurado, guarda lo recibido y cuenta las llamadas. */
    static class CategoriaServiceFalso implements ICategoriaService {
        ResultadoOperacionDTO resultado = ResultadoOperacionDTO.exito();
        List<CategoriaDTO> lista = List.of(new CategoriaDTO("CAT-000001", "Laptop"));
        int llamadas;
        CategoriaDTO categoriaRecibida;
        String idRecibido;
        String descripcionRecibida = "sin llamar";

        @Override
        public ResultadoOperacionDTO registrar(CategoriaDTO categoria) {
            llamadas++;
            categoriaRecibida = categoria;
            return resultado;
        }

        @Override
        public ResultadoOperacionDTO modificar(CategoriaDTO categoria) {
            llamadas++;
            categoriaRecibida = categoria;
            return resultado;
        }

        @Override
        public ResultadoOperacionDTO eliminar(String id) {
            llamadas++;
            idRecibido = id;
            return resultado;
        }

        @Override
        public List<CategoriaDTO> buscar(String descripcionParcial) {
            llamadas++;
            descripcionRecibida = descripcionParcial;
            return lista;
        }

        @Override
        public List<CategoriaDTO> listarTodos() {
            llamadas++;
            return lista;
        }
    }

    private CategoriaServiceFalso servicio;
    private DespachadorPeticiones despachador;

    @BeforeEach
    void preparar() {
        servicio = new CategoriaServiceFalso();
        despachador = new DespachadorPeticiones();
        new ManejadorCategorias(servicio).registrarEn(despachador);
    }

    private MensajeRed comoAdmin(String operacion, Object datos) {
        return enviar(despachador, operacion, datos, administrador());
    }

    // --- permisos ---

    @ParameterizedTest
    @ValueSource(strings = {Operaciones.CAT_CREAR, Operaciones.CAT_EDITAR, Operaciones.CAT_ELIMINAR,
            Operaciones.CAT_BUSCAR})
    void unFuncionarioNoPuedeUsarLasOperacionesDeAdministrador(String operacion) {
        MensajeRed respuesta = enviar(despachador, operacion,
                Map.of("id", "CAT-000001", "descripcion", "x", "idCategoria", "CAT-000001"), funcionario());

        assertError(SIN_PERMISO, respuesta);
        assertEquals(0, servicio.llamadas);
    }

    @Test
    void unFuncionarioSiPuedeListarLasCategorias() {
        // la usan Reservas y Calendarizacion
        MensajeRed respuesta = enviar(despachador, Operaciones.CAT_LISTAR, null, funcionario());

        assertExito(respuesta);
        assertEquals(1, servicio.llamadas);
    }

    @Test
    void sinSesionNiSiquieraSePuedeListar() {
        assertError(SIN_SESION, enviar(despachador, Operaciones.CAT_LISTAR, null, sinSesion()));
        assertEquals(0, servicio.llamadas);
    }

    // --- crear y editar ---

    @Test
    void crearPasaLaDescripcionYRespondeExitoSinDatos() {
        MensajeRed respuesta = comoAdmin(Operaciones.CAT_CREAR, Map.of("descripcion", "Proyectores"));

        assertExitoSinDatos(respuesta);
        assertEquals("Proyectores", servicio.categoriaRecibida.getDescripcion());
        assertNull(servicio.categoriaRecibida.getId());   // lo genera el Backend
    }

    @Test
    void crearConFalloDeLaLogicaRespondeErrorConSuMensaje() {
        servicio.resultado = ResultadoOperacionDTO.fallo("Ya existe una categoria con esa descripcion.");

        assertError("Ya existe una categoria con esa descripcion.",
                comoAdmin(Operaciones.CAT_CREAR, Map.of("descripcion", "Laptop")));
    }

    @Test
    void editarPasaIdYDescripcion() {
        assertExitoSinDatos(comoAdmin(Operaciones.CAT_EDITAR, new CategoriaDTO("CAT-000001", "Laptops")));
        assertEquals("CAT-000001", servicio.categoriaRecibida.getId());
        assertEquals("Laptops", servicio.categoriaRecibida.getDescripcion());
    }

    @Test
    void crearOEditarSinDatosOConPropiedadDesconocidaNoEsValido() {
        assertError(DATOS_INVALIDOS, comoAdmin(Operaciones.CAT_CREAR, null));
        assertError(DATOS_INVALIDOS, comoAdmin(Operaciones.CAT_EDITAR, null));
        assertError(DATOS_INVALIDOS, comoAdmin(Operaciones.CAT_CREAR, Map.of("descripcion", "x", "color", "rojo")));
        assertEquals(0, servicio.llamadas);
    }

    // --- eliminar ---

    @Test
    void eliminarPasaElIdAlService() {
        assertExitoSinDatos(comoAdmin(Operaciones.CAT_ELIMINAR, Map.of("idCategoria", "CAT-000007")));
        assertEquals("CAT-000007", servicio.idRecibido);
    }

    @Test
    void eliminarRechazadoPorLaLogicaRespondeErrorConSuMensaje() {
        servicio.resultado = ResultadoOperacionDTO.fallo("No se puede eliminar la categoria porque tiene recursos asociados");

        assertError("No se puede eliminar la categoria porque tiene recursos asociados",
                comoAdmin(Operaciones.CAT_ELIMINAR, Map.of("idCategoria", "CAT-000001")));
    }

    @Test
    void eliminarSinIdNoEsValido() {
        assertError(DATOS_INVALIDOS, comoAdmin(Operaciones.CAT_ELIMINAR, null));
        assertError(DATOS_INVALIDOS, comoAdmin(Operaciones.CAT_ELIMINAR, Map.of("id", "CAT-000001")));
        assertEquals(0, servicio.llamadas);
    }

    // --- buscar y listar ---

    @Test
    void buscarPasaLaDescripcionOElNullSiNoViene() {
        assertExito(comoAdmin(Operaciones.CAT_BUSCAR, Map.of("descripcion", "lap")));
        assertEquals("lap", servicio.descripcionRecibida);

        assertExito(comoAdmin(Operaciones.CAT_BUSCAR, null));
        assertNull(servicio.descripcionRecibida);
    }

    @Test
    void listarDevuelveUnArregloDeCategorias() {
        MensajeRed respuesta = comoAdmin(Operaciones.CAT_LISTAR, null);

        assertExito(respuesta);
        JsonNode lista = respuesta.getDatos();
        assertTrue(lista.isArray());
        assertEquals("CAT-000001", lista.get(0).get("id").asText());
        assertEquals("Laptop", lista.get(0).get("descripcion").asText());
    }
}
