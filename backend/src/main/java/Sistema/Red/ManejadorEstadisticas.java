package Sistema.Red;

import Sistema.DTO.ReservaDTO;
import Sistema.Logica.EstadisticasRecursosLogica;
import Sistema.Service.EstadisticasActividadesService;
import Sistema.Service.EstadisticasRecursosService;
import Sistema.Service.ReservaService;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;

/** Operaciones de estadísticas de recursos y actividades. */
public class ManejadorEstadisticas {

    private static final String PERIODO_INVALIDO =
            "La fecha 'desde' no puede ser posterior a la fecha 'hasta'.";

    private final EstadisticasRecursosService recursosService;
    private final EstadisticasActividadesService actividadesService;
    private final ReservaService reservaService;

    public ManejadorEstadisticas(
            EstadisticasRecursosService recursosService,
            EstadisticasActividadesService actividadesService,
            ReservaService reservaService
    ) {
        this.recursosService = Objects.requireNonNull(recursosService);
        this.actividadesService = Objects.requireNonNull(actividadesService);
        this.reservaService = Objects.requireNonNull(reservaService);
    }

    public void registrarEn(DespachadorPeticiones despachador) {
        despachador.registrar(Operaciones.EST_RECURSOS, this::recursos);
        despachador.registrar(Operaciones.EST_ACTIVIDADES, this::actividades);
    }

    private Object recursos(JsonNode datos, ContextoSesion contexto) {
        Periodo periodo = periodo(datos);
        EstadisticasRecursosLogica.ResultadoEstadisticas resultado =
                recursosService.calcular(periodo.desde(), periodo.hasta());

        if (!resultado.isExitoso()) {
            throw new IllegalArgumentException(resultado.getMensajeError());
        }

        return resultado.getDatos();
    }

    private Object actividades(JsonNode datos, ContextoSesion contexto) {
        Periodo periodo = periodo(datos);
        List<ReservaDTO> reservas = reservaService.buscarPorPeriodo(
                periodo.desde(),
                periodo.hasta()
        );

        return actividadesService.contarActividadesPorSemana(
                periodo.desde(),
                periodo.hasta(),
                reservas
        );
    }

    private Periodo periodo(JsonNode datos) {
        LocalDate desde = fecha(datos, "desde");
        LocalDate hasta = fecha(datos, "hasta");

        if (desde.isAfter(hasta)) {
            throw new IllegalArgumentException(PERIODO_INVALIDO);
        }

        return new Periodo(desde, hasta);
    }

    private LocalDate fecha(JsonNode datos, String campo) {
        JsonNode valor = datos == null ? null : datos.get(campo);

        if (valor == null || !valor.isTextual()) {
            throw new IllegalArgumentException(DatosSolicitud.MENSAJE_INVALIDOS);
        }

        try {
            return LocalDate.parse(DatosSolicitud.texto(datos, campo));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(DatosSolicitud.MENSAJE_INVALIDOS, e);
        }
    }

    private record Periodo(LocalDate desde, LocalDate hasta) {
    }
}
