package Sistema.Datos;

import Sistema.DTO.Funcionario;
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
 * Prueba de integracion: verifica que FuncionarioDatos efectivamente lee y
 * escribe src/data/funcionarios.json en disco. Igual que en
 * UsuarioJsonRepositoryIT, cada verificacion usa una instancia NUEVA de
 * FuncionarioDatos despues de guardar, para forzar una lectura real del
 * archivo.
 */
class FuncionarioJsonRepositoryIT {

    private static final Path RUTA_FUNCIONARIOS = Paths.get("src/data/funcionarios.json");

    private String backupFuncionarios;

    @BeforeEach
    void respaldarArchivoReal() throws IOException {
        backupFuncionarios = Files.exists(RUTA_FUNCIONARIOS) ? Files.readString(RUTA_FUNCIONARIOS) : null;
    }

    @AfterEach
    void restaurarArchivoReal() throws IOException {
        if (backupFuncionarios == null) {
            Files.deleteIfExists(RUTA_FUNCIONARIOS);
        } else {
            Files.writeString(RUTA_FUNCIONARIOS, backupFuncionarios);
        }
    }

    @Test
    void guardarPersisteElFuncionarioYSePuedeLeerConUnaInstanciaNueva() throws IOException {
        Files.writeString(RUTA_FUNCIONARIOS, "[]");

        new FuncionarioDatos().guardar(new Funcionario("func_it1", "Carla Ruiz", "85551111"));

        Funcionario leido = new FuncionarioDatos().buscarPorId("func_it1");
        assertNotNull(leido);
        assertEquals("Carla Ruiz", leido.getNombre());
        assertEquals("85551111", leido.getTelefono());
    }

    @Test
    void listarTodosLeeTodosLosRegistrosDelArchivo() throws IOException {
        Files.writeString(RUTA_FUNCIONARIOS, """
                [
                  {"id":"func_it2","nombre":"Ana Perez","telefono":"88889999"},
                  {"id":"func_it3","nombre":"Beto Soto","telefono":"87776666"}
                ]
                """);

        List<Funcionario> todos = new FuncionarioDatos().listarTodos();

        assertEquals(2, todos.size());
    }

    @Test
    void modificarQuedaPersistidoEnElArchivo() throws IOException {
        Files.writeString(RUTA_FUNCIONARIOS, """
                [{"id":"func_it4","nombre":"Nombre Viejo","telefono":"80000000"}]
                """);

        new FuncionarioDatos().modificar(new Funcionario("func_it4", "Nombre Nuevo", "80000001"));

        Funcionario leido = new FuncionarioDatos().buscarPorId("func_it4");
        assertEquals("Nombre Nuevo", leido.getNombre());
        assertEquals("80000001", leido.getTelefono());
    }

    @Test
    void eliminarQuitaElFuncionarioDelArchivo() throws IOException {
        Files.writeString(RUTA_FUNCIONARIOS, """
                [{"id":"func_it5","nombre":"Diego Mora","telefono":"86662222"}]
                """);

        new FuncionarioDatos().eliminar("func_it5");

        assertNull(new FuncionarioDatos().buscarPorId("func_it5"));
    }

    @Test
    void buscarPorNombreParcialEsInsensibleAMayusculasLeyendoDelArchivo() throws IOException {
        Files.writeString(RUTA_FUNCIONARIOS, """
                [
                  {"id":"func_it6","nombre":"Ana Perez","telefono":"88889999"},
                  {"id":"func_it7","nombre":"Beto Soto","telefono":"87776666"}
                ]
                """);

        List<Funcionario> resultado = new FuncionarioDatos().buscar(null, "ana");

        assertEquals(1, resultado.size());
        assertEquals("func_it6", resultado.get(0).getId());
    }
}