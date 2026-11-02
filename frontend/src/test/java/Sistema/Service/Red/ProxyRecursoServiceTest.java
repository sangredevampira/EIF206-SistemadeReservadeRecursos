package Sistema.Service.Red;

import Sistema.DTO.CategoriaDTO;
import Sistema.DTO.RecursoDTO;
import Sistema.DTO.ResultadoOperacionDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

class ProxyRecursoServiceTest {

    private final ServidorFalso servidor = new ServidorFalso();

    @BeforeEach
    void antes() {
        servidor.cerrarCliente();
    }

    @AfterEach
    void despues() {
        servidor.cerrarCliente();
    }

    // --- crear, editar, eliminar ---

    @Test
    void registrarEnviaRecCrearConLaCategoriaAnidada() throws Exception {
        RecursoDTO recurso = new RecursoDTO("816243", "Laptop HP", new CategoriaDTO("CAT-000001", "Laptop"));

        ResultadoOperacionDTO resultado = servidor.conExito(null,
                () -> new ProxyRecursoService().registrarRecurso(recurso));

        assertEquals("REC_CREAR", servidor.operacionRecibida());
        JsonNode datos = servidor.datosRecibidos();
        assertEquals("816243", datos.path("id").asText());
        assertEquals("Laptop HP", datos.path("descripcion").asText());
        assertEquals("CAT-000001", datos.path("categoria").path("id").asText());
        assertEquals("Laptop", datos.path("categoria").path("descripcion").asText());
        assertTrue(resultado.isExito());
        assertNull(resultado.getMensaje());
    }

    @Test
    void registrarSinCategoriaEnviaCategoriaNullYDevuelveElFallo() throws Exception {
        ResultadoOperacionDTO resultado = servidor.conFallo("Debe seleccionar una categoría",
                () -> new ProxyRecursoService().registrarRecurso(new RecursoDTO("816243", "Laptop HP", null)));

        assertTrue(servidor.datosRecibidos().path("categoria").isNull());
        assertFalse(resultado.isExito());
        assertEquals("Debe seleccionar una categoría", resultado.getMensaje());
    }

    @Test
    void modificarEnviaRecEditar() throws Exception {
        servidor.conExito(null, () -> new ProxyRecursoService().modificarRecurso(
                new RecursoDTO("118554", "Laptop Dell", new CategoriaDTO("CAT-000001", "Laptop"))));

        assertEquals("REC_EDITAR", servidor.operacionRecibida());
        assertEquals("Laptop Dell", servidor.datosRecibidos().path("descripcion").asText());
    }

    @Test
    void eliminarEnviaRecEliminarConIdRecurso() throws Exception {
        ResultadoOperacionDTO resultado = servidor.conExito(null,
                () -> new ProxyRecursoService().eliminarRecurso("118554"));

        assertEquals("REC_ELIMINAR", servidor.operacionRecibida());
        assertEquals("118554", servidor.datosRecibidos().path("idRecurso").asText());
        assertTrue(resultado.isExito());
    }

    @Test
    void eliminarRechazadoDevuelveFalloConElMensaje() throws Exception {
        String mensaje = "No se puede eliminar el recurso porque tiene reservas futuras activas";
        ResultadoOperacionDTO resultado = servidor.conFallo(mensaje,
                () -> new ProxyRecursoService().eliminarRecurso("118554"));

        assertFalse(resultado.isExito());
        assertEquals(mensaje, resultado.getMensaje());
    }

    // --- buscar por ID ---

    @Test
    void buscarRecursoIdEnviaRecBuscarYConvierteElRecurso() throws Exception {
        RecursoDTO recurso = servidor.conExito(recursoJson("118554", "Laptop Dell"),
                () -> new ProxyRecursoService().buscarRecursoID("118554"));

        assertEquals("REC_BUSCAR", servidor.operacionRecibida());
        assertEquals("118554", servidor.datosRecibidos().path("idRecurso").asText());
        assertEquals("118554", recurso.getId());
        assertEquals("Laptop Dell", recurso.getDescripcion());
        assertEquals("CAT-000001", recurso.getCategoria().getId());
        assertEquals("Laptop", recurso.getCategoria().getDescripcion());
    }

    @Test
    void buscarRecursoIdInexistenteLanzaIllegalArgumentConElMensaje() throws Exception {
        IllegalArgumentException error = servidor.conFallo("El recurso solicitado no fue encontrado",
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ProxyRecursoService().buscarRecursoID("999999")));

        assertEquals("El recurso solicitado no fue encontrado", error.getMessage());
    }

    @Test
    void buscarRecursoIdConDatosNullEsRespuestaInvalida() throws Exception {
        IllegalStateException error = servidor.conExito(null,
                () -> assertThrows(IllegalStateException.class,
                        () -> new ProxyRecursoService().buscarRecursoID("118554")));

        assertEquals("El servidor no devolvió el recurso solicitado.", error.getMessage());
    }

    // --- listas (REC_LISTAR) ---

    @Test
    void filtrarPorCategoriaEnviaSoloIdCategoria() throws Exception {
        servidor.conExito(JSON.createArrayNode(),
                () -> new ProxyRecursoService().filtrarRecursos("CAT-000001"));

        assertEquals("REC_LISTAR", servidor.operacionRecibida());
        JsonNode datos = servidor.datosRecibidos();
        assertEquals("CAT-000001", datos.path("idCategoria").asText());
        assertEquals(1, datos.size());
    }

    @Test
    void filtrarConCategoriaYDescripcionEnviaLosDos() throws Exception {
        servidor.conExito(JSON.createArrayNode(),
                () -> new ProxyRecursoService().filtrarRecursos("CAT-000001", "dell"));

        JsonNode datos = servidor.datosRecibidos();
        assertEquals("CAT-000001", datos.path("idCategoria").asText());
        assertEquals("dell", datos.path("descripcion").asText());
    }

    @Test
    void filtrarOmiteLaCategoriaNull() throws Exception {
        servidor.conExito(JSON.createArrayNode(),
                () -> new ProxyRecursoService().filtrarRecursos(null, "lap"));

        JsonNode datos = servidor.datosRecibidos();
        assertFalse(datos.has("idCategoria"));
        assertEquals("lap", datos.path("descripcion").asText());
    }

    @Test
    void listarRecursosEnviaRecListarSinDatosYConvierteLaLista() throws Exception {
        ArrayNode lista = JSON.createArrayNode();
        lista.add(recursoJson("118554", "Laptop Dell"));
        lista.add(recursoJson("891234", "Monitor Dell 27 pulgadas"));

        List<RecursoDTO> recursos = servidor.conExito(lista,
                () -> new ProxyRecursoService().listarRecursos());

        assertEquals("REC_LISTAR", servidor.operacionRecibida());
        assertTrue(servidor.datosRecibidos().isNull());
        assertEquals(2, recursos.size());
        assertEquals("891234", recursos.get(1).getId());
        assertEquals("CAT-000001", recursos.get(1).getCategoria().getId());
    }

    @Test
    void respuestaQueNoEsListaLanzaIllegalState() throws Exception {
        IllegalStateException error = servidor.conExito(recursoJson("118554", "Laptop Dell"),
                () -> assertThrows(IllegalStateException.class,
                        () -> new ProxyRecursoService().listarRecursos()));

        assertEquals("El servidor no devolvió una lista válida de recursos.", error.getMessage());
    }

    // --- nuevo ID ---

    @Test
    void generarNuevoIdEnviaRecNuevoIdSinDatosYDevuelveElTexto() throws Exception {
        String id = servidor.conExito(JSON.getNodeFactory().textNode("816243"),
                () -> new ProxyRecursoService().generarNuevoId());

        assertEquals("REC_NUEVO_ID", servidor.operacionRecibida());
        assertTrue(servidor.datosRecibidos().isNull());
        assertEquals("816243", id);
    }

    @Test
    void generarNuevoIdConDatosNullEsRespuestaInvalida() throws Exception {
        IllegalStateException error = servidor.conExito(null,
                () -> assertThrows(IllegalStateException.class,
                        () -> new ProxyRecursoService().generarNuevoId()));

        assertEquals("El servidor no devolvió un ID nuevo para el recurso.", error.getMessage());
    }

    @Test
    void generarNuevoIdSinPermisoLanzaIllegalArgument() throws Exception {
        IllegalArgumentException error = servidor.conFallo("No tiene permiso para esta operación.",
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ProxyRecursoService().generarNuevoId()));

        assertEquals("No tiene permiso para esta operación.", error.getMessage());
    }

    @Test
    void eliminarSinServidorDejaPasarIllegalState() {
        assertThrows(IllegalStateException.class,
                () -> new ProxyRecursoService().eliminarRecurso("118554"));
    }

    private static ObjectNode recursoJson(String id, String descripcion) {
        ObjectNode recurso = JSON.createObjectNode().put("id", id).put("descripcion", descripcion);
        recurso.putObject("categoria").put("id", "CAT-000001").put("descripcion", "Laptop");
        return recurso;
    }
}
