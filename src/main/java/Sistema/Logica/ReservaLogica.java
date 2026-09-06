package Sistema.Logica;

import java.util.List;
import java.util.ArrayList;
import java.time.LocalDate;
import java.time.LocalTime;

import Sistema.DTO.RecursoDTO;
import Sistema.DTO.EstadoReserva;
import Sistema.DTO.Funcionario;
import Sistema.DTO.ReservaDTO;
import Sistema.DTO.EstadoReserva;
import Sistema.Datos.ReservaDatos;

import java.time.LocalDate;
import java.util.List;

public class ReservaLogica {

    private final ReservaDatos reservaDatos;
    private RecursoLogica recursoLogica;

    public ReservaLogica() { this.reservaDatos = new ReservaDatos("src/data/reservas.json"); }

    private RecursoLogica getRecursoLogica() {
        if (recursoLogica == null) recursoLogica = new RecursoLogica();
        return recursoLogica;
    }

    private RecursoDTO buscarPrimerRecursoDisponible(String idCategoria, LocalDate fecha,
                                                     LocalTime horaInicio, LocalTime horaFin) {
        List<RecursoDTO> recursosDeLaCategoria = getRecursoLogica().filtrarPorCategoria(idCategoria);
        for (RecursoDTO recurso : recursosDeLaCategoria)
            if (recursoDisponible(recurso, fecha, horaInicio, horaFin))
                return recurso;
        return null;
    }

    public String registrarReserva(String idFuncionario, String nombreFuncionario, String nombreActividad,
                                   LocalDate fecha, LocalTime horaInicio, LocalTime horaFin, List<String> idsCategoriasSolicitadas){
        validarDatosGenerales(nombreActividad, fecha, horaInicio, horaFin, idsCategoriasSolicitadas);

        List<RecursoDTO> recursosAsignados = new ArrayList<>();
        List<String> categoriasNoDisponibles = new ArrayList<>();

        for (String idCategoria : idsCategoriasSolicitadas) {
            RecursoDTO disponible = buscarPrimerRecursoDisponible(idCategoria, fecha, horaInicio, horaFin);

            if (disponible == null) categoriasNoDisponibles.add(idCategoria);   // TODO: cambiar a descripcion de categoria
            else recursosAsignados.add(disponible);
        }

        if (!categoriasNoDisponibles.isEmpty()) {
            String listado = String.join(", ", categoriasNoDisponibles);
            throw new IllegalArgumentException( "No hay disponibilidad para las siguientes categorías: " + listado);
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
        return nuevaReserva.getIdReserva();
    }

    public boolean cancelarReserva(String idReserva){
        ReservaDTO reserva = reservaDatos.buscarReservaID(idReserva);

        if(reserva == null)
            throw new IllegalArgumentException("La reserva que se intenta cancelar no existe");
        if(reserva.getEstado() == EstadoReserva.CANCELADA)
            throw new IllegalArgumentException("Esta reserva ya se encuentra cancelada");
        if(reserva.getFechaReserva().isBefore(LocalDate.now()))
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

    public String generarNuevoId(){
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
    private void validarDatosGenerales(String nombreActividad, LocalDate fecha, LocalTime horaInicio,
                                     LocalTime horaFin, List<String> idsCategoriasSolicitadas) {
        if(nombreActividad == null || nombreActividad.isBlank())
            throw new IllegalArgumentException("Debe describir la actividad a realizar");
        if(fecha.isBefore(LocalDate.now()))
            throw new IllegalArgumentException("No se pueden crear reservas en fechas pasadas");
        if(horaInicio == null || horaFin == null)
            throw new IllegalArgumentException("Debe indicar la hora de inicio y finalización");
        if(!horaInicio.isBefore(horaFin))
            throw new IllegalArgumentException("La hora de inicio debe ser anterior a la hora de finalización");
        if(idsCategoriasSolicitadas == null || idsCategoriasSolicitadas.isEmpty())
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

    /**
     * Busca en reservas.json todas las reservas del funcionario dado y se
     * las asigna a su atributo funcionario.reservas (ver Funcionario.java,
     * @JsonIgnore: ese atributo nunca se persiste, es solo un "casillero"
     * en memoria para que la UI lo consulte).
     *
     * Este es el metodo que reemplaza la idea original de
     * "filtrarReservasPorUsuario": en vez de que quien pida las reservas
     * reciba una lista suelta, se la dejamos ya cargada dentro del propio
     * Funcionario, que es el objeto que representa a quien reserva
     * (ver enunciado, funcionalidad 2).
     */
    public void cargarReservasDeFuncionario(Funcionario funcionario) {
        if (funcionario == null) {
            return;
        }
        List<ReservaDTO> reservas = reservaDatos.filtrarReservaFuncionario(funcionario.getId());
        funcionario.setReservas(reservas);
    }

    /**
     * Ejemplo pedido por Kris: recibe una lista de reservas ya armada
     * (por ejemplo, el resultado de una busqueda o filtro distinto a
     * "todas las reservas del funcionario") y la asigna directo al
     * atributo, sin volver a consultar el archivo.
     *
     * Nombre distinto a proposito de "agregarReserva" (singular, en
     * ReservaDatos) para no confundirlos: ese SI guarda en el JSON;
     * este SOLO pega una lista en memoria, en el atributo del Funcionario.
     */
    public void asignarReservasAFuncionario(Funcionario funcionario, List<ReservaDTO> reservas) {
        if (funcionario == null) {
            return;
        }
        funcionario.setReservas(reservas);
    }

    /**
     * Regla del plan detallado (2.5, "Registro de un funcionario" / regla
     * de eliminacion) y del TODO que quedo pendiente en FuncionarioLogica:
     * "No se puede eliminar el funcionario porque tiene reservas futuras
     * registradas." Una reserva cuenta como "futura" si su fecha es hoy o
     * despues Y sigue ACTIVA (una CANCELADA no bloquea el borrado, aunque
     * la fecha todavia no haya pasado).
     */
    public boolean tieneReservasFuturas(String idFuncionario) {
        List<ReservaDTO> reservas = reservaDatos.filtrarReservaFuncionario(idFuncionario);
        LocalDate hoy = LocalDate.now();

        for (ReservaDTO reserva : reservas) {
            boolean esFutura = !reserva.getFechaReserva().isBefore(hoy);
            boolean estaActiva = reserva.getEstado() == EstadoReserva.ACTIVA;
            if (esFutura && estaActiva) {
                return true;
            }
        }
        return false;
    }
}
