package Sistema.DTO;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.ArrayList;
import java.util.List;

public class Funcionario {
    private String id;
    private String nombre;
    private String telefono;

    // Lista de reservas del funcionario. NO se persiste en funcionarios.json
    // (@JsonIgnore): la fuente real de las reservas es reservas.json, a
    // cargo del modulo de Kris. Este atributo es solo un "casillero" en
    // memoria que se llena en tiempo de ejecucion (ver ReservaLogica)
    // cuando la UI necesita mostrar las reservas de este funcionario.
    // Si se guardara en el JSON del funcionario, quedaria duplicado el
    // dato en dos archivos y con el tiempo se desincronizarian.
    @JsonIgnore
    private List<ReservaDTO> reservas = new ArrayList<>();

    public Funcionario() {
    }

    public Funcionario(String id, String nombre, String telefono) {
        this.id = id;
        this.nombre = nombre;
        this.telefono = telefono;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }

    public List<ReservaDTO> getReservas() { return reservas; }
    public void setReservas(List<ReservaDTO> reservas) {
        this.reservas = (reservas != null) ? reservas : new ArrayList<>();
    }
}
