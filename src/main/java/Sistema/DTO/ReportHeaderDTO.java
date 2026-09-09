package Sistema.DTO;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

public class ReportHeaderDTO {

    private final String nombreSistema;
    private final String titulo;
    private final LocalDateTime fechaGeneracion;
    private final String usuario;
    private final Map<String, String> filtros;

    public ReportHeaderDTO(String nombreSistema, String titulo, String usuario) {
        this.nombreSistema = nombreSistema;
        this.titulo = titulo;
        this.usuario = usuario;
        this.fechaGeneracion = LocalDateTime.now();
        this.filtros = new LinkedHashMap<>();
    }

    public void agregarFiltro(String nombre, String valor) {
        filtros.put(nombre, valor);
    }

    public String getNombreSistema() {
        return nombreSistema;
    }

    public String getTitulo() {
        return titulo;
    }

    public LocalDateTime getFechaGeneracion() {
        return fechaGeneracion;
    }

    public String getUsuario() {
        return usuario;
    }

    public Map<String, String> getFiltros() {
        return filtros;
    }
}