package Sistema.DTO;

import Sistema.DTO.RecursoDTO;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public class ReservaDTO {
    private String idReserva;
    private String idFuncionario;       // ID compartido con Usuario/Funcionario
    private String descripcionActividad;    // descripcion de la actividad
    private LocalDate fechaReserva;
    private LocalTime horaInicio;
    private LocalTime horaFinal;
    private List<RecursoDTO> recursosReservados;
    private EstadoReserva estado;   // se debe tener unicamente dos valores (ACTIVA/CANCELADA)

    public ReservaDTO() {}

    public ReservaDTO(EstadoReserva estado, List<RecursoDTO> recursosReservados, LocalTime horaFinal, LocalTime horaInicio,
                      LocalDate fechaReserva, String descripcionActividad, String idFuncionario, String idReserva) {
        this.estado = estado;
        this.recursosReservados = recursosReservados;
        this.horaFinal = horaFinal;
        this.horaInicio = horaInicio;
        this.fechaReserva = fechaReserva;
        this.descripcionActividad = descripcionActividad;
        this.idFuncionario = idFuncionario;
        this.idReserva = idReserva;
    }

    public String getIdReserva() { return idReserva; }
    public void setIdReserva(String idReserva) { this.idReserva = idReserva; }

    public String getIdFuncionario() { return idFuncionario; }
    public void setIdFuncionario(String idFuncionario) { this.idFuncionario = idFuncionario; }

    public String getDescripcionActividad() { return descripcionActividad; }
    public void setDescripcionActividad(String descripcionActividad) { this.descripcionActividad = descripcionActividad; }

    public LocalDate getFechaReserva() { return fechaReserva; }
    public void setFechaReserva(LocalDate fechaReserva) { this.fechaReserva = fechaReserva; }

    public LocalTime getHoraInicio() { return horaInicio; }
    public void setHoraInicio(LocalTime horaInicio) { this.horaInicio = horaInicio; }

    public LocalTime getHoraFinal() { return horaFinal; }
    public void setHoraFinal(LocalTime horaFinal) { this.horaFinal = horaFinal; }

    public List<RecursoDTO> getRecursosReservados() { return recursosReservados; }
    public void setRecursosReservados(List<RecursoDTO> recursosReservados) { this.recursosReservados = recursosReservados; }

    public EstadoReserva getEstado() { return estado; }
    public void setEstado(EstadoReserva estado) { this.estado = estado; }
}
