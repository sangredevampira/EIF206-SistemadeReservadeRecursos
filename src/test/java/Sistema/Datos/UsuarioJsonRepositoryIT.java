package Sistema.Datos;

import Sistema.DTO.Rol;
import Sistema.DTO.Usuario;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Prueba de integracion: verifica que UsuarioDatos efectivamente lee y
 * escribe src/data/usuarios.json en disco (a diferencia de AuthServiceTest,
 * que prueba reglas de negocio; aqui se prueba la persistencia en si).
 *
 * Cada metodo crea una instancia NUEVA de UsuarioDatos despues de guardar,
 * para forzar una lectura real del archivo y no de un objeto en memoria.
 */
class UsuarioJsonRepositoryIT {

    private static final Path RUTA_USUARIOS = Paths.get("src/data/usuarios.json");

    private String backupUsuarios;

    @BeforeEach
    void respaldarArchivoReal() throws IOException {
        backupUsuarios = Files.exists(RUTA_USUARIOS) ? Files.readString(RUTA_USUARIOS) : null;
    }

    @AfterEach
    void restaurarArchivoReal() throws IOException {
        if (backupUsuarios == null) {
            Files.deleteIfExists(RUTA_USUARIOS);
        } else {
            Files.writeString(RUTA_USUARIOS, backupUsuarios);
        }
    }

    @Test
    void guardarPersisteElUsuarioYSePuedeLeerConUnaInstanciaNueva() throws IOException {
        Files.writeString(RUTA_USUARIOS, "[]");

        new UsuarioDatos().guardar(new Usuario("user_it1", "clave1", Rol.FUNCIONARIO));

        Usuario leido = new UsuarioDatos().buscarPorId("user_it1");
        assertNotNull(leido);
        assertEquals("clave1", leido.getClave());
        assertEquals(Rol.FUNCIONARIO, leido.getRol());
    }

    @Test
    void buscarPorIdDevuelveNullSiNoExisteEnElArchivo() throws IOException {
        Files.writeString(RUTA_USUARIOS, "[]");

        assertNull(new UsuarioDatos().buscarPorId("noExiste"));
    }

    @Test
    void actualizarClaveQuedaPersistidoEnElArchivo() throws IOException {
        Files.writeString(RUTA_USUARIOS, """
                [{"id":"user_it2","clave":"vieja","rol":"ADMINISTRADOR"}]
                """);

        new UsuarioDatos().actualizarClave("user_it2", "nueva");

        Usuario leido = new UsuarioDatos().buscarPorId("user_it2");
        assertEquals("nueva", leido.getClave());
    }

    @Test
    void eliminarQuitaElUsuarioDelArchivo() throws IOException {
        Files.writeString(RUTA_USUARIOS, """
                [{"id":"user_it3","clave":"clave3","rol":"FUNCIONARIO"}]
                """);

        new UsuarioDatos().eliminar("user_it3");

        assertNull(new UsuarioDatos().buscarPorId("user_it3"));
    }

    @Test
    void siElArchivoNoExisteSeCreaConElAdministradorInicial() throws IOException {
        Files.deleteIfExists(RUTA_USUARIOS);

        Usuario admin = new UsuarioDatos().buscarPorId("admin");

        assertNotNull(admin);
        assertEquals("admin", admin.getClave());
        assertEquals(Rol.ADMINISTRADOR, admin.getRol());
    }
}
