package Sistema.DTO;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Contiene la configuración necesaria para solicitar un reporte PDF,
 * sin incluir la lógica utilizada para generarlo.
 */
public class ReportConfigDTO {

    private final String tituloReporte;
    private final String tituloTabla;
    private final String nombreArchivo;
    private final Map<String, String> filtros = new LinkedHashMap<>();

    private String tituloVentana;
    private String tituloGrafico;
    private boolean horizontal;

    public ReportConfigDTO(String tituloReporte, String tituloTabla, String nombreArchivo) {
        this.tituloReporte = tituloReporte;
        this.tituloTabla = tituloTabla;
        this.nombreArchivo = nombreArchivo;
        this.tituloVentana = "Guardar " + tituloReporte.toLowerCase();
    }

    public void agregarFiltro(String nombre, String valor) {
        filtros.put(nombre, valor);
    }

    public String getTituloReporte() {
        return tituloReporte;
    }

    public String getTituloTabla() {
        return tituloTabla;
    }

    public String getNombreArchivo() {
        return nombreArchivo;
    }

    public Map<String, String> getFiltros() {
        return filtros;
    }

    public String getTituloVentana() {
        return tituloVentana;
    }

    public void setTituloVentana(String tituloVentana) {
        this.tituloVentana = tituloVentana;
    }

    public String getTituloGrafico() {
        return tituloGrafico;
    }

    public void setTituloGrafico(String tituloGrafico) {
        this.tituloGrafico = tituloGrafico;
    }

    public boolean isHorizontal() {
        return horizontal;
    }

    public void setHorizontal(boolean horizontal) {
        this.horizontal = horizontal;
    }
}