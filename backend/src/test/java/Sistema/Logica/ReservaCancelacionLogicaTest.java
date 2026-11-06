package Sistema.Logica;

import Sistema.DTO.EstadoReserva;
import Sistema.DTO.ReservaDTO;
import Sistema.Datos.ReservaDatos;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReservaCancelacionLogicaTest {

    @Test
    void cancelaUnaReservaActivaFutura() {
        DatosFalsos datos = new DatosFalsos(reserva(EstadoReserva.ACTIVA, LocalDate.now().plusDays(1)));
        ReservaLogica logica = new ReservaLogica(datos, null, null);

        assertTrue(logica.cancelarReserva("RES-000001"));
        assertEquals(1, datos.cancelaciones);
        assertEquals("RES-000001", datos.idCancelado);
    }

    @Test
    void reservaPreviamenteCanceladaConservaElMensajeExistente() {
        DatosFalsos datos = new DatosFalsos(reserva(EstadoReserva.CANCELADA, LocalDate.now().plusDays(1)));
        ReservaLogica logica = new ReservaLogica(datos, null, null);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> logica.cancelarReserva("RES-000001")
        );

        assertEquals("Esta reserva ya se encuentra cancelada", error.getMessage());
        assertEquals(0, datos.cancelaciones);
    }

    @Test
    void reservaInexistenteConservaElMensajeExistente() {
        DatosFalsos datos = new DatosFalsos(null);
        ReservaLogica logica = new ReservaLogica(datos, null, null);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> logica.cancelarReserva("RES-NO-EXISTE")
        );

        assertEquals("La reserva que se intenta cancelar no existe", error.getMessage());
        assertEquals(0, datos.cancelaciones);
    }

    @Test
    void reservaPasadaConservaLaRestriccionDeFecha() {
        DatosFalsos datos = new DatosFalsos(reserva(EstadoReserva.ACTIVA, LocalDate.now().minusDays(1)));
        ReservaLogica logica = new ReservaLogica(datos, null, null);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> logica.cancelarReserva("RES-000001")
        );

        assertEquals("No se puede cancelar una reserva que ya pasó", error.getMessage());
        assertEquals(0, datos.cancelaciones);
    }

    @Test
    void siOtraSolicitudGanaLaTransicionNoConfirmaUnaSegundaCancelacion() {
        DatosFalsos datos = new DatosFalsos(reserva(EstadoReserva.ACTIVA, LocalDate.now().plusDays(1)));
        datos.resultadoCancelacion = false;
        ReservaLogica logica = new ReservaLogica(datos, null, null);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> logica.cancelarReserva("RES-000001")
        );

        assertEquals("Esta reserva ya se encuentra cancelada", error.getMessage());
        assertEquals(1, datos.cancelaciones);
    }

    private static ReservaDTO reserva(EstadoReserva estado, LocalDate fecha) {
        return new ReservaDTO(
                "RES-000001",
                "FUNC-01",
                "Ana",
                "Reunión",
                fecha,
                LocalTime.of(8, 0),
                LocalTime.of(9, 0),
                List.of(),
                estado
        );
    }

    private static final class DatosFalsos extends ReservaDatos {
        private final ReservaDTO reserva;
        private boolean resultadoCancelacion = true;
        private int cancelaciones;
        private String idCancelado;

        private DatosFalsos(ReservaDTO reserva) {
            this.reserva = reserva;
        }

        @Override
        public ReservaDTO buscarReservaID(String id) {
            return reserva;
        }

        @Override
        public boolean cancelarReservaActiva(String idReserva) {
            cancelaciones++;
            idCancelado = idReserva;
            return resultadoCancelacion;
        }
    }
}
