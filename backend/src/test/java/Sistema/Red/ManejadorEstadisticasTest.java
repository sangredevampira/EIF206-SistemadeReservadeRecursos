package Sistema.Red;

import Sistema.DTO.EstadisticaCategoriaDTO;
import Sistema.DTO.EstadoReserva;
import Sistema.DTO.ReservaDTO;
import Sistema.Logica.EstadisticasRecursosLogica;
import Sistema.Service.EstadisticasActividadesService;
import Sistema.Service.EstadisticasRecursosService;
import Sistema.Service.ReservaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static Sistema.Red.ApoyoManejadores.DATOS_INVALIDOS;
import static Sistema.Red.ApoyoManejadores.SIN_SESION;
import static Sistema.Red.ApoyoManejadores.assertError;
import static Sistema.Red.ApoyoManejadores.assertExito;
import static Sistema.Red.ApoyoManejadores.enviar;
import static Sistema.Red.ApoyoManejadores.funcionario;
import static Sistema.Red.ApoyoManejadores.sinSesion;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pruebas unitarias del manejador, sin red ni MySQL. */
class ManejadorEstadisticasTest {

    private static final LocalDate DESDE = LocalDate.of(2026, 11, 1);
    private static final LocalDate HASTA = LocalDate.of(2026, 11, 30);

    private EstadisticasRecursosServiceFalso recursosService;
    private EstadisticasActividadesService actividadesService;
    private ReservaServiceFalso reservaService;
    private DespachadorPeticiones despachador;

    @BeforeEach
    void preparar() {
        recursosService = new EstadisticasRecursosServiceFalso();
        actividadesService = new EstadisticasActividadesService();
        reservaService = new ReservaServiceFalso();
        despachador = new DespachadorPeticiones();

        new ManejadorEstadisticas(
                recursosService,
                actividadesService,
                reservaService
        ).registrarEn(despachador);
    }

    @Test
    void registraLasDosOperaciones() {
        MensajeRed recursos = consultar(Operaciones.EST_RECURSOS);
        MensajeRed actividades = consultar(Operaciones.EST_ACTIVIDADES);

        assertExito(recursos);
        assertExito(actividades);
        assertEquals(1, recursosService.llamadas);
        assertEquals(1, reservaService.llamadas);
    }

    @Test
    void recursosDevuelveSolamenteLaListaDeEstadisticas() {
        recursosService.resultado = EstadisticasRecursosLogica.ResultadoEstadisticas.exito(
                List.of(
                        new EstadisticaCategoriaDTO("Laboratorio", 4),
                        new EstadisticaCategoriaDTO("Equipo audiovisual", 2)
                )
        );

        MensajeRed respuesta = consultar(Operaciones.EST_RECURSOS);

        assertExito(respuesta);
        assertTrue(respuesta.getDatos().isArray());
        assertEquals(2, respuesta.getDatos().size());
        assertEquals("Laboratorio", respuesta.getDatos().get(0).path("categoria").asText());
        assertEquals(4, respuesta.getDatos().get(0).path("cantidad").asInt());
        assertEquals(DESDE, recursosService.desde);
        assertEquals(HASTA, recursosService.hasta);
    }

    @Test
    void actividadesBuscaReservasYDelegaElConteoSemanal() {
        reservaService.reservas = List.of(
                reserva("R1", LocalDate.of(2026, 11, 1), EstadoReserva.ACTIVA),
                reserva("R2", LocalDate.of(2026, 11, 2), EstadoReserva.ACTIVA),
                reserva("R3", LocalDate.of(2026, 11, 3), EstadoReserva.CANCELADA)
        );

        MensajeRed respuesta = consultar(Operaciones.EST_ACTIVIDADES);

        assertExito(respuesta);
        assertTrue(respuesta.getDatos().isObject());
        assertEquals(1, respuesta.getDatos().path("2026-10-26").asInt());
        assertEquals(1, respuesta.getDatos().path("2026-11-02").asInt());
        assertEquals(0, respuesta.getDatos().path("2026-11-09").asInt());
        assertEquals(DESDE, reservaService.desde);
        assertEquals(HASTA, reservaService.hasta);
    }

    @Test
    void periodosSinResultadosConservanLasEstructurasDelContrato() {
        recursosService.resultado = EstadisticasRecursosLogica.ResultadoEstadisticas.exito(List.of());
        reservaService.reservas = List.of();

        MensajeRed recursos = consultar(Operaciones.EST_RECURSOS);
        MensajeRed actividades = enviar(
                despachador,
                Operaciones.EST_ACTIVIDADES,
                Map.of("desde", "2026-11-02", "hasta", "2026-11-08"),
                funcionario()
        );

        assertExito(recursos);
        assertTrue(recursos.getDatos().isArray());
        assertTrue(recursos.getDatos().isEmpty());
        assertExito(actividades);
        assertEquals(0, actividades.getDatos().path("2026-11-02").asInt());
    }

    @Test
    void validaCamposObligatoriosTextualesYFechasIso() {
        assertError(DATOS_INVALIDOS, enviar(
                despachador,
                Operaciones.EST_RECURSOS,
                Map.of("hasta", "2026-11-30"),
                funcionario()
        ));
        assertError(DATOS_INVALIDOS, enviar(
                despachador,
                Operaciones.EST_ACTIVIDADES,
                Map.of("desde", 20261101, "hasta", "2026-11-30"),
                funcionario()
        ));
        assertError(DATOS_INVALIDOS, enviar(
                despachador,
                Operaciones.EST_RECURSOS,
                Map.of("desde", "01/11/2026", "hasta", "2026-11-30"),
                funcionario()
        ));

        assertEquals(0, recursosService.llamadas);
        assertEquals(0, reservaService.llamadas);
    }

    @ParameterizedTest
    @ValueSource(strings = {Operaciones.EST_RECURSOS, Operaciones.EST_ACTIVIDADES})
    void rechazaPeriodoInvertido(String operacion) {
        MensajeRed respuesta = enviar(
                despachador,
                operacion,
                Map.of("desde", "2026-12-01", "hasta", "2026-11-30"),
                funcionario()
        );

        assertError(
                "La fecha 'desde' no puede ser posterior a la fecha 'hasta'.",
                respuesta
        );
    }

    @Test
    void propagaElFalloDeNegocioDeRecursos() {
        recursosService.resultado = EstadisticasRecursosLogica.ResultadoEstadisticas.fallo(
                "No se pudo calcular el período solicitado."
        );

        assertError(
                "No se pudo calcular el período solicitado.",
                consultar(Operaciones.EST_RECURSOS)
        );
    }

    @Test
    void propagaElFalloDeNegocioDeReservas() {
        reservaService.error = new IllegalArgumentException("Período no disponible.");

        assertError("Período no disponible.", consultar(Operaciones.EST_ACTIVIDADES));
    }

    @Test
    void ocultaDetallesInternosDeErroresInesperados() {
        reservaService.error = new IllegalStateException("SELECT secreto FROM reservas");

        MensajeRed respuesta = consultar(Operaciones.EST_ACTIVIDADES);

        assertError("Error interno del servidor. Intente de nuevo.", respuesta);
        assertFalse(respuesta.getMensaje().contains("SELECT"));
    }

    @ParameterizedTest
    @ValueSource(strings = {Operaciones.EST_RECURSOS, Operaciones.EST_ACTIVIDADES})
    void exigeSesionAutenticada(String operacion) {
        MensajeRed respuesta = enviar(despachador, operacion, periodo(), sinSesion());

        assertError(SIN_SESION, respuesta);
        assertEquals(0, recursosService.llamadas);
        assertEquals(0, reservaService.llamadas);
    }

    private MensajeRed consultar(String operacion) {
        return enviar(despachador, operacion, periodo(), funcionario());
    }

    private Map<String, String> periodo() {
        Map<String, String> periodo = new LinkedHashMap<>();
        periodo.put("desde", DESDE.toString());
        periodo.put("hasta", HASTA.toString());
        return periodo;
    }

    private static ReservaDTO reserva(String id, LocalDate fecha, EstadoReserva estado) {
        return new ReservaDTO(
                id,
                "F1",
                "Sofía",
                "Actividad",
                fecha,
                LocalTime.of(8, 0),
                LocalTime.of(9, 0),
                List.of(),
                estado
        );
    }

    private static final class EstadisticasRecursosServiceFalso
            extends EstadisticasRecursosService {

        private EstadisticasRecursosLogica.ResultadoEstadisticas resultado =
                EstadisticasRecursosLogica.ResultadoEstadisticas.exito(List.of());
        private int llamadas;
        private LocalDate desde;
        private LocalDate hasta;

        @Override
        public EstadisticasRecursosLogica.ResultadoEstadisticas calcular(
                LocalDate fechaDesde,
                LocalDate fechaHasta
        ) {
            llamadas++;
            desde = fechaDesde;
            hasta = fechaHasta;
            return resultado;
        }
    }

    private static final class ReservaServiceFalso extends ReservaService {

        private List<ReservaDTO> reservas = List.of();
        private RuntimeException error;
        private int llamadas;
        private LocalDate desde;
        private LocalDate hasta;

        @Override
        public List<ReservaDTO> buscarPorPeriodo(LocalDate fechaDesde, LocalDate fechaHasta) {
            llamadas++;
            desde = fechaDesde;
            hasta = fechaHasta;

            if (error != null) {
                throw error;
            }

            return reservas;
        }
    }
}
