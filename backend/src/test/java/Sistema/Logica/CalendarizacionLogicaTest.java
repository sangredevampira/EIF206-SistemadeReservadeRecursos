package Sistema.Logica;

import Sistema.DTO.CalendarizacionDTO;
import Sistema.DTO.CategoriaDTO;
import Sistema.DTO.EstadoReserva;
import Sistema.DTO.RecursoDTO;
import Sistema.DTO.ReservaDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Sin MySQL: RecursoLogica y ReservaLogica se reemplazan por dobles que devuelven lo que la prueba indique.
class CalendarizacionLogicaTest {

    static class RecursoLogicaFalsa extends RecursoLogica {
        List<RecursoDTO> recursos = List.of();
        String categoriaRecibida;

        @Override
        public List<RecursoDTO> filtrarRecursos(String idCategoria) {
            categoriaRecibida = idCategoria;
            return recursos;
        }
    }

    static class ReservaLogicaFalsa extends ReservaLogica {
        List<ReservaDTO> reservas = List.of();
        LocalDate desdeRecibido;
        LocalDate hastaRecibido;

        @Override
        public List<ReservaDTO> buscarPorPeriodo(LocalDate fechaDesde, LocalDate fechaHasta) {
            desdeRecibido = fechaDesde;
            hastaRecibido = fechaHasta;
            return reservas;
        }
    }

    private RecursoLogicaFalsa recursoLogica;
    private ReservaLogicaFalsa reservaLogica;
    private CalendarizacionLogica logica;
    private RecursoDTO salaA;
    private RecursoDTO salaB;
    private final LocalDate fecha = LocalDate.of(2026, 9, 20);

    @BeforeEach
    void preparar() {
        recursoLogica = new RecursoLogicaFalsa();
        reservaLogica = new ReservaLogicaFalsa();
        logica = new CalendarizacionLogica(recursoLogica, reservaLogica);

        CategoriaDTO salas = new CategoriaDTO("CAT-000001", "Salas");
        salaA = new RecursoDTO("REC-000001", "Sala A", salas);
        salaB = new RecursoDTO("REC-000002", "Sala B", salas);
        recursoLogica.recursos = List.of(salaA, salaB);
    }

    private ReservaDTO reserva(RecursoDTO recurso, int horaIni, int minIni, int horaFin, int minFin, EstadoReserva estado) {
        return new ReservaDTO("RES-000001", "FUNC-001", "Sofia", "Reunión", fecha,
                LocalTime.of(horaIni, minIni), LocalTime.of(horaFin, minFin), List.of(recurso), estado);
    }

    @Test
    void generaCuarentaYOchoHoras() {
        List<String> horas = logica.generarHorasDelDia();

        assertEquals(48, horas.size());
        assertEquals("00:00", horas.get(0));
        assertEquals("23:30", horas.get(47));
    }

    @Test
    void losEstadosVanAlineadosConLasHoras() {
        CalendarizacionDTO resultado = logica.consultar("CAT-000001", fecha);

        assertEquals("CAT-000001", recursoLogica.categoriaRecibida);
        assertEquals(fecha, reservaLogica.desdeRecibido);
        assertEquals(fecha, reservaLogica.hastaRecibido);
        assertEquals(48, resultado.getHoras().size());
        assertEquals(2, resultado.getRecursos().size());
        assertSame(salaA, resultado.getRecursos().get(0).getRecurso());
        assertSame(salaB, resultado.getRecursos().get(1).getRecurso());
        for (var fila : resultado.getRecursos())
            assertEquals(resultado.getHoras().size(), fila.getEstados().size());
    }

    @Test
    void reservaDeSieteTreintaAOchoTreintaMarcaSoloSusBloquesYSoloEseRecurso() {
        reservaLogica.reservas = List.of(reserva(salaA, 7, 30, 8, 30, EstadoReserva.ACTIVA));

        CalendarizacionDTO resultado = logica.consultar("CAT-000001", fecha);
        List<String> horas = resultado.getHoras();
        List<String> estadosA = resultado.getRecursos().get(0).getEstados();
        List<String> estadosB = resultado.getRecursos().get(1).getEstados();

        assertEquals("Disponible", estadosA.get(horas.indexOf("07:00")));
        assertEquals("Reservado - Reunión - Sofia", estadosA.get(horas.indexOf("07:30")));
        assertEquals("Reservado - Reunión - Sofia", estadosA.get(horas.indexOf("08:00")));
        assertEquals("Disponible", estadosA.get(horas.indexOf("08:30")));
        assertEquals("Disponible", estadosB.get(horas.indexOf("07:30")));
    }

    @Test
    void fueraDeHorarioAntesDeLas6YDesdeLas22() {
        CalendarizacionDTO resultado = logica.consultar("CAT-000001", fecha);
        List<String> horas = resultado.getHoras();
        List<String> estados = resultado.getRecursos().get(0).getEstados();

        assertEquals("Fuera de horario", estados.get(horas.indexOf("00:00")));
        assertEquals("Fuera de horario", estados.get(horas.indexOf("05:30")));
        assertEquals("Disponible", estados.get(horas.indexOf("06:00")));
        assertEquals("Disponible", estados.get(horas.indexOf("21:30")));
        assertEquals("Fuera de horario", estados.get(horas.indexOf("22:00")));
        assertEquals("Fuera de horario", estados.get(horas.indexOf("23:30")));
    }

    @Test
    void unaReservaCanceladaNoOcupa() {
        reservaLogica.reservas = List.of(reserva(salaA, 7, 30, 8, 30, EstadoReserva.CANCELADA));

        List<String> estados = logica.consultar("CAT-000001", fecha).getRecursos().get(0).getEstados();

        assertFalse(estados.stream().anyMatch(e -> e.startsWith("Reservado")));
    }

    @Test
    void categoriaSinRecursosDevuelveListaVacia() {
        recursoLogica.recursos = List.of();

        CalendarizacionDTO resultado = logica.consultar("CAT-000001", fecha);

        assertNotNull(resultado.getRecursos());
        assertTrue(resultado.getRecursos().isEmpty());
        assertEquals(48, resultado.getHoras().size());
    }

    @Test
    void sinCategoriaLanzaElMensajeExacto() {
        for (String categoria : new String[]{null, "", "  "}) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> logica.consultar(categoria, fecha));
            assertEquals("Debe seleccionar una categoría.", e.getMessage());
        }
    }

    @Test
    void sinFechaLanzaElMensajeExacto() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> logica.consultar("CAT-000001", null));
        assertEquals("Debe seleccionar una fecha.", e.getMessage());
    }
}
