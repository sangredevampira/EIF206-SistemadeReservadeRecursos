package Sistema.Service;

import Sistema.DTO.CategoriaDTO;
import Sistema.Datos.RecursoDatos;
import Sistema.Datos.UsuarioDatos;
import Sistema.Logica.CategoriaLogica;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias de CategoriaService (que es un passthrough puro hacia
 * CategoriaLogica). Las categorias siguen en JSON hasta el paso 5b: se
 * respalda y restaura categorias.json en cada prueba. La revision de
 * "tiene recursos" ya es de MySQL, asi que se reemplaza por un RecursoDatos
 * falso (la consulta real se prueba en RecursoDatosIT).
 */
class CategoriaServiceTest {

    private static final Path RUTA_CATEGORIAS = Paths.get("src/data/categorias.json");

    // RecursoDatos falso: responde desde un conjunto en memoria, sin MySQL
    static class RecursoDatosFake extends RecursoDatos {
        Set<String> categoriasConRecursos = Set.of("CAT-000003");
        boolean baseCaida = false;

        @Override
        public boolean categoriaTieneRecursos(String idCategoria) {
            if (baseCaida) throw new IllegalStateException(UsuarioDatos.MENSAJE_ERROR_BD);
            return categoriasConRecursos.contains(idCategoria);
        }
    }

    private String backupCategorias;

    private RecursoDatosFake recursoDatos;
    private CategoriaService categoriaService;

    @BeforeEach
    void prepararDatosDePrueba() throws IOException {
        backupCategorias = leerSiExiste(RUTA_CATEGORIAS);

        Files.writeString(RUTA_CATEGORIAS, """
                [
                  {"id":"CAT-000001","descripcion":"Sala para 10 personas"},
                  {"id":"CAT-000003","descripcion":"Laptop windows 11"}
                ]
                """);

        recursoDatos = new RecursoDatosFake();
        categoriaService = new CategoriaService(new CategoriaLogica(recursoDatos));
    }

    @AfterEach
    void restaurarDatosOriginales() throws IOException {
        restaurar(RUTA_CATEGORIAS, backupCategorias);
    }

    private String leerSiExiste(Path ruta) throws IOException {
        return Files.exists(ruta) ? Files.readString(ruta) : null;
    }

    private void restaurar(Path ruta, String contenidoOriginal) throws IOException {
        if (contenidoOriginal == null) {
            Files.deleteIfExists(ruta);
        } else {
            Files.writeString(ruta, contenidoOriginal);
        }
    }

    @Test
    void registrarGeneraIdAutomaticoConsecutivo() {
        var resultado = categoriaService.registrar(new CategoriaDTO(null, "Proyector HD"));
        assertTrue(resultado.isExitoso());

        boolean existeConIdEsperado = categoriaService.listarTodos().stream()
                .anyMatch(c -> c.getDescripcion().equals("Proyector HD") && c.getId().equals("CAT-000004"));
        assertTrue(existeConIdEsperado);
    }

    @Test
    void registrarFallaConDescripcionVacia() {
        var resultado = categoriaService.registrar(new CategoriaDTO(null, "   "));

        assertFalse(resultado.isExitoso());
        assertEquals("La descripcion no puede estar vacia.", resultado.getMensajeError());
    }

    @Test
    void registrarFallaConDescripcionDuplicadaSinImportarMayusculas() {
        var resultado = categoriaService.registrar(new CategoriaDTO(null, "sala para 10 personas"));

        assertFalse(resultado.isExitoso());
        assertEquals("Ya existe una categoria con esa descripcion.", resultado.getMensajeError());
    }

    @Test
    void modificarFallaSiLaCategoriaNoExiste() {
        var resultado = categoriaService.modificar(new CategoriaDTO("CAT-999999", "Lo que sea"));

        assertFalse(resultado.isExitoso());
        assertEquals("La categoria no existe.", resultado.getMensajeError());
    }

    @Test
    void eliminarFallaSiTieneRecursosAsociados() {
        var resultado = categoriaService.eliminar("CAT-000003");

        assertFalse(resultado.isExitoso());
        assertEquals("No se puede eliminar la categoria porque tiene recursos asociados"
                        + " (tambien cuentan los recursos dados de baja).",
                resultado.getMensajeError());
    }

    @Test
    void eliminarConLaBaseCaidaDevuelveFalloConMensajeGenericoYNoBorra() {
        recursoDatos.baseCaida = true;

        var resultado = categoriaService.eliminar("CAT-000001");

        assertFalse(resultado.isExitoso());
        assertEquals(UsuarioDatos.MENSAJE_ERROR_BD, resultado.getMensajeError());
        assertTrue(categoriaService.listarTodos().stream().anyMatch(c -> c.getId().equals("CAT-000001")));
    }

    @Test
    void eliminarFuncionaSiNoTieneRecursosAsociados() {
        var resultado = categoriaService.eliminar("CAT-000001");
        assertTrue(resultado.isExitoso());

        boolean sigueExistiendo = categoriaService.listarTodos().stream()
                .anyMatch(c -> c.getId().equals("CAT-000001"));
        assertFalse(sigueExistiendo);
    }

    @Test
    void buscarPorDescripcionParcialEsInsensibleAMayusculas() {
        List<CategoriaDTO> resultado = categoriaService.buscar("laptop");

        assertEquals(1, resultado.size());
        assertEquals("CAT-000003", resultado.get(0).getId());
    }

    @Test
    void lanzaExcepcionSiElArchivoDeCategoriasEstaDanado() throws IOException {
        Files.writeString(RUTA_CATEGORIAS, "{ esto no es una lista valida de categorias");

        assertThrows(RuntimeException.class, () -> categoriaService.listarTodos());
    }
}
