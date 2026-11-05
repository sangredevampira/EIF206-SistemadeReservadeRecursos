package Sistema.Logica;

import Sistema.DTO.CalendarizacionDTO;
import Sistema.DTO.DisponibilidadRecursoDTO;
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

// Arma la matriz de disponibilidad (recursos x horas) de una categoria en una fecha.
public class CalendarizacionLogica {

    private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final RecursoLogica recursoLogica;
    private final ReservaLogica reservaLogica;

    // las logicas reales no abren conexion al construirse
    public CalendarizacionLogica() {
        this(new RecursoLogica(), new ReservaLogica());
    }

    // permite inyectar las logicas (lo usan las pruebas)
    public CalendarizacionLogica(RecursoLogica recursoLogica, ReservaLogica reservaLogica) {
        this.recursoLogica = recursoLogica;
        this.reservaLogica = reservaLogica;
    }

    public CalendarizacionDTO consultar(String idCategoria, LocalDate fecha) {
        if (idCategoria == null || idCategoria.isBlank())
            throw new IllegalArgumentException("Debe seleccionar una categoría.");
        if (fecha == null)
            throw new IllegalArgumentException("Debe seleccionar una fecha.");

        List<RecursoDTO> recursos = recursoLogica.filtrarRecursos(idCategoria);
        List<ReservaDTO> reservas = reservaLogica.buscarPorPeriodo(fecha, fecha);
        List<String> horas = generarHorasDelDia();

        List<DisponibilidadRecursoDTO> filas = new ArrayList<>();
        for (RecursoDTO recurso : recursos) {
            List<String> estados = new ArrayList<>();
            for (String hora : horas)
                estados.add(obtenerEstadoRecurso(recurso, fecha, hora, reservas));

            DisponibilidadRecursoDTO fila = new DisponibilidadRecursoDTO();
            fila.setRecurso(recurso);
            fila.setEstados(estados);
            filas.add(fila);
        }

        CalendarizacionDTO resultado = new CalendarizacionDTO();
        resultado.setHoras(horas);
        resultado.setRecursos(filas);
        return resultado;
    }

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
