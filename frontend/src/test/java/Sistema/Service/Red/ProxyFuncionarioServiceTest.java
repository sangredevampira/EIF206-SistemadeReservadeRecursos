package Sistema.Service.Red;

import Sistema.DTO.Funcionario;
import Sistema.DTO.ResultadoOperacionDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static Sistema.Service.Red.ServidorFalso.JSON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProxyFuncionarioServiceTest {

    private final ServidorFalso servidor = new ServidorFalso();

    @BeforeEach
    void antes() {
        servidor.cerrarCliente();
    }

    @AfterEach
    void despues() {
        servidor.cerrarCliente();
    }

    @Test
    void registrarEnviaFuncCrearConIdNombreYTelefonoSinReservas() throws Exception {
        servidor.conExito(null, () -> new ProxyFuncionarioService()
                .registrar(new Funcionario("555", "Ana", "8888-0000")));

        assertEquals("FUNC_CREAR", servidor.operacionRecibida());
        JsonNode datos = servidor.datosRecibidos();
        assertEquals("555", datos.path("id").asText());
        assertEquals("Ana", datos.path("nombre").asText());
        assertEquals("8888-0000", datos.path("telefono").asText());
        // el servidor rechaza propiedades desconocidas: reservas no debe viajar
        assertFalse(datos.has("reservas"));
        assertEquals(3, datos.size());
    }

    @Test
    void registrarExitosoDevuelveExitoSinMensaje() throws Exception {
        ResultadoOperacionDTO resultado = servidor.conExito(null, () -> new ProxyFuncionarioService()
                .registrar(new Funcionario("555", "Ana", "8888-0000")));

        assertTrue(resultado.isExito());
        assertNull(resultado.getMensaje());
    }

    @Test
    void registrarRechazadoDevuelveFalloConElMensaje() throws Exception {
        String mensaje = "Ya existe un usuario o funcionario con ese ID (puede estar dado de baja).";
        ResultadoOperacionDTO resultado = servidor.conFallo(mensaje, () -> new ProxyFuncionarioService()
                .registrar(new Funcionario("44444", "Ana", "8888-0000")));

        assertFalse(resultado.isExito());
        assertEquals(mensaje, resultado.getMensaje());
    }

    @Test
    void modificarEnviaFuncEditar() throws Exception {
        servidor.conExito(null, () -> new ProxyFuncionarioService()
                .modificar(new Funcionario("44444", "Kristel P.", "8888-1111")));

        assertEquals("FUNC_EDITAR", servidor.operacionRecibida());
        assertEquals("Kristel P.", servidor.datosRecibidos().path("nombre").asText());
    }

    @Test
    void eliminarEnviaFuncBajaConIdFuncionario() throws Exception {
        ResultadoOperacionDTO resultado = servidor.conExito(null,
                () -> new ProxyFuncionarioService().eliminar("555"));

        assertEquals("FUNC_BAJA", servidor.operacionRecibida());
        assertEquals("555", servidor.datosRecibidos().path("idFuncionario").asText());
        assertTrue(resultado.isExito());
    }

    @Test
    void eliminarRechazadoDevuelveFalloConElMensaje() throws Exception {
        String mensaje = "No se puede eliminar el funcionario porque tiene reservas futuras activas.";
        ResultadoOperacionDTO resultado = servidor.conFallo(mensaje,
                () -> new ProxyFuncionarioService().eliminar("44444"));

        assertFalse(resultado.isExito());
        assertEquals(mensaje, resultado.getMensaje());
    }

    @Test
    void buscarEnviaLosDosFiltros() throws Exception {
        servidor.conExito(JSON.createArrayNode(),
                () -> new ProxyFuncionarioService().buscar("44", "kris"));

        assertEquals("FUNC_BUSCAR", servidor.operacionRecibida());
        JsonNode datos = servidor.datosRecibidos();
        assertEquals("44", datos.path("idParcial").asText());
        assertEquals("kris", datos.path("nombreParcial").asText());
    }

    @Test
    void buscarOmiteLosFiltrosNull() throws Exception {
        servidor.conExito(JSON.createArrayNode(),
                () -> new ProxyFuncionarioService().buscar(null, "kris"));

        JsonNode datos = servidor.datosRecibidos();
        assertFalse(datos.has("idParcial"));
        assertEquals("kris", datos.path("nombreParcial").asText());
    }

    @Test
    void buscarRechazadoLanzaIllegalArgumentConElMensaje() throws Exception {
        IllegalArgumentException error = servidor.conFallo("No tiene permiso para esta operación.",
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ProxyFuncionarioService().buscar("4", null)));

        assertEquals("No tiene permiso para esta operación.", error.getMessage());
    }

    @Test
    void listarTodosEnviaFuncListarSinDatosYConvierteLaLista() throws Exception {
        ArrayNode lista = JSON.createArrayNode();
        lista.addObject().put("id", "123").put("nombre", "Sofía").put("telefono", "7000-0000");
        lista.addObject().put("id", "44444").put("nombre", "Kristel").put("telefono", "8888-8888");

        List<Funcionario> funcionarios = servidor.conExito(lista,
                () -> new ProxyFuncionarioService().listarTodos());

        assertEquals("FUNC_LISTAR", servidor.operacionRecibida());
        assertTrue(servidor.datosRecibidos().isNull());
        assertEquals(2, funcionarios.size());
        assertEquals("123", funcionarios.get(0).getId());
        assertEquals("Sofía", funcionarios.get(0).getNombre());
        assertEquals("8888-8888", funcionarios.get(1).getTelefono());
    }

    @Test
    void listaVaciaDevuelveListaVacia() throws Exception {
        List<Funcionario> funcionarios = servidor.conExito(JSON.createArrayNode(),
                () -> new ProxyFuncionarioService().listarTodos());

        assertTrue(funcionarios.isEmpty());
    }

    @Test
    void respuestaQueNoEsListaLanzaIllegalState() throws Exception {
        IllegalStateException error = servidor.conExito(JSON.createObjectNode().put("id", "123"),
                () -> assertThrows(IllegalStateException.class,
                        () -> new ProxyFuncionarioService().listarTodos()));

        assertEquals("El servidor no devolvió una lista válida de funcionarios.", error.getMessage());
    }

    @Test
    void elementoConPropiedadDesconocidaLanzaIllegalState() throws Exception {
        ArrayNode lista = JSON.createArrayNode();
        lista.addObject().put("id", "123").put("edad", 20);

        IllegalStateException error = servidor.conExito(lista,
                () -> assertThrows(IllegalStateException.class,
                        () -> new ProxyFuncionarioService().listarTodos()));

        assertTrue(error.getMessage().contains("funcionarios"));
    }

    @Test
    void registrarSinServidorDejaPasarIllegalState() {
        assertThrows(IllegalStateException.class, () -> new ProxyFuncionarioService()
                .registrar(new Funcionario("555", "Ana", "8888-0000")));
    }
}
