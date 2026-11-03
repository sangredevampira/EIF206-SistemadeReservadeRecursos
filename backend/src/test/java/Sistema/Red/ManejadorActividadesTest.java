package Sistema.Red;

import Sistema.DTO.ProgramacionActividadesDTO;
import Sistema.Service.ProgramacionActividadesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static Sistema.Red.ApoyoManejadores.*;
import static org.junit.jupiter.api.Assertions.*;

// Por medio del despachador real y un servicio falso (sin red ni MySQL).
class ManejadorActividadesTest {

    static class ServicioFalso extends ProgramacionActividadesService {
        boolean seLlamo = false;
        LocalDate desdeRecibido;
        LocalDate hastaRecibido;
        ProgramacionActividadesDTO respuesta = new ProgramacionActividadesDTO();
        RuntimeException fallo;

        @Override
        public ProgramacionActividadesDTO consultar(LocalDate fechaDesde, LocalDate fechaHasta) {
            seLlamo = true;
            desdeRecibido = fechaDesde;
            hastaRecibido = fechaHasta;
            if (fallo != null) throw fallo;
            return respuesta;
        }
    }

    private ServicioFalso servicio;
    private DespachadorPeticiones despachador;

    @BeforeEach
    void preparar() {
        servicio = new ServicioFalso();
        despachador = new DespachadorPeticiones();
        new ManejadorActividades(servicio).registrarEn(despachador);

        servicio.respuesta.setHoras(List.of("07:00"));
        servicio.respuesta.setActividadesPorFecha(Map.of("2026-11-03", Map.of("07:00", "Reunión\nAna")));
    }

    @Test
    void enviaLasFechasAlServicioYDevuelveElDto() {
        MensajeRed respuesta = enviar(despachador, Operaciones.ACT_CONSULTAR,
                Map.of("fechaDesde", "2026-11-01", "fechaHasta", "2026-11-30"), funcionario());

        assertExito(respuesta);
        assertEquals(LocalDate.of(2026, 11, 1), servicio.desdeRecibido);
        assertEquals(LocalDate.of(2026, 11, 30), servicio.hastaRecibido);
        assertEquals("07:00", respuesta.getDatos().get("horas").get(0).asText());
        assertEquals("Reunión\nAna",
                respuesta.getDatos().get("actividadesPorFecha").get("2026-11-03").get("07:00").asText());
    }

    @Test
    void unaFechaIlegibleDaDatosNoValidosYNoLlamaAlServicio() {
        MensajeRed respuesta = enviar(despachador, Operaciones.ACT_CONSULTAR,
                Map.of("fechaDesde", "ayer", "fechaHasta", "2026-11-30"), funcionario());

        assertError(DATOS_INVALIDOS, respuesta);
        assertFalse(servicio.seLlamo);
    }

    @Test
    void unDatoAusenteLlegaComoNullAlServicio() {
        enviar(despachador, Operaciones.ACT_CONSULTAR, Map.of("fechaDesde", "2026-11-01"), funcionario());

        assertTrue(servicio.seLlamo);
        assertEquals(LocalDate.of(2026, 11, 1), servicio.desdeRecibido);
        assertNull(servicio.hastaRecibido);
    }

    @Test
    void elMensajeDeLaLogicaLlegaAlCliente() {
        servicio.fallo = new IllegalArgumentException("Debe indicar ambas fechas del período");

        MensajeRed respuesta = enviar(despachador, Operaciones.ACT_CONSULTAR,
                Map.of("fechaDesde", "2026-11-01"), funcionario());

        assertError("Debe indicar ambas fechas del período", respuesta);
    }

    @Test
    void sinSesionNoSeAtiende() {
        MensajeRed respuesta = enviar(despachador, Operaciones.ACT_CONSULTAR,
                Map.of("fechaDesde", "2026-11-01", "fechaHasta", "2026-11-30"), sinSesion());

        assertError(SIN_SESION, respuesta);
        assertFalse(servicio.seLlamo);
    }
}
