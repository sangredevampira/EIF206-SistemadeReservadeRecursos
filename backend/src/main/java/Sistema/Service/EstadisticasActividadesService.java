package Sistema.Service;

import Sistema.DTO.EstadoReserva;
import Sistema.DTO.ReservaDTO;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class EstadisticasActividadesService {

    public Map<LocalDate, Integer> contarActividadesPorSemana(
            LocalDate fechaDesde,
            LocalDate fechaHasta,
            List<ReservaDTO> reservas
    ) {
        if (fechaDesde == null || fechaHasta == null) {
            throw new IllegalArgumentException("Debe indicar ambas fechas del período.");
        }

        if (fechaDesde.isAfter(fechaHasta)) {
            throw new IllegalArgumentException("La fecha inicial no puede ser posterior a la fecha final.");
        }

        Map<LocalDate, Integer> conteoSemanal =
                new TreeMap<>();

        LocalDate primeraSemana = fechaDesde.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        LocalDate ultimaSemana = fechaHasta.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        LocalDate semanaActual = primeraSemana;

        while (!semanaActual.isAfter(ultimaSemana)) {
            conteoSemanal.put(semanaActual, 0);
            semanaActual = semanaActual.plusWeeks(1);
        }

        if (reservas == null) {
            return conteoSemanal;
        }

        for (ReservaDTO reserva : reservas) {
            if (reserva == null
                    || reserva.getEstado() != EstadoReserva.ACTIVA
                    || reserva.getFecha() == null
                    || reserva.getFecha().isBefore(fechaDesde)
                    || reserva.getFecha().isAfter(fechaHasta)) {

                continue;
            }

            LocalDate lunesDeLaSemana = reserva.getFecha().with(
                            TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

            conteoSemanal.computeIfPresent(
                    lunesDeLaSemana,
                    (semana, cantidad) -> cantidad + 1
            );
        }
        return conteoSemanal;
    }
}