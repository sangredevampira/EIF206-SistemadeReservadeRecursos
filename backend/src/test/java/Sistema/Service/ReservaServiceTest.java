package Sistema.Service;

import Sistema.DTO.EstadoReserva;
import Sistema.DTO.ReservaDTO;
import Sistema.Logica.ReservaLogica;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReservaServiceTest {

    static class ReservaLogicaFake extends ReservaLogica {
        ReservaDTO reservaARetornarRegistrar;
        boolean valorARetornarCancelar;
        ReservaDTO reservaARetornarBuscar;
        List<ReservaDTO> listaARetornar;
        RuntimeException excepcionARetornar;
        String idCanceladaRecibido;
        String idBuscadaRecibido;

        @Override
        public ReservaDTO registrarReserva(String idFuncionario, String nombreFuncionario, String nombreActividad,
                                           LocalDate fecha, LocalTime horaInicio, LocalTime horaFin,
                                           List<String> idsCategoriasSolicitadas) {
            if (excepcionARetornar != null) throw excepcionARetornar;
            return reservaARetornarRegistrar;
        }

        @Override
        public boolean cancelarReserva(String idReserva) {
            idCanceladaRecibido = idReserva;
            if (excepcionARetornar != null) throw excepcionARetornar;
            return valorARetornarCancelar;
        }

        @Override
        public ReservaDTO buscarReservaID(String id) {
            idBuscadaRecibido = id;
            if (excepcionARetornar != null) throw excepcionARetornar;
            return reservaARetornarBuscar;
        }

        @Override
        public List<ReservaDTO> buscarPorFuncionario(String idFuncionario) {
            if (excepcionARetornar != null) throw excepcionARetornar;
            return listaARetornar;
        }

        @Override
        public List<ReservaDTO> buscarPorPeriodo(LocalDate fechaDesde, LocalDate fechaHasta) {
            if (excepcionARetornar != null) throw excepcionARetornar;
            return listaARetornar;
        }
    }

    @Test
    void registrarReservaDevuelveLaReservaCreada() {
        ReservaLogicaFake fake = new ReservaLogicaFake();
        fake.reservaARetornarRegistrar = new ReservaDTO();
        fake.reservaARetornarRegistrar.setIdReserva("RES-000001");
        ReservaService service = new ReservaService(fake);

        ReservaDTO resultado = service.registrarReserva("FUNC-01", "Ana Pérez", "Reunión de ventas",
                LocalDate.now().plusDays(1), LocalTime.of(8, 0), LocalTime.of(10, 0),
                List.of("CAT-000001"));

        assertSame(fake.reservaARetornarRegistrar, resultado);
    }

    @Test
    void registrarReservaSinDisponibilidadPropagaLaExcepcionConElMensaje() {
        ReservaLogicaFake fake = new ReservaLogicaFake();
        // así responde ReservaLogica cuando falta una categoría
        fake.excepcionARetornar = new IllegalArgumentException("No hay recursos disponibles en las categorías: Salas");
        ReservaService service = new ReservaService(fake);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                service.registrarReserva("FUNC-01", "Ana Pérez", "Reunión",
                        LocalDate.now().plusDays(1), LocalTime.of(8, 0), LocalTime.of(9, 0),
                        List.of("CAT-000002")));

        assertEquals("No hay recursos disponibles en las categorías: Salas", error.getMessage());
    }

    @Test
    void registrarReservaConDatosInvalidosPropagaLaExcepcionDeLaLogica() {
        ReservaLogicaFake fake = new ReservaLogicaFake();
        fake.excepcionARetornar = new IllegalArgumentException("Debe describir la actividad a realizar.");
        ReservaService service = new ReservaService(fake);

        assertThrows(IllegalArgumentException.class, () ->
                service.registrarReserva("FUNC-01", "Ana Pérez", "",
                        LocalDate.now().plusDays(1), LocalTime.of(8, 0), LocalTime.of(9, 0),
                        List.of("CAT-000001")));
    }

    @Test
    void cancelarReservaDelegaYDevuelveSuResultado() {
        ReservaLogicaFake fake = new ReservaLogicaFake();
        fake.valorARetornarCancelar = true;
        ReservaService service = new ReservaService(fake);

        assertTrue(service.cancelarReserva("RES-000001"));
        assertEquals("RES-000001", fake.idCanceladaRecibido);
    }

    @Test
    void cancelarReservaYaCanceladaPropagaLaExcepcion() {
        ReservaLogicaFake fake = new ReservaLogicaFake();
        fake.excepcionARetornar = new IllegalArgumentException("Esta reserva ya se encuentra cancelada");
        ReservaService service = new ReservaService(fake);

        assertThrows(IllegalArgumentException.class, () -> service.cancelarReserva("RES-000002"));
    }

    @Test
    void buscarReservaIDDelegaYDevuelveLaReservaEncontrada() {
        ReservaLogicaFake fake = new ReservaLogicaFake();
        ReservaDTO esperada = new ReservaDTO("RES-000001", "FUNC-01", "Ana Pérez", "Reunión",
                LocalDate.now().plusDays(1), LocalTime.of(8, 0), LocalTime.of(9, 0),
                List.of(), EstadoReserva.ACTIVA);
        fake.reservaARetornarBuscar = esperada;
        ReservaService service = new ReservaService(fake);

        assertEquals(esperada, service.buscarReservaID("RES-000001"));
        assertEquals("RES-000001", fake.idBuscadaRecibido);
    }

    @Test
    void buscarPorFuncionarioSinReservasPropagaLaExcepcion() {
        ReservaLogicaFake fake = new ReservaLogicaFake();
        fake.excepcionARetornar = new IllegalArgumentException("El usuario no tiene reservas registradas");
        ReservaService service = new ReservaService(fake);

        assertThrows(IllegalArgumentException.class, () -> service.buscarPorFuncionario("FUNC-99"));
    }

    @Test
    void buscarPorPeriodoConFechaDesdePosteriorAHastaPropagaLaExcepcion() {
        ReservaLogicaFake fake = new ReservaLogicaFake();
        fake.excepcionARetornar = new IllegalArgumentException(
                "La fecha 'desde' no puede ser posterior a la fecha 'hasta'");
        ReservaService service = new ReservaService(fake);

        assertThrows(IllegalArgumentException.class, () ->
                service.buscarPorPeriodo(LocalDate.now().plusDays(5), LocalDate.now()));
    }
}