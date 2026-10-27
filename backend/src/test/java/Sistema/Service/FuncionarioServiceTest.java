package Sistema.Service;

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
 * Pruebas unitarias de FuncionarioService. FuncionarioLogica toca tres
 * archivos (funcionarios.json, usuarios.json y reservas.json, esta ultima
 * a traves de ReservaLogica.tieneReservasFuturas), asi que los tres se
 * respaldan y restauran en cada prueba.
 */
class FuncionarioServiceTest {

    private static final Path RUTA_FUNCIONARIOS = Paths.get("src/data/funcionarios.json");
    private static final Path RUTA_USUARIOS = Paths.get("src/data/usuarios.json");
    private static final Path RUTA_RESERVAS = Paths.get("src/data/reservas.json");

    private String backupFuncionarios;
    private String backupUsuarios;
    private String backupReservas;

    private FuncionarioService funcionarioService;

    @BeforeEach
    void prepararDatosDePrueba() throws IOException {
        backupFuncionarios = leerSiExiste(RUTA_FUNCIONARIOS);
        backupUsuarios = leerSiExiste(RUTA_USUARIOS);
        backupReservas = leerSiExiste(RUTA_RESERVAS);

        Files.writeString(RUTA_FUNCIONARIOS, """
                [
                  {"id":"func1","nombre":"Ana Perez","telefono":"88889999"},
                  {"id":"func2","nombre":"Beto Soto","telefono":"87776666"}
                ]
                """);

        Files.writeString(RUTA_USUARIOS, """
                [
                  {"id":"admin","clave":"admin","rol":"ADMINISTRADOR"},
                  {"id":"func1","clave":"func1","rol":"FUNCIONARIO"},
                  {"id":"func2","clave":"func2","rol":"FUNCIONARIO"}
                ]
                """);

        // func2 tiene una reserva futura activa (para la regla de eliminacion);
        // func1 no tiene ninguna reserva.
        Files.writeString(RUTA_RESERVAS, """
                [
                  {"idReserva":"RES-000001","idFuncionario":"func2","nombreFuncionario":"Beto Soto",
                   "nombreActividad":"Reunion de ventas","fecha":"2099-01-01",
                   "horaInicio":"08:00:00","horaFin":"10:00:00","recursosReservados":[],
                   "estado":"ACTIVA"}
                ]
                """);

        funcionarioService = new FuncionarioService();
    }

    @AfterEach
    void restaurarDatosOriginales() throws IOException {
        restaurar(RUTA_FUNCIONARIOS, backupFuncionarios);
        restaurar(RUTA_USUARIOS, backupUsuarios);
        restaurar(RUTA_RESERVAS, backupReservas);
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
    void registrarCreaFuncionarioYUsuarioAsociado() {
        var resultado = funcionarioService.registrar(new Funcionario("func3", "Carla Ruiz", "85551111"));
        assertTrue(resultado.isExitoso());

        boolean existeFuncionario = funcionarioService.listarTodos().stream()
                .anyMatch(f -> f.getId().equals("func3"));
        assertTrue(existeFuncionario);
    }

    @Test
    void registrarFallaSiElIdYaExisteComoFuncionario() {
        var resultado = funcionarioService.registrar(new Funcionario("func1", "Otro Nombre", "80001111"));

        assertFalse(resultado.isExitoso());
        assertEquals("Ya existe un funcionario con ese ID.", resultado.getMensajeError());
    }

    @Test
    void registrarFallaSiElIdYaExisteComoUsuario() {
        var resultado = funcionarioService.registrar(new Funcionario("admin", "Otro Nombre", "80001111"));

        assertFalse(resultado.isExitoso());
        assertEquals("Ya existe un usuario con ese ID (por ejemplo, el administrador).", resultado.getMensajeError());
    }

    @Test
    void registrarFallaConTelefonoVacio() {
        var resultado = funcionarioService.registrar(new Funcionario("func3", "Carla Ruiz", "   "));

        assertFalse(resultado.isExitoso());
        assertEquals("El telefono no puede estar vacio.", resultado.getMensajeError());
    }

    @Test
    void registrarFallaConIdVacio() {
        var resultado = funcionarioService.registrar(new Funcionario("   ", "Carla Ruiz", "85551111"));

        assertFalse(resultado.isExitoso());
        assertEquals("El ID no puede estar vacio.", resultado.getMensajeError());
    }

    @Test
    void registrarFallaConNombreVacio() {
        var resultado = funcionarioService.registrar(new Funcionario("func3", "   ", "85551111"));

        assertFalse(resultado.isExitoso());
        assertEquals("El nombre no puede estar vacio.", resultado.getMensajeError());
    }

    @Test
    void eliminarFallaSiTieneReservasFuturasActivas() {
        var resultado = funcionarioService.eliminar("func2");

        assertFalse(resultado.isExitoso());
        assertEquals("No se puede eliminar el funcionario porque tiene reservas futuras registradas.",
                resultado.getMensajeError());
    }

    @Test
    void eliminarFuncionaSiLaUnicaReservaFuturaEstaCancelada() throws IOException {
        // Reserva futura pero CANCELADA: no debe bloquear la eliminacion
        // (solo bloquean las reservas futuras y ACTIVAS a la vez).
        Files.writeString(RUTA_RESERVAS, """
                [
                  {"idReserva":"RES-000002","idFuncionario":"func2","nombreFuncionario":"Beto Soto",
                   "nombreActividad":"Reunion cancelada","fecha":"2099-01-01",
                   "horaInicio":"08:00:00","horaFin":"10:00:00","recursosReservados":[],
                   "estado":"CANCELADA"}
                ]
                """);

        var resultado = funcionarioService.eliminar("func2");
        assertTrue(resultado.isExitoso());
    }

    @Test
    void eliminarFuncionaSiLaUnicaReservaActivaYaPaso() throws IOException {
        // Reserva ACTIVA pero con fecha pasada: tampoco debe bloquear.
        Files.writeString(RUTA_RESERVAS, """
                [
                  {"idReserva":"RES-000003","idFuncionario":"func2","nombreFuncionario":"Beto Soto",
                   "nombreActividad":"Reunion pasada","fecha":"2020-01-01",
                   "horaInicio":"08:00:00","horaFin":"10:00:00","recursosReservados":[],
                   "estado":"ACTIVA"}
                ]
                """);

        var resultado = funcionarioService.eliminar("func2");
        assertTrue(resultado.isExitoso());
    }

    @Test
    void eliminarFuncionaSinReservasFuturasYBorraElUsuarioAsociado() {
        var resultado = funcionarioService.eliminar("func1");
        assertTrue(resultado.isExitoso());

        boolean sigueExistiendo = funcionarioService.listarTodos().stream()
                .anyMatch(f -> f.getId().equals("func1"));
        assertFalse(sigueExistiendo);
    }

    @Test
    void buscarPorNombreParcialEsInsensibleAMayusculas() {
        List<Funcionario> resultado = funcionarioService.buscar(null, "beto");

        assertEquals(1, resultado.size());
        assertEquals("func2", resultado.get(0).getId());
    }

    @Test
    void lanzaExcepcionSiElArchivoDeFuncionariosEstaDanado() throws IOException {
        Files.writeString(RUTA_FUNCIONARIOS, "{ esto no es una lista valida de funcionarios");

        assertThrows(RuntimeException.class, () -> funcionarioService.listarTodos());
    }
}
