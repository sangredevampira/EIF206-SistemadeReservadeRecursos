package Sistema.Logica;

import java.util.List;
import java.util.ArrayList;
import java.time.LocalDate;
import java.time.LocalTime;

import Sistema.DTO.RecursoDTO;
import Sistema.DTO.ReservaDTO;
import Sistema.DTO.Funcionario;
import Sistema.DTO.EstadoReserva;
import Sistema.Datos.ReservaDatos;
import Sistema.util.HorarioSistema;

public class ReservaLogica {

    private final ReservaDatos reservaDatos;
    private RecursoLogica recursoLogica;

    public ReservaLogica() { this.reservaDatos = new ReservaDatos("src/data/reservas.json"); }

    // constructor solo para pruebas, permite inyectar ambas dependencias
    public ReservaLogica(ReservaDatos reservaDatos, RecursoLogica recursoLogica) {
        this.reservaDatos = reservaDatos;
        this.recursoLogica = recursoLogica;
    }

    private RecursoLogica getRecursoLogica() {
        if (recursoLogica == null) recursoLogica = new RecursoLogica();
        return recursoLogica;
    }

    private RecursoDTO buscarPrimerRecursoDisponible(String idCategoria, LocalDate fecha,
                                                     LocalTime horaInicio, LocalTime horaFin) {
        List<RecursoDTO> recursosDeLaCategoria = getRecursoLogica().filtrarRecursos(idCategoria);
        for (RecursoDTO recurso : recursosDeLaCategoria)
            if (recursoDisponible(recurso, fecha, horaInicio, horaFin))
                return recurso;
        return null;
    }

    public int registrarReserva(String idFuncionario, String nombreFuncionario, String nombreActividad,
                                LocalDate fecha, LocalTime horaInicio, LocalTime horaFin,
                                List<String> idsCategoriasSolicitadas) {

        validarDatosGenerales(nombreActividad, fecha, horaInicio, horaFin, idsCategoriasSolicitadas);

        List<RecursoDTO> recursosAsignados = new ArrayList<>();
        List<String> categoriasNoDisponibles = new ArrayList<>();

        for (String idCategoria : idsCategoriasSolicitadas) {
            RecursoDTO disponible = buscarPrimerRecursoDisponible(
                    idCategoria, fecha, horaInicio, horaFin
            );

            if (disponible == null)
                categoriasNoDisponibles.add(idCategoria);
            else
                recursosAsignados.add(disponible);
        }

        if (!categoriasNoDisponibles.isEmpty()) {
            String listado = String.join(", ", categoriasNoDisponibles);
            return 0;
        }

        ReservaDTO nuevaReserva = new ReservaDTO(
                generarNuevoId(),
                idFuncionario,
                nombreFuncionario,
                nombreActividad,
                fecha,
                horaInicio,
                horaFin,
                recursosAsignados,
                EstadoReserva.ACTIVA
        );

        reservaDatos.agregarReserva(nuevaReserva);

        return 1;
    }

    public boolean cancelarReserva(String idReserva){
        ReservaDTO reserva = reservaDatos.buscarReservaID(idReserva);

        if(reserva == null)
            throw new IllegalArgumentException("La reserva que se intenta cancelar no existe");
        if(reserva.getEstado() == EstadoReserva.CANCELADA)
            throw new IllegalArgumentException("Esta reserva ya se encuentra cancelada");
        if(reserva.getFecha().isBefore(LocalDate.now()))
            throw new IllegalArgumentException("No se puede cancelar una reserva que ya pasó");

        reserva.setEstado(EstadoReserva.CANCELADA);
        return reservaDatos.modificarReserva(reserva);
    }

    public ReservaDTO buscarReservaID(String id){
        ReservaDTO reserva = reservaDatos.buscarReservaID(id);
        if(reserva == null)
            throw new IllegalArgumentException("La reserva solicitada no fue encontrada");
        return reserva;
    }

    // ver las reservas de un funcionario
    public List<ReservaDTO> buscarPorFuncionario(String idFuncionario){
        List<ReservaDTO> reservasFuncionario = reservaDatos.filtrarReservaFuncionario(idFuncionario);
        if(reservasFuncionario.isEmpty())
            throw new IllegalArgumentException("El usuario no tiene reservas registradas");

        return reservasFuncionario;
    }

    // metodos para coordinarcon con el trabajo de sofi
    public List<ReservaDTO> buscarPorPeriodo(LocalDate fechaDesde, LocalDate fechaHasta){
        if (fechaDesde == null || fechaHasta == null)
            throw new IllegalArgumentException("Debe indicar ambas fechas del período");

        if (fechaDesde.isAfter(fechaHasta))
            throw new IllegalArgumentException("La fecha 'desde' no puede ser posterior a la fecha 'hasta'");

        return reservaDatos.filtrarReservasRangoFechas(fechaDesde, fechaHasta);
    }

    // metodo para coordinar la eliminacion de un recurso con RecursoLogica
    public boolean tieneReservasFuturasActivas(String idRecurso){
        LocalDate hoy = LocalDate.now();
        List<ReservaDTO> reservasDelRecurso = reservaDatos.filtrarReservaPorRecurso(idRecurso);

        for(ReservaDTO r : reservasDelRecurso){
            boolean esFutura = !r.getFecha().isBefore(hoy);
            boolean estaActiva = r.getEstado() == EstadoReserva.ACTIVA;

            if(esFutura && estaActiva) return true;
        }

        return false;
    }

    private String generarNuevoId(){
        List<ReservaDTO> reservas = reservaDatos.listarReservas();
        int numeroMasAlto = 0;

        for(ReservaDTO r : reservas){
            String id = r.getIdReserva();
            if(id != null && id.startsWith("RES-")){
                try{
                    int numero = Integer.parseInt(id.substring(4));
                    if (numero > numeroMasAlto) numeroMasAlto = numero;
                } catch (NumberFormatException error){ }
            }
        }
        int siguienteNumero = numeroMasAlto + 1;
        return String.format("RES-%06d", siguienteNumero);
    }

    // metodos de comprobacion
    private void validarDatosGenerales(String nombreActividad, LocalDate fecha,
                                       LocalTime horaInicio, LocalTime horaFin,
                                       List<String> idsCategoriasSolicitadas) {
        if (nombreActividad == null || nombreActividad.isBlank())
            throw new IllegalArgumentException("Debe describir la actividad a realizar.");
        if (fecha == null)
            throw new IllegalArgumentException("Debe indicar la fecha de la reserva.");
        if (fecha.isBefore(LocalDate.now()))
            throw new IllegalArgumentException("No se pueden crear reservas en fechas pasadas.");
        if (horaInicio == null || horaFin == null)
            throw new IllegalArgumentException("Debe indicar la hora de inicio y finalización.");
        if (!horaInicio.isBefore(horaFin))
            throw new IllegalArgumentException("La hora de inicio debe ser anterior a la hora de finalización.");
        if (horaInicio.isBefore(HorarioSistema.INICIO_SERVICIO)
                || horaFin.isAfter(HorarioSistema.FIN_SERVICIO))
            throw new IllegalArgumentException("La reserva debe estar dentro del horario de 06:00 a 22:00.");
        if (horaInicio.getMinute() % HorarioSistema.MINUTOS_POR_BLOQUE != 0
                || horaFin.getMinute() % HorarioSistema.MINUTOS_POR_BLOQUE != 0
                || horaInicio.getSecond() != 0
                || horaFin.getSecond() != 0)
            throw new IllegalArgumentException("Las horas deben utilizar intervalos de 30 minutos.");
        if (idsCategoriasSolicitadas == null || idsCategoriasSolicitadas.isEmpty())
            throw new IllegalArgumentException("Debe seleccionar al menos una categoría de recurso.");
    }

    public boolean reservaExistente(String id){
        for (ReservaDTO r : reservaDatos.listarReservas())
            if (r.getIdReserva().equals(id))
                return true;
        return false;
    }

    private boolean recursoDisponible(RecursoDTO recurso, LocalDate fecha,
                                      LocalTime horaInicio, LocalTime horaFin){
        List<ReservaDTO> reservasRecurso = reservaDatos.filtrarReservaPorRecurso(recurso.getId());

        for(ReservaDTO r : reservasRecurso){
            boolean estaActiva = r.getEstado() == EstadoReserva.ACTIVA;
            boolean mismaFecha = r.getFecha().equals(fecha);

            boolean seTraslapan = horaInicio.isBefore(r.getHoraFin()) && horaFin.isAfter(r.getHoraInicio());

            if(estaActiva && mismaFecha && seTraslapan)
                return false;
        }
        return true;
    }

    public boolean tieneReservasFuturas(String idFuncionario) {
        List<ReservaDTO> reservas = reservaDatos.filtrarReservaFuncionario(idFuncionario);
        LocalDate hoy = LocalDate.now();

        for (ReservaDTO reserva : reservas) {
            boolean esFutura = !reserva.getFecha().isBefore(hoy);
            boolean estaActiva = reserva.getEstado() == EstadoReserva.ACTIVA;
            if (esFutura && estaActiva) {
                return true;
            }
        }
        return false;
    }
}
