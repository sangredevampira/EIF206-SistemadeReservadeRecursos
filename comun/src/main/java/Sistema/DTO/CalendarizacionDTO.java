package Sistema.DTO;

import java.util.List;

// Matriz de disponibilidad de una categoria en una fecha: las horas del dia ("HH:mm")
// y, por cada recurso, su estado en cada una de esas horas.
public class CalendarizacionDTO {

    private List<String> horas;
    private List<DisponibilidadRecursoDTO> recursos;

    public CalendarizacionDTO() {
    }

    public List<String> getHoras() { return horas; }
    public void setHoras(List<String> horas) { this.horas = horas; }

    public List<DisponibilidadRecursoDTO> getRecursos() { return recursos; }
    public void setRecursos(List<DisponibilidadRecursoDTO> recursos) { this.recursos = recursos; }
}
