package Sistema.Service;

import Sistema.DTO.CategoriaDTO;
import Sistema.DTO.ResultadoOperacionDTO;
import Sistema.Logica.CategoriaLogica;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas de unidad de CategoriaService. La capa Service solo delega
 * ("en esta capa NO se realizan validaciones"), asi que se prueba que pasa
 * los mismos parametros a CategoriaLogica y devuelve lo mismo que ella.
 * Las reglas se prueban en CategoriaLogicaTest y el SQL en CategoriaDatosIT.
 */
class CategoriaServiceTest {

    // CategoriaLogica falsa: anota lo que recibe y devuelve lo preparado.
    // super(null, null, null): no necesita datos ni conexiones.
    static class CategoriaLogicaFake extends CategoriaLogica {
        final ResultadoOperacionDTO resultado = ResultadoOperacionDTO.fallo("respuesta preparada");
        final List<CategoriaDTO> lista = List.of(new CategoriaDTO("CAT-000001", "Laptop"));
        Object recibido;

        CategoriaLogicaFake() { super(null, null, null); }

        @Override public ResultadoOperacionDTO registrar(CategoriaDTO c) { recibido = c; return resultado; }
        @Override public ResultadoOperacionDTO modificar(CategoriaDTO c) { recibido = c; return resultado; }
        @Override public ResultadoOperacionDTO eliminar(String id) { recibido = id; return resultado; }
        @Override public List<CategoriaDTO> buscar(String texto) { recibido = texto; return lista; }
        @Override public List<CategoriaDTO> listarTodos() { recibido = "listarTodos"; return lista; }
    }

    private CategoriaLogicaFake logica;
    private CategoriaService service;

    @BeforeEach
    void setUp() {
        logica = new CategoriaLogicaFake();
        service = new CategoriaService(logica);
    }

    @Test
    void registrarDelegaYDevuelveElMismoResultado() {
        CategoriaDTO categoria = new CategoriaDTO(null, "Proyector");

        assertSame(logica.resultado, service.registrar(categoria));
        assertSame(categoria, logica.recibido);
    }

    @Test
    void modificarDelegaYDevuelveElMismoResultado() {
        CategoriaDTO categoria = new CategoriaDTO("CAT-000001", "Proyector");

        assertSame(logica.resultado, service.modificar(categoria));
        assertSame(categoria, logica.recibido);
    }

    @Test
    void eliminarDelegaYDevuelveElMismoResultado() {
        assertSame(logica.resultado, service.eliminar("CAT-000001"));
        assertEquals("CAT-000001", logica.recibido);
    }

    @Test
    void buscarDelegaElTextoSinCambiarlo() {
        assertSame(logica.lista, service.buscar("  lap "));
        assertEquals("  lap ", logica.recibido);
    }

    @Test
    void listarTodosDelega() {
        assertSame(logica.lista, service.listarTodos());
        assertEquals("listarTodos", logica.recibido);
    }
}
