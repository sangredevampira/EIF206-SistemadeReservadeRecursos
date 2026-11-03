package Sistema.DTO;

import java.util.List;

// Una fila de la calendarizacion: un recurso y su estado en cada hora del dia
// (la lista de estados va alineada con la lista de horas de CalendarizacionDTO).
public class DisponibilidadRecursoDTO {

    private RecursoDTO recurso;
    private List<String> estados;

    public DisponibilidadRecursoDTO() {
    }

    public RecursoDTO getRecurso() { return recurso; }
    public void setRecurso(RecursoDTO recurso) { this.recurso = recurso; }

    public List<String> getEstados() { return estados; }
    public void setEstados(List<String> estados) { this.estados = estados; }
}
