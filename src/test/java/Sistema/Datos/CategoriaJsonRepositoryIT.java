package Sistema.Datos;

import Sistema.DTO.CategoriaDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Prueba de integracion: verifica que CategoriaDatos efectivamente lee y
 * escribe src/data/categorias.json en disco. Igual que en las otras dos
 * clases IT, cada verificacion usa una instancia NUEVA de CategoriaDatos
 * despues de guardar, para forzar una lectura real del archivo.
 */
class CategoriaJsonRepositoryIT {

    private static final Path RUTA_CATEGORIAS = Paths.get("src/data/categorias.json");

    private String backupCategorias;

    @BeforeEach
    void respaldarArchivoReal() throws IOException {
        backupCategorias = Files.exists(RUTA_CATEGORIAS) ? Files.readString(RUTA_CATEGORIAS) : null;
    }

    @AfterEach
    void restaurarArchivoReal() throws IOException {
        if (backupCategorias == null) {
            Files.deleteIfExists(RUTA_CATEGORIAS);
        } else {
            Files.writeString(RUTA_CATEGORIAS, backupCategorias);
        }
    }

    @Test
    void guardarPersisteLaCategoriaYSePuedeLeerConUnaInstanciaNueva() throws IOException {
        Files.writeString(RUTA_CATEGORIAS, "[]");

        new CategoriaDatos().guardar(new CategoriaDTO("CAT-000001", "Proyector HD"));

        CategoriaDTO leida = new CategoriaDatos().buscarPorId("CAT-000001");
        assertNotNull(leida);
        assertEquals("Proyector HD", leida.getDescripcion());
    }

    @Test
    void modificarQuedaPersistidoEnElArchivo() throws IOException {
        Files.writeString(RUTA_CATEGORIAS, """
                [{"id":"CAT-000002","descripcion":"Descripcion vieja"}]
                """);

        new CategoriaDatos().modificar(new CategoriaDTO("CAT-000002", "Descripcion nueva"));

        CategoriaDTO leida = new CategoriaDatos().buscarPorId("CAT-000002");
        assertEquals("Descripcion nueva", leida.getDescripcion());
    }

    @Test
    void eliminarQuitaLaCategoriaDelArchivo() throws IOException {
        Files.writeString(RUTA_CATEGORIAS, """
                [{"id":"CAT-000003","descripcion":"Laptop windows 11"}]
                """);

        new CategoriaDatos().eliminar("CAT-000003");

        assertNull(new CategoriaDatos().buscarPorId("CAT-000003"));
    }

    @Test
    void generarSiguienteIdUsaElNumeroMasAltoAunConHuecos() throws IOException {
        // CAT-000002 fue "borrada" (no aparece), pero CAT-000005 si existe;
        // el siguiente ID debe ser CAT-000006, no CAT-000003.
        Files.writeString(RUTA_CATEGORIAS, """
                [
                  {"id":"CAT-000001","descripcion":"Sala para 10 personas"},
                  {"id":"CAT-000005","descripcion":"Laptop windows 11"}
                ]
                """);

        String siguienteId = new CategoriaDatos().generarSiguienteId();

        assertEquals("CAT-000006", siguienteId);
    }

    @Test
    void buscarPorDescripcionParcialEsInsensibleAMayusculasLeyendoDelArchivo() throws IOException {
        Files.writeString(RUTA_CATEGORIAS, """
                [
                  {"id":"CAT-000001","descripcion":"Sala para 10 personas"},
                  {"id":"CAT-000002","descripcion":"Laptop windows 11"}
                ]
                """);

        List<CategoriaDTO> resultado = new CategoriaDatos().buscar("laptop");

        assertEquals(1, resultado.size());
        assertEquals("CAT-000002", resultado.get(0).getId());
    }
}