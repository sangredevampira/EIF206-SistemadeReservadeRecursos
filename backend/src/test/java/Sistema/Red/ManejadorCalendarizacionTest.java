package Sistema.Red;

import Sistema.DTO.CalendarizacionDTO;
import Sistema.DTO.DisponibilidadRecursoDTO;
import Sistema.DTO.RecursoDTO;
import Sistema.Service.CalendarizacionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static Sistema.Red.ApoyoManejadores.*;
import static org.junit.jupiter.api.Assertions.*;

// Por medio del despachador real y un servicio falso (sin red ni MySQL).
class ManejadorCalendarizacionTest {

    static class ServicioFalso extends CalendarizacionService {
        boolean seLlamo = false;
        String categoriaRecibida;
        LocalDate fechaRecibida;
        CalendarizacionDTO respuesta = new CalendarizacionDTO();
        RuntimeException fallo;

        @Override
        public CalendarizacionDTO consultar(String idCategoria, LocalDate fecha) {
            seLlamo = true;
            categoriaRecibida = idCategoria;
            fechaRecibida = fecha;
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
        new ManejadorCalendarizacion(servicio).registrarEn(despachador);

        DisponibilidadRecursoDTO fila = new DisponibilidadRecursoDTO();
        RecursoDTO recurso = new RecursoDTO();
        recurso.setId("REC-000001");
        fila.setRecurso(recurso);
        fila.setEstados(List.of("Fuera de horario", "Disponible"));
        servicio.respuesta.setHoras(List.of("05:30", "06:00"));
        servicio.respuesta.setRecursos(List.of(fila));
    }

    @Test
    void enviaLosDatosAlServicioYDevuelveElDto() {
        MensajeRed respuesta = enviar(despachador, Operaciones.CAL_CONSULTAR,
                Map.of("idCategoria", "CAT-000001", "fecha", "2026-11-03"), funcionario());

        assertExito(respuesta);
        assertEquals("CAT-000001", servicio.categoriaRecibida);
        assertEquals(LocalDate.of(2026, 11, 3), servicio.fechaRecibida);
        assertEquals("06:00", respuesta.getDatos().get("horas").get(1).asText());
        assertEquals("REC-000001", respuesta.getDatos().get("recursos").get(0).get("recurso").get("id").asText());
        assertEquals("Disponible", respuesta.getDatos().get("recursos").get(0).get("estados").get(1).asText());
    }

    @Test
    void unaFechaIlegibleDaDatosNoValidosYNoLlamaAlServicio() {
        MensajeRed respuesta = enviar(despachador, Operaciones.CAL_CONSULTAR,
                Map.of("idCategoria", "CAT-000001", "fecha", "03/11/2026"), funcionario());

        assertError(DATOS_INVALIDOS, respuesta);
        assertFalse(servicio.seLlamo);
    }

    @Test
    void unDatoAusenteOUnNullLlegaComoNullAlServicio() {
        enviar(despachador, Operaciones.CAL_CONSULTAR, Map.of("fecha", "2026-11-03"), funcionario());
        assertTrue(servicio.seLlamo);
        assertNull(servicio.categoriaRecibida);

        Map<String, Object> conNull = new HashMap<>();
        conNull.put("idCategoria", "CAT-000001");
        conNull.put("fecha", null);
        enviar(despachador, Operaciones.CAL_CONSULTAR, conNull, funcionario());
        assertNull(servicio.fechaRecibida);
    }

    @Test
    void elMensajeDeLaLogicaLlegaAlCliente() {
        servicio.fallo = new IllegalArgumentException("Debe seleccionar una fecha.");

        MensajeRed respuesta = enviar(despachador, Operaciones.CAL_CONSULTAR,
                Map.of("idCategoria", "CAT-000001"), funcionario());

        assertError("Debe seleccionar una fecha.", respuesta);
    }

    @Test
    void sinSesionNoSeAtiende() {
        MensajeRed respuesta = enviar(despachador, Operaciones.CAL_CONSULTAR,
                Map.of("idCategoria", "CAT-000001", "fecha", "2026-11-03"), sinSesion());

        assertError(SIN_SESION, respuesta);
        assertFalse(servicio.seLlamo);
    }
}
