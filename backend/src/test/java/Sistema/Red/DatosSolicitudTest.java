package Sistema.Red;

import Sistema.DTO.CategoriaDTO;
import Sistema.DTO.Funcionario;
import Sistema.DTO.RecursoDTO;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Pruebas de unidad del ayudante que leen los manejadores de Derek. */
class DatosSolicitudTest {

    private static JsonNode nodo(Object valor) {
        return JsonRed.aNodo(valor);
    }

    private static void assertInvalidos(Runnable lectura) {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, lectura::run);
        assertEquals("Los datos de la solicitud no son válidos.", ex.getMessage());
    }

    // --- texto ---

    @Test
    void textoDevuelveElValorDelCampo() {
        assertEquals("CAT-000001", DatosSolicitud.texto(nodo(Map.of("idCategoria", "CAT-000001")), "idCategoria"));
    }

    @Test
    void textoNoRecortaNiCambiaElValor() {
        // recortar es trabajo de la Logica
        assertEquals("  44 ", DatosSolicitud.texto(nodo(Map.of("idFuncionario", "  44 ")), "idFuncionario"));
    }

    @Test
    void textoSinDatosOSinElCampoOConNullNoEsValido() {
        Map<String, Object> conNull = new HashMap<>();
        conNull.put("idRecurso", null);

        assertInvalidos(() -> DatosSolicitud.texto(null, "idRecurso"));
        assertInvalidos(() -> DatosSolicitud.texto(nodo(Map.of("otro", "x")), "idRecurso"));
        assertInvalidos(() -> DatosSolicitud.texto(nodo(conNull), "idRecurso"));
    }

    @Test
    void textoConUnObjetoOUnaListaNoEsValido() {
        assertInvalidos(() -> DatosSolicitud.texto(nodo(Map.of("idRecurso", Map.of("a", 1))), "idRecurso"));
        assertInvalidos(() -> DatosSolicitud.texto(nodo(Map.of("idRecurso", List.of("1"))), "idRecurso"));
    }

    // --- textoOpcional ---

    @Test
    void textoOpcionalDevuelveNullSiFaltaOEsNull() {
        Map<String, Object> conNull = new HashMap<>();
        conNull.put("descripcion", null);

        assertNull(DatosSolicitud.textoOpcional(null, "descripcion"));
        assertNull(DatosSolicitud.textoOpcional(nodo(Map.of()), "descripcion"));
        assertNull(DatosSolicitud.textoOpcional(nodo(conNull), "descripcion"));
        assertEquals("lap", DatosSolicitud.textoOpcional(nodo(Map.of("descripcion", "lap")), "descripcion"));
    }

    @Test
    void textoOpcionalConUnObjetoNoEsValido() {
        assertInvalidos(() -> DatosSolicitud.textoOpcional(nodo(Map.of("descripcion", Map.of())), "descripcion"));
    }

    // --- objeto ---

    @Test
    void objetoConvierteLosDatosEnElDto() {
        Funcionario leido = DatosSolicitud.objeto(
                nodo(new Funcionario("44444", "Ana Perez", "8888-9999")), Funcionario.class);

        assertEquals("44444", leido.getId());
        assertEquals("Ana Perez", leido.getNombre());
        assertEquals("8888-9999", leido.getTelefono());
    }

    @Test
    void objetoLeeLaCategoriaAnidadaDeUnRecurso() {
        RecursoDTO leido = DatosSolicitud.objeto(nodo(Map.of(
                "id", "123456", "descripcion", "Proyector",
                "categoria", Map.of("id", "CAT-000001", "descripcion", "Salas"))), RecursoDTO.class);

        assertEquals("123456", leido.getId());
        assertEquals("CAT-000001", leido.getCategoria().getId());
    }

    @Test
    void objetoSinDatosOQueNoEsUnObjetoNoEsValido() {
        assertInvalidos(() -> DatosSolicitud.objeto(null, CategoriaDTO.class));
        assertInvalidos(() -> DatosSolicitud.objeto(nodo(List.of("x")), CategoriaDTO.class));
        assertInvalidos(() -> DatosSolicitud.objeto(nodo("texto"), CategoriaDTO.class));
    }

    @Test
    void objetoConUnaPropiedadQueElDtoNoTieneNoEsValido() {
        assertInvalidos(() -> DatosSolicitud.objeto(
                nodo(Map.of("descripcion", "Salas", "color", "rojo")), CategoriaDTO.class));
    }

    @Test
    void objetoIgnoraLasReservasDeUnFuncionario() {
        // reservas tiene @JsonIgnore: si llegara en el JSON se ignora, no falla
        Funcionario leido = DatosSolicitud.objeto(nodo(Map.of(
                "id", "44444", "nombre", "Ana", "telefono", "8888", "reservas", List.of())), Funcionario.class);

        assertEquals("44444", leido.getId());
        assertTrue(leido.getReservas().isEmpty());
    }
}
