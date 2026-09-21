package Sistema.Service;

import Sistema.DTO.EstadoReserva;
import Sistema.DTO.ReservaDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EstadisticasActividadesServiceTest {

    private EstadisticasActividadesService service;

    @BeforeEach
    void prepararDatosDePrueba() {
        service = new EstadisticasActividadesService();
    }

    private ReservaDTO crearReserva(String id, LocalDate fecha, EstadoReserva estado) {
        return new ReservaDTO(
                id,
                "FUNC-001",
                "Sofia",
                "Actividad de prueba",
                fecha,
                LocalTime.of(8, 0),
                LocalTime.of(9, 0),
                List.of(),
                estado
        );
    }

    @Test
    void contarActividadesAgrupaReservasActivasPorSemana() {
        LocalDate fechaDesde = LocalDate.of(2026, 9, 21);
        LocalDate fechaHasta = LocalDate.of(2026, 10, 4);
        ReservaDTO primeraActividad = crearReserva("RES-000001", LocalDate.of(2026, 9, 21), EstadoReserva.ACTIVA);

        ReservaDTO segundaActividad = crearReserva("RES-000002", LocalDate.of(2026, 9, 23), EstadoReserva.ACTIVA);
        ReservaDTO terceraActividad = crearReserva("RES-000003", LocalDate.of(2026, 9, 28), EstadoReserva.ACTIVA);
        ReservaDTO actividadCancelada = crearReserva("RES-000004", LocalDate.of(2026, 9, 22), EstadoReserva.CANCELADA);

        Map<LocalDate, Integer> resultado =
                service.contarActividadesPorSemana(fechaDesde, fechaHasta,
                        List.of(primeraActividad, segundaActividad, terceraActividad, actividadCancelada)
                );

        assertEquals(2, resultado.size());

        assertEquals(2, resultado.get(LocalDate.of(2026, 9, 21)));

        assertEquals(1, resultado.get(LocalDate.of(2026, 9, 28)));
    }

    @Test
    void semanaSinActividadesMantieneConteoEnCero() {
        LocalDate fechaDesde = LocalDate.of(2026, 9, 21);
        LocalDate fechaHasta = LocalDate.of(2026, 9, 27);

        Map<LocalDate, Integer> resultado =
                service.contarActividadesPorSemana(fechaDesde, fechaHasta, List.of());

        assertEquals(1, resultado.size());
        assertEquals(0, resultado.get(LocalDate.of(2026, 9, 21)));
    }
}