package Sistema.Service;

import Sistema.DTO.EstadoReserva;
import Sistema.DTO.RecursoDTO;
import Sistema.DTO.ReservaDTO;
import Sistema.util.HorarioSistema;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class CalendarizacionService {

    private static final DateTimeFormatter FORMATO_HORA =
            DateTimeFormatter.ofPattern("HH:mm");

    public List<String> generarHorasDelDia() {
        List<String> horas = new ArrayList<>();

        LocalTime hora = LocalTime.MIDNIGHT;

        int cantidadBloques = (24 * 60) / HorarioSistema.MINUTOS_POR_BLOQUE;

        for (int bloque = 0; bloque < cantidadBloques; bloque++) {
            horas.add(hora.format(FORMATO_HORA));
            hora = hora.plusMinutes(HorarioSistema.MINUTOS_POR_BLOQUE);
        }
        return horas;
    }

    public String obtenerEstadoRecurso(RecursoDTO recurso, LocalDate fecha, String hora, List<ReservaDTO> reservas) {
        LocalTime inicioBloque = LocalTime.parse(hora, FORMATO_HORA);

        if (inicioBloque.isBefore(HorarioSistema.INICIO_SERVICIO) || !inicioBloque.isBefore(HorarioSistema.FIN_SERVICIO)) {
            return "Fuera de horario";
        }

        LocalTime finBloque = inicioBloque.plusMinutes(HorarioSistema.MINUTOS_POR_BLOQUE);

        if (reservas == null) {return "Disponible";}

        for (ReservaDTO reserva : reservas) {
            if (reserva == null
                    || reserva.getEstado() != EstadoReserva.ACTIVA
                    || !Objects.equals(fecha, reserva.getFecha())
                    || reserva.getHoraInicio() == null
                    || reserva.getHoraFin() == null) {
                continue;
            }

            boolean contieneRecurso =
                    reserva.getRecursosReservados() != null
                            && reserva.getRecursosReservados()
                            .stream()
                            .filter(Objects::nonNull)
                            .anyMatch(recursoReservado ->
                                    Objects.equals(recursoReservado.getId(), recurso.getId())
                            );

            boolean ocupaBloque = reserva.getHoraInicio().isBefore(finBloque)
                            && reserva.getHoraFin().isAfter(inicioBloque);

            if (contieneRecurso && ocupaBloque) {
                return "Reservado - " + reserva.getNombreActividad() + " - " + reserva.getNombreFuncionario();
            }
        }
        return "Disponible";
    }
}