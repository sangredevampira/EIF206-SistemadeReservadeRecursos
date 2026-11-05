package ui;

import Sistema.DTO.CategoriaDTO;
import Sistema.DTO.RecursoDTO;
import Sistema.DTO.ResultadoOperacionDTO;
import Sistema.Service.ICategoriaService;
import Sistema.Service.IRecursoService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prueba como decide guardarRecurso entre modificar y registrar
 * (RecursoViewController.existeRecurso) con un IRecursoService falso: no usa red
 * ni abre ventanas, asi que no necesita JavaFX. Lo demas de la pantalla se revisa
 * en la prueba manual.
 */
class RecursoViewControllerTest {

    @Test
    void existeRecursoDevuelveTrueSiElServidorLoEncuentra() {
        RecursoFalso recursos = new RecursoFalso();
        recursos.encontrado = new RecursoDTO("118554", "Laptop Dell", new CategoriaDTO("CAT-000001", "Laptop"));

        assertTrue(new RecursoViewController(recursos, new CategoriaFalsa()).existeRecurso("118554"));
        assertEquals("118554", recursos.idPedido);
    }

    @Test
    void existeRecursoDevuelveFalseSiElServidorDiceQueNoExiste() {
        RecursoFalso recursos = new RecursoFalso();
        recursos.error = new IllegalArgumentException("El recurso solicitado no fue encontrado");

        assertFalse(new RecursoViewController(recursos, new CategoriaFalsa()).existeRecurso("999999"));
    }

    @Test
    void existeRecursoDejaPasarElErrorDeRed() {
        // si se tomara como "no existe", guardarRecurso intentaria registrar
        RecursoFalso recursos = new RecursoFalso();
        IllegalStateException red = new IllegalStateException("No fue posible comunicarse con el servidor.");
        recursos.error = red;

        RecursoViewController controller = new RecursoViewController(recursos, new CategoriaFalsa());

        assertSame(red, assertThrows(IllegalStateException.class, () -> controller.existeRecurso("118554")));
    }

    @Test
    void constructorRechazaServiciosNulos() {
        assertThrows(NullPointerException.class, () -> new RecursoViewController(null, new CategoriaFalsa()));
        assertThrows(NullPointerException.class, () -> new RecursoViewController(new RecursoFalso(), null));
    }

    // Solo responde buscarRecursoID; lo demas no debe llamarse en estas pruebas
    private static class RecursoFalso implements IRecursoService {
        RecursoDTO encontrado;
        RuntimeException error;
        String idPedido;

        @Override
        public RecursoDTO buscarRecursoID(String id) {
            idPedido = id;
            if (error != null) {
                throw error;
            }
            return encontrado;
        }

        @Override
        public ResultadoOperacionDTO registrarRecurso(RecursoDTO recurso) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ResultadoOperacionDTO modificarRecurso(RecursoDTO recurso) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ResultadoOperacionDTO eliminarRecurso(String id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<RecursoDTO> filtrarRecursos(String idCategoria) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<RecursoDTO> filtrarRecursos(String idCategoria, String descripcion) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<RecursoDTO> listarRecursos() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String generarNuevoId() {
            throw new UnsupportedOperationException();
        }
    }

    // existeRecurso no usa categorias: cualquier llamada es un error de la prueba
    private static class CategoriaFalsa implements ICategoriaService {
        @Override
        public ResultadoOperacionDTO registrar(CategoriaDTO categoria) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ResultadoOperacionDTO modificar(CategoriaDTO categoria) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ResultadoOperacionDTO eliminar(String id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<CategoriaDTO> buscar(String descripcionParcial) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<CategoriaDTO> listarTodos() {
            throw new UnsupportedOperationException();
        }
    }
}
