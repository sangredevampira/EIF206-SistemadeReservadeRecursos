package Sistema.Logica;

import Sistema.DTO.EstadoReserva;
import Sistema.DTO.ProgramacionActividadesDTO;
import Sistema.DTO.ReservaDTO;
import Sistema.util.HorarioSistema;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Arma la programacion de actividades (fecha x hora) de un rango de fechas.
public class ProgramacionActividadesLogica {

    private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final ReservaLogica reservaLogica;

    // la logica real no abre conexion al construirse
    public ProgramacionActividadesLogica() {
        this(new ReservaLogica());
    }

    // permite inyectar la logica de reservas (lo usan las pruebas)
    public ProgramacionActividadesLogica(ReservaLogica reservaLogica) {
        this.reservaLogica = reservaLogica;
    }

    // la validacion de las fechas la hace reservaLogica.buscarPorPeriodo
    public ProgramacionActividadesDTO consultar(LocalDate fechaDesde, LocalDate fechaHasta) {
        List<ReservaDTO> reservas = reservaLogica.buscarPorPeriodo(fechaDesde, fechaHasta);

        Map<String, Map<String, String>> porFecha = new HashMap<>();
        for (Map.Entry<LocalDate, Map<String, String>> entrada : construirProgramacion(reservas).entrySet())
            porFecha.put(entrada.getKey().toString(), entrada.getValue());

        ProgramacionActividadesDTO resultado = new ProgramacionActividadesDTO();
        resultado.setHoras(generarHorasDelDia());
        resultado.setActividadesPorFecha(porFecha);
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

    public Map<LocalDate, Map<String, String>> construirProgramacion(List<ReservaDTO> reservaciones) {
        Map<LocalDate, Map<String, String>> actividadesPorFecha = new HashMap<>();

        if (reservaciones == null) {
            return actividadesPorFecha;
        }

        int cantidadBloques = (24 * 60) / HorarioSistema.MINUTOS_POR_BLOQUE;

        for (ReservaDTO reserva : reservaciones) {
            if (reserva == null
                    || reserva.getEstado() != EstadoReserva.ACTIVA
                    || reserva.getFecha() == null
                    || reserva.getHoraInicio() == null
                    || reserva.getHoraFin() == null) {
                continue;
            }

            LocalTime inicioBloque = LocalTime.MIDNIGHT;

            for (int bloque = 0; bloque < cantidadBloques; bloque++) {
                LocalTime finBloque;

                if (bloque == cantidadBloques - 1) {
                    finBloque = LocalTime.MAX;
                } else {
                    finBloque = inicioBloque.plusMinutes(HorarioSistema.MINUTOS_POR_BLOQUE);
                }

                boolean ocupaBloque =
                        reserva.getHoraInicio().isBefore(finBloque)
                                && reserva.getHoraFin().isAfter(inicioBloque);

                if (ocupaBloque) {
                    registrarActividad(actividadesPorFecha, reserva, inicioBloque.format(FORMATO_HORA));
                }
                inicioBloque = inicioBloque.plusMinutes(HorarioSistema.MINUTOS_POR_BLOQUE);
            }
        }
        return actividadesPorFecha;
    }

    private void registrarActividad(Map<LocalDate, Map<String, String>> actividadesPorFecha, ReservaDTO reserva, String hora) {
        Map<String, String> actividadesDelDia = actividadesPorFecha.computeIfAbsent(reserva.getFecha(), fecha -> new HashMap<>());

        String textoActividad = reserva.getNombreActividad() + "\n" + reserva.getNombreFuncionario();

        actividadesDelDia.merge(
                hora,
                textoActividad,
                (actividadAnterior, actividadNueva) ->
                        actividadAnterior
                                + "\n\n"
                                + actividadNueva
        );
    }
}
