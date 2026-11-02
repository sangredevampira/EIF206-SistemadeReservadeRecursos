package Sistema.Service.Red;

import Sistema.DTO.CategoriaDTO;
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

class ProxyCategoriaServiceTest {

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
    void registrarEnviaCatCrearConDescripcionYIdNull() throws Exception {
        ResultadoOperacionDTO resultado = servidor.conExito(null,
                () -> new ProxyCategoriaService().registrar(new CategoriaDTO(null, "Proyector")));

        assertEquals("CAT_CREAR", servidor.operacionRecibida());
        JsonNode datos = servidor.datosRecibidos();
        assertEquals("Proyector", datos.path("descripcion").asText());
        assertTrue(datos.path("id").isNull());
        assertTrue(resultado.isExito());
        assertNull(resultado.getMensaje());
    }

    @Test
    void registrarRechazadoDevuelveFalloConElMensaje() throws Exception {
        ResultadoOperacionDTO resultado = servidor.conFallo("Ya existe una categoria con esa descripcion.",
                () -> new ProxyCategoriaService().registrar(new CategoriaDTO(null, "LAPTOP")));

        assertFalse(resultado.isExito());
        assertEquals("Ya existe una categoria con esa descripcion.", resultado.getMensaje());
    }

    @Test
    void modificarEnviaCatEditarConIdYDescripcion() throws Exception {
        servidor.conExito(null,
                () -> new ProxyCategoriaService().modificar(new CategoriaDTO("CAT-000001", "Laptops")));

        assertEquals("CAT_EDITAR", servidor.operacionRecibida());
        JsonNode datos = servidor.datosRecibidos();
        assertEquals("CAT-000001", datos.path("id").asText());
        assertEquals("Laptops", datos.path("descripcion").asText());
        assertEquals(2, datos.size());
    }

    @Test
    void eliminarEnviaCatEliminarConIdCategoria() throws Exception {
        ResultadoOperacionDTO resultado = servidor.conExito(null,
                () -> new ProxyCategoriaService().eliminar("CAT-000008"));

        assertEquals("CAT_ELIMINAR", servidor.operacionRecibida());
        assertEquals("CAT-000008", servidor.datosRecibidos().path("idCategoria").asText());
        assertTrue(resultado.isExito());
    }

    @Test
    void eliminarRechazadoDevuelveFalloConElMensaje() throws Exception {
        String mensaje = "No se puede eliminar la categoria porque tiene recursos asociados.";
        ResultadoOperacionDTO resultado = servidor.conFallo(mensaje,
                () -> new ProxyCategoriaService().eliminar("CAT-000001"));

        assertFalse(resultado.isExito());
        assertEquals(mensaje, resultado.getMensaje());
    }

    @Test
    void buscarEnviaCatBuscarConDescripcion() throws Exception {
        servidor.conExito(JSON.createArrayNode(), () -> new ProxyCategoriaService().buscar("lap"));

        assertEquals("CAT_BUSCAR", servidor.operacionRecibida());
        assertEquals("lap", servidor.datosRecibidos().path("descripcion").asText());
    }

    @Test
    void buscarConNullOmiteElFiltro() throws Exception {
        servidor.conExito(JSON.createArrayNode(), () -> new ProxyCategoriaService().buscar(null));

        JsonNode datos = servidor.datosRecibidos();
        assertTrue(datos.isObject());
        assertFalse(datos.has("descripcion"));
    }

    @Test
    void buscarConTextoVacioLoEnvia() throws Exception {
        servidor.conExito(JSON.createArrayNode(), () -> new ProxyCategoriaService().buscar(""));

        assertTrue(servidor.datosRecibidos().has("descripcion"));
        assertEquals("", servidor.datosRecibidos().path("descripcion").asText());
    }

    @Test
    void listarTodosEnviaCatListarSinDatosYConvierteLaLista() throws Exception {
        ArrayNode lista = JSON.createArrayNode();
        lista.addObject().put("id", "CAT-000001").put("descripcion", "Laptop");
        lista.addObject().put("id", "CAT-000002").put("descripcion", "Cámara");

        List<CategoriaDTO> categorias = servidor.conExito(lista,
                () -> new ProxyCategoriaService().listarTodos());

        assertEquals("CAT_LISTAR", servidor.operacionRecibida());
        assertTrue(servidor.datosRecibidos().isNull());
        assertEquals(2, categorias.size());
        assertEquals("CAT-000001", categorias.get(0).getId());
        assertEquals("Cámara", categorias.get(1).getDescripcion());
    }

    @Test
    void listaVaciaDevuelveListaVacia() throws Exception {
        List<CategoriaDTO> categorias = servidor.conExito(JSON.createArrayNode(),
                () -> new ProxyCategoriaService().listarTodos());

        assertTrue(categorias.isEmpty());
    }

    @Test
    void respuestaQueNoEsListaLanzaIllegalState() throws Exception {
        IllegalStateException error = servidor.conExito(null,
                () -> assertThrows(IllegalStateException.class,
                        () -> new ProxyCategoriaService().listarTodos()));

        assertEquals("El servidor no devolvió una lista válida de categorías.", error.getMessage());
    }

    @Test
    void listarRechazadoLanzaIllegalArgumentConElMensaje() throws Exception {
        IllegalArgumentException error = servidor.conFallo("Error interno del servidor. Intente de nuevo.",
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ProxyCategoriaService().listarTodos()));

        assertEquals("Error interno del servidor. Intente de nuevo.", error.getMessage());
    }
}
