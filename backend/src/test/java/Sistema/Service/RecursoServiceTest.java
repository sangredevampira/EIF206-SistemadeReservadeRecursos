package Sistema.Service;

import Sistema.DTO.CategoriaDTO;
import Sistema.DTO.RecursoDTO;
import Sistema.Logica.RecursoLogica;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class RecursoServiceTest {

    /** NOTA:
     * Fake de RecursoLogica: sustituye a la lógica real. No toca el archivo
     * JSON. Cada prueba configura qué debe "responder" y luego revisa
     * qué parámetro recibió, para confirmar que RecursoService delega bien.
     */

    static class RecursoLogicaFake extends RecursoLogica {

        // las respuestas que queremos que devuelva la logica
        int valorARetornarRegistrar;
        String idARetornarGenerar;
        RecursoDTO recursoARetornarBuscar;
        List<RecursoDTO> listaARetornar;
        boolean valorARetornarModificar;
        boolean valorARetornarEliminar;
        RuntimeException excepcionARetornar;

        // los parámetros que recibió la logica
        RecursoDTO parametroRecibidoRegistrar;
        String parametroRecibidoBuscar;
        String parametroRecibidoEliminar;

        @Override
        public int registrarRecurso(RecursoDTO nuevoRecurso) {
            parametroRecibidoRegistrar = nuevoRecurso;
            if (excepcionARetornar != null) throw excepcionARetornar;
            return valorARetornarRegistrar;
        }

        @Override
        public String generarNuevoId() { return idARetornarGenerar; }

        @Override
        public RecursoDTO buscarRecursoID(String id) {
            parametroRecibidoBuscar = id;
            return recursoARetornarBuscar;
        }

        @Override
        public List<RecursoDTO> filtrarRecursos(String idCategoria) { return listaARetornar; }

        @Override
        public List<RecursoDTO> listarRecursos() { return listaARetornar; }

        @Override
        public boolean modificarRecurso(RecursoDTO recurso) { return valorARetornarModificar; }

        @Override
        public boolean eliminarRecurso(String id) {
            parametroRecibidoEliminar = id;
            if (excepcionARetornar != null) throw excepcionARetornar;
            return valorARetornarEliminar;
        }
    }

        // pruebas

        @Test
        void registrarRecursoLlamaALogica() {
            RecursoLogicaFake fake = new RecursoLogicaFake();
            fake.valorARetornarRegistrar = 1;
            RecursoService service = new RecursoService(fake);
            RecursoDTO nuevo = new RecursoDTO("100001", "Proyector Epson",
                    new CategoriaDTO("CAT-000001", "Salas"));

            int resultado = service.registrarRecurso(nuevo);

            assertEquals(1, resultado);
            assertEquals(nuevo, fake.parametroRecibidoRegistrar);
        }

        @Test
        void registrarRecursoCuandoLaLogicaLanzaExcepcionSeLaPropagaSinAtraparla() {
            RecursoLogicaFake fake = new RecursoLogicaFake();
            fake.excepcionARetornar = new IllegalArgumentException("La descripción no puede estar vacía");
            RecursoService service = new RecursoService(fake);
            RecursoDTO invalido = new RecursoDTO(null, "", null);

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> service.registrarRecurso(invalido)
            );
            assertEquals("La descripción no puede estar vacía", ex.getMessage());
        }

        @Test
        void generarNuevoIdDelegaEnLaLogica() {
            RecursoLogicaFake fake = new RecursoLogicaFake();
            fake.idARetornarGenerar = "482913";
            RecursoService service = new RecursoService(fake);

            assertEquals("482913", service.generarNuevoId());
        }

        @Test
        void buscarRecursoIDDelegaEnLaLogicaYDevuelveElRecursoEncontrado() {
            RecursoLogicaFake fake = new RecursoLogicaFake();
            RecursoDTO esperado = new RecursoDTO("100001", "Proyector Epson",
                    new CategoriaDTO("CAT-000001", "Salas"));
            fake.recursoARetornarBuscar = esperado;
            RecursoService service = new RecursoService(fake);

            RecursoDTO resultado = service.buscarRecursoID("100001");

            assertEquals(esperado, resultado);
            assertEquals("100001", fake.parametroRecibidoBuscar);
        }

        @Test
        void listarRecursosDelegaEnLaLogica() {
            RecursoLogicaFake fake = new RecursoLogicaFake();
            List<RecursoDTO> todos = List.of(new RecursoDTO("100001", "Proyector", null));
            fake.listaARetornar = todos;
            RecursoService service = new RecursoService(fake);

            assertEquals(todos, service.listarRecursos());
        }

        @Test
        void modificarRecursoDelegaEnLaLogicaYDevuelveSuResultado() {
            RecursoLogicaFake fake = new RecursoLogicaFake();
            fake.valorARetornarModificar = true;
            RecursoService service = new RecursoService(fake);

            assertTrue(service.modificarRecurso(new RecursoDTO("100001", "Proyector 4K", null)));
        }

        @Test
        void eliminarRecursoCuandoTieneReservasFuturasPropagaLaExcepcionDeLaLogica() {
            RecursoLogicaFake fake = new RecursoLogicaFake();
            fake.excepcionARetornar = new IllegalArgumentException(
                    "No se puede eliminar el recurso porque tiene reservas futuras activas");
            RecursoService service = new RecursoService(fake);

            assertThrows(IllegalArgumentException.class, () -> service.eliminarRecurso("100001"));
        }

        @Test
        void eliminarRecursoSinReservasFuturasDevuelveTrue() {
            RecursoLogicaFake fake = new RecursoLogicaFake();
            fake.valorARetornarEliminar = true;
            RecursoService service = new RecursoService(fake);

            assertTrue(service.eliminarRecurso("100002"));
            assertEquals("100002", fake.parametroRecibidoEliminar);
        }
}