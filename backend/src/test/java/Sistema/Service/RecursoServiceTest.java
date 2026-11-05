package Sistema.Service;

import Sistema.DTO.CategoriaDTO;
import Sistema.DTO.RecursoDTO;
import Sistema.DTO.ResultadoOperacionDTO;
import Sistema.Logica.RecursoLogica;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas de RecursoService: solo comprueban que delega en RecursoLogica (la
 * capa Service no valida nada). Las reglas se prueban en RecursoLogicaTest y
 * el SQL en RecursoDatosIT.
 */
public class RecursoServiceTest {

    /**
     * Fake de RecursoLogica: devuelve lo que configura cada prueba y guarda
     * los parametros que recibio. Usa RecursoLogica(), que no abre nada.
     */
    static class RecursoLogicaFake extends RecursoLogica {

        // las respuestas que queremos que devuelva la logica
        ResultadoOperacionDTO resultado = ResultadoOperacionDTO.fallo("respuesta de la logica");
        String idARetornarGenerar;
        RecursoDTO recursoARetornarBuscar;
        List<RecursoDTO> listaARetornar;
        RuntimeException excepcionARetornar;

        // los parametros que recibio la logica
        RecursoDTO recursoRecibido;
        String idRecibido;
        String categoriaRecibida;
        String descripcionRecibida;

        @Override
        public ResultadoOperacionDTO registrarRecurso(RecursoDTO nuevoRecurso) {
            recursoRecibido = nuevoRecurso;
            return resultado;
        }

        @Override
        public ResultadoOperacionDTO modificarRecurso(RecursoDTO recurso) {
            recursoRecibido = recurso;
            return resultado;
        }

        @Override
        public ResultadoOperacionDTO eliminarRecurso(String id) {
            idRecibido = id;
            return resultado;
        }

        @Override
        public String generarNuevoId() { return idARetornarGenerar; }

        @Override
        public RecursoDTO buscarRecursoID(String id) {
            idRecibido = id;
            if (excepcionARetornar != null) throw excepcionARetornar;
            return recursoARetornarBuscar;
        }

        @Override
        public List<RecursoDTO> filtrarRecursos(String idCategoria) {
            categoriaRecibida = idCategoria;
            return listaARetornar;
        }

        @Override
        public List<RecursoDTO> filtrarRecursos(String idCategoria, String descripcion) {
            categoriaRecibida = idCategoria;
            descripcionRecibida = descripcion;
            return listaARetornar;
        }

        @Override
        public List<RecursoDTO> listarRecursos() { return listaARetornar; }
    }

    private final RecursoLogicaFake fake = new RecursoLogicaFake();
    private final RecursoService service = new RecursoService(fake);

    private static RecursoDTO proyector() {
        return new RecursoDTO("100001", "Proyector Epson", new CategoriaDTO("CAT-000001", "Salas"));
    }

    @Test
    void registrarRecursoDelegaYDevuelveElMismoResultado() {
        RecursoDTO nuevo = proyector();

        assertSame(fake.resultado, service.registrarRecurso(nuevo));
        assertSame(nuevo, fake.recursoRecibido);
    }

    @Test
    void modificarRecursoDelegaYDevuelveElMismoResultado() {
        fake.resultado = ResultadoOperacionDTO.exito();
        RecursoDTO recurso = proyector();

        assertSame(fake.resultado, service.modificarRecurso(recurso));
        assertSame(recurso, fake.recursoRecibido);
    }

    @Test
    void eliminarRecursoDelegaYDevuelveElMismoResultado() {
        assertSame(fake.resultado, service.eliminarRecurso("100002"));
        assertEquals("100002", fake.idRecibido);
    }

    @Test
    void generarNuevoIdDelegaEnLaLogica() {
        fake.idARetornarGenerar = "482913";

        assertEquals("482913", service.generarNuevoId());
    }

    @Test
    void buscarRecursoIDDelegaEnLaLogicaYDevuelveElRecursoEncontrado() {
        RecursoDTO esperado = proyector();
        fake.recursoARetornarBuscar = esperado;

        assertSame(esperado, service.buscarRecursoID("100001"));
        assertEquals("100001", fake.idRecibido);
    }

    @Test
    void buscarRecursoIDPropagaLaExcepcionDeLaLogicaSinAtraparla() {
        fake.excepcionARetornar = new IllegalArgumentException("El recurso solicitado no fue encontrado");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.buscarRecursoID("999999"));
        assertEquals("El recurso solicitado no fue encontrado", ex.getMessage());
    }

    @Test
    void filtrarPorCategoriaDelegaEnLaVersionDeUnParametro() {
        fake.listaARetornar = List.of(proyector());

        assertSame(fake.listaARetornar, service.filtrarRecursos("CAT-000001"));
        assertEquals("CAT-000001", fake.categoriaRecibida);
        assertNull(fake.descripcionRecibida);
    }

    @Test
    void filtrarPorCategoriaYDescripcionDelegaLosDosFiltros() {
        fake.listaARetornar = List.of(proyector());

        assertSame(fake.listaARetornar, service.filtrarRecursos("CAT-000001", "epson"));
        assertEquals("CAT-000001", fake.categoriaRecibida);
        assertEquals("epson", fake.descripcionRecibida);
    }

    @Test
    void listarRecursosDelegaEnLaLogica() {
        fake.listaARetornar = List.of(proyector());

        assertSame(fake.listaARetornar, service.listarRecursos());
    }
}
