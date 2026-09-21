package Sistema.Service;

import Sistema.DTO.Rol;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias de AuthService (login y cambio de clave).
 *
 * El proyecto no tiene inyeccion de dependencias: AuthService -> UsuarioLogica
 * -> UsuarioDatos siempre apunta a src/data/usuarios.json (y, para el
 * funcionario asociado, a src/data/funcionarios.json). Para no depender del
 * contenido real de esos archivos ni corromperlo, cada prueba:
 *   1) respalda el contenido actual de los archivos,
 *   2) escribe datos de prueba conocidos,
 *   3) corre la prueba,
 *   4) restaura el contenido original en @AfterEach (incluso si la prueba falla).
 */
class AuthServiceTest {

    private static final Path RUTA_USUARIOS = Paths.get("src/data/usuarios.json");
    private static final Path RUTA_FUNCIONARIOS = Paths.get("src/data/funcionarios.json");

    private String backupUsuarios;
    private String backupFuncionarios;

    private AuthService authService;

    @BeforeEach
    void prepararDatosDePrueba() throws IOException {
        backupUsuarios = leerSiExiste(RUTA_USUARIOS);
        backupFuncionarios = leerSiExiste(RUTA_FUNCIONARIOS);

        Files.writeString(RUTA_USUARIOS, """
                [
                  {"id":"admin1","clave":"clave123","rol":"ADMINISTRADOR"},
                  {"id":"func1","clave":"clave456","rol":"FUNCIONARIO"}
                ]
                """);

        Files.writeString(RUTA_FUNCIONARIOS, """
                [
                  {"id":"func1","nombre":"Ana Perez","telefono":"88889999"}
                ]
                """);

        authService = new AuthService();
    }

    @AfterEach
    void restaurarDatosOriginales() throws IOException {
        restaurar(RUTA_USUARIOS, backupUsuarios);
        restaurar(RUTA_FUNCIONARIOS, backupFuncionarios);
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
    void loginExitosoParaAdministrador() {
        var resultado = authService.login("admin1", "clave123");

        assertTrue(resultado.isExitoso());
        assertEquals(Rol.ADMINISTRADOR, resultado.getUsuario().getRol());
        assertNull(resultado.getFuncionarioAsociado());
    }

    @Test
    void loginExitosoParaFuncionarioCargaElFuncionarioAsociado() {
        var resultado = authService.login("func1", "clave456");

        assertTrue(resultado.isExitoso());
        assertNotNull(resultado.getFuncionarioAsociado());
        assertEquals("Ana Perez", resultado.getFuncionarioAsociado().getNombre());
    }

    @Test
    void loginFallaConClaveIncorrecta() {
        var resultado = authService.login("admin1", "claveMala");

        assertFalse(resultado.isExitoso());
        assertEquals("La clave ingresada es incorrecta.", resultado.getMensajeError());
    }

    @Test
    void loginFallaConUsuarioInexistente() {
        var resultado = authService.login("noExiste", "algo");

        assertFalse(resultado.isExitoso());
        assertEquals("El usuario ingresado no existe.", resultado.getMensajeError());
    }

    @Test
    void loginFallaConIdVacio() {
        var resultado = authService.login("", "clave123");

        assertFalse(resultado.isExitoso());
        assertEquals("El ID no puede estar vacio.", resultado.getMensajeError());
    }

    @Test
    void loginFallaConClaveVacia() {
        var resultado = authService.login("admin1", "");

        assertFalse(resultado.isExitoso());
        assertEquals("La clave no puede estar vacia.", resultado.getMensajeError());
    }

    @Test
    void loginFallaSiElUsuarioNoTieneRolAsignado() throws IOException {
        Files.writeString(RUTA_USUARIOS, """
                [{"id":"sinRol","clave":"clave789","rol":null}]
                """);

        var resultado = authService.login("sinRol", "clave789");

        assertFalse(resultado.isExitoso());
        assertEquals("El usuario no tiene un rol asignado. Contacte al administrador.", resultado.getMensajeError());
    }

    @Test
    void loginLanzaExcepcionSiElArchivoDeUsuariosEstaDanado() throws IOException {
        Files.writeString(RUTA_USUARIOS, "{ esto no es una lista valida de usuarios");

        assertThrows(RuntimeException.class, () -> authService.login("admin1", "clave123"));
    }

    @Test
    void cambioDeClaveExitosoQuedaPersistido() {
        var resultado = authService.cambiarClave("admin1", "clave123", "nuevaClave1", "nuevaClave1");
        assertTrue(resultado.isExitoso());

        // Verifica que el cambio realmente se guardo: login con la clave nueva debe funcionar.
        var login = authService.login("admin1", "nuevaClave1");
        assertTrue(login.isExitoso());
    }

    @Test
    void cambioDeClaveFallaConClaveActualIncorrecta() {
        var resultado = authService.cambiarClave("admin1", "claveMala", "nuevaClave1", "nuevaClave1");

        assertFalse(resultado.isExitoso());
        assertEquals("La clave actual es incorrecta.", resultado.getMensajeError());
    }

    @Test
    void cambioDeClaveFallaSiLasConfirmacionesNoCoinciden() {
        var resultado = authService.cambiarClave("admin1", "clave123", "nuevaClave1", "otraCosa");

        assertFalse(resultado.isExitoso());
        assertEquals("Las dos claves nuevas no coinciden.", resultado.getMensajeError());
    }

    @Test
    void cambioDeClaveFallaConClaveNuevaVacia() {
        var resultado = authService.cambiarClave("admin1", "clave123", "", "");

        assertFalse(resultado.isExitoso());
        assertEquals("La clave nueva no puede estar vacia.", resultado.getMensajeError());
    }
}