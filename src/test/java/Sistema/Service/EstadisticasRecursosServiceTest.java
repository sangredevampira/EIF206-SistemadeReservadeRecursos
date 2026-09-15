package Sistema.Service;

import Sistema.DTO.EstadisticaCategoriaDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias de EstadisticasRecursosService. Internamente delega en
 * ReservaLogica.buscarPorPeriodo, que lee reservas.json, asi que ese archivo
 * se respalda y se restaura en cada prueba.
 */
class EstadisticasRecursosServiceTest {

    private static final Path RUTA_RESERVAS = Paths.get("src/data/reservas.json");

    private String backupReservas;

    private EstadisticasRecursosService estadisticasRecursosService;

    @BeforeEach
    void prepararDatosDePrueba() throws IOException {
        backupReservas = Files.exists(RUTA_RESERVAS) ? Files.readString(RUTA_RESERVAS) : null;

        // RES-000001: activa, dentro del rango de marzo -> cuenta.
        // RES-000002: cancelada, dentro del rango de marzo -> NO debe contarse.
        // RES-000003: activa, fuera del rango de marzo (abril) -> no cuenta en ese rango.
        Files.writeString(RUTA_RESERVAS, """
                [
                  {"idReserva":"RES-000001","idFuncionario":"func1","nombreFuncionario":"Ana Perez",
                   "nombreActividad":"Reunion","fecha":"2026-03-10",
                   "horaInicio":"08:00:00","horaFin":"09:00:00",
                   "recursosReservados":[{"id":"REC-0001","descripcion":"Sala 1",
                     "categoria":{"id":"CAT-000001","descripcion":"Sala para 10 personas"}}],
                   "estado":"ACTIVA"},
                  {"idReserva":"RES-000002","idFuncionario":"func1","nombreFuncionario":"Ana Perez",
                   "nombreActividad":"Capacitacion","fecha":"2026-03-12",
                   "horaInicio":"10:00:00","horaFin":"11:00:00",
                   "recursosReservados":[{"id":"REC-0002","descripcion":"Laptop 1",
                     "categoria":{"id":"CAT-000002","descripcion":"Laptop windows 11"}}],
                   "estado":"CANCELADA"},
                  {"idReserva":"RES-000003","idFuncionario":"func1","nombreFuncionario":"Ana Perez",
                   "nombreActividad":"Otra reunion","fecha":"2026-04-01",
                   "horaInicio":"08:00:00","horaFin":"09:00:00",
                   "recursosReservados":[{"id":"REC-0003","descripcion":"Sala 2",
                     "categoria":{"id":"CAT-000001","descripcion":"Sala para 10 personas"}}],
                   "estado":"ACTIVA"}
                ]
                """);

        estadisticasRecursosService = new EstadisticasRecursosService();
    }

    @AfterEach
    void restaurarDatosOriginales() throws IOException {
        if (backupReservas == null) {
            Files.deleteIfExists(RUTA_RESERVAS);
        } else {
            Files.writeString(RUTA_RESERVAS, backupReservas);
        }
    }

    @Test
    void calcularCuentaSoloReservasActivasDentroDelPeriodo() {
        var resultado = estadisticasRecursosService.calcular(
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));

        assertTrue(resultado.isExitoso());
        List<EstadisticaCategoriaDTO> datos = resultado.getDatos();
        assertEquals(1, datos.size());
        assertEquals("Sala para 10 personas", datos.get(0).getCategoria());
        assertEquals(1, datos.get(0).getCantidad()); // solo RES-000001; RES-000002 esta cancelada
    }

    @Test
    void calcularAgrupaPorCategoriaEnUnPeriodoMasAmplio() {
        var resultado = estadisticasRecursosService.calcular(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        assertTrue(resultado.isExitoso());
        List<EstadisticaCategoriaDTO> datos = resultado.getDatos();
        assertEquals(1, datos.size());
        assertEquals("Sala para 10 personas", datos.get(0).getCategoria());
        assertEquals(2, datos.get(0).getCantidad()); // RES-000001 + RES-000003
    }

    @Test
    void calcularFallaSiFechaDesdeEsPosteriorAFechaHasta() {
        var resultado = estadisticasRecursosService.calcular(
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 1, 1));

        assertFalse(resultado.isExitoso());
        assertEquals("La fecha 'desde' no puede ser posterior a la fecha 'hasta'.", resultado.getMensajeError());
    }

    @Test
    void calcularFallaSiFaltaAlgunaFecha() {
        var resultado = estadisticasRecursosService.calcular(null, LocalDate.of(2026, 1, 1));

        assertFalse(resultado.isExitoso());
        assertEquals("Debe indicar ambas fechas del período.", resultado.getMensajeError());
    }
}
