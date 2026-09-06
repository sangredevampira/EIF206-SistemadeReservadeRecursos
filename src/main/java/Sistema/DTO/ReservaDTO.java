package Sistema.DTO;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public class ReservaDTO {

    private String idReserva;
    private String idFuncionario;
    private String nombreFuncionario;
    private String nombreActividad;            // descripcion de la actividad
    private LocalDate fecha;
    private LocalTime horaInicio;
    private LocalTime horaFin;
    private List<RecursoDTO> recursosReservados;
    private EstadoReserva estado;                  // se debe tener unicamente dos valores (ACTIVA/CANCELADA)

    public ReservaDTO() {}

    public ReservaDTO(String idReserva, String idFuncionario, String nombreFuncionario,
                     String nombreActividad, LocalDate fecha, LocalTime horaInicio,
                     LocalTime horaFin, List<RecursoDTO> recursosReservados, EstadoReserva estado) {
        this.idReserva = idReserva;
        this.idFuncionario = idFuncionario;
        this.nombreFuncionario = nombreFuncionario;
        this.nombreActividad = nombreActividad;
        this.fecha = fecha;
        this.horaInicio = horaInicio;
        this.horaFin = horaFin;
        this.recursosReservados = recursosReservados;
        this.estado = estado;
    }

    public String getIdReserva() { return idReserva; }
    public void setIdReserva(String idReserva) { this.idReserva = idReserva; }

    public String getIdFuncionario() { return idFuncionario; }
    public void setIdFuncionario(String idFuncionario) { this.idFuncionario = idFuncionario; }

    public String getNombreFuncionario() { return nombreFuncionario; }
    public void setNombreFuncionario(String nombreFuncionario) { this.nombreFuncionario = nombreFuncionario; }

    public String getNombreActividad() { return nombreActividad; }
    public void setNombreActividad(String nombreActividad) { this.nombreActividad = nombreActividad; }

    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }

    public LocalTime getHoraInicio() { return horaInicio; }
    public void setHoraInicio(LocalTime horaInicio) { this.horaInicio = horaInicio; }

    public LocalTime getHoraFin() { return horaFin; }
    public void setHoraFin(LocalTime horaFin) { this.horaFin = horaFin; }

    public List<RecursoDTO> getRecursosReservados() { return recursosReservados; }
    public void setRecursosReservados(List<RecursoDTO> recursosReservados) { this.recursosReservados = recursosReservados; }

    public EstadoReserva getEstado() { return estado; }
    public void setEstado(EstadoReserva estado) { this.estado = estado; }
}
