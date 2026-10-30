package Sistema.Service.IAService;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * resultado de interpretar, con IA (Gemini), la frase en lenguaje
 * natural que escribió el funcionario. El controlador usa estos datos
 * para llenar el formulario de reserva; el usuario puede modificarlos
 * antes de presionar "Reservar".
 */
public class ReservaExtraida {

    private String actividad = "";
    private LocalDate fecha;
    private LocalTime horaInicio;
    private LocalTime horaFin;

    // Descripciones de categoría tal como las devolvió el modelo (texto libre).
    // ReservasViewController se encarga de emparejarlas contra las
    // CategoriaDTO reales del ListView.
    private List<String> categoriasSugeridas = new ArrayList<>();

    public String getActividad() { return actividad; }
    public void setActividad(String actividad) { this.actividad = actividad; }

    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }

    public LocalTime getHoraInicio() { return horaInicio; }
    public void setHoraInicio(LocalTime horaInicio) { this.horaInicio = horaInicio; }

    public LocalTime getHoraFin() { return horaFin; }
    public void setHoraFin(LocalTime horaFin) { this.horaFin = horaFin; }

    public List<String> getCategoriasSugeridas() { return categoriasSugeridas; }
    public void setCategoriasSugeridas(List<String> categoriasSugeridas) { this.categoriasSugeridas = categoriasSugeridas; }
}