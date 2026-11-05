package Sistema.DTO;

import java.util.List;
import java.util.Map;

// Programacion de actividades de un rango de fechas: las horas del dia ("HH:mm") y, por fecha
// ("AAAA-MM-DD"), el texto de cada celda indexado por hora.
public class ProgramacionActividadesDTO {

    private List<String> horas;
    private Map<String, Map<String, String>> actividadesPorFecha;

    public ProgramacionActividadesDTO() {
    }

    public List<String> getHoras() { return horas; }
    public void setHoras(List<String> horas) { this.horas = horas; }

    public Map<String, Map<String, String>> getActividadesPorFecha() { return actividadesPorFecha; }
    public void setActividadesPorFecha(Map<String, Map<String, String>> actividadesPorFecha) {
        this.actividadesPorFecha = actividadesPorFecha;
    }
}
