package Sistema.DTO;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ReportTableDTO {

    private final String titulo;
    private final List<String> columnas;
    private final List<List<String>> filas;

    public ReportTableDTO(String titulo, List<String> columnas) {
        this.titulo = titulo;
        this.columnas = new ArrayList<>(columnas);
        this.filas = new ArrayList<>();
    }

    public void agregarFila(List<String> valores) {

        if (valores.size() != columnas.size()) {
            throw new IllegalArgumentException("La cantidad de valores de la fila no coincide " + "con la cantidad de columnas.");
        }
        filas.add(new ArrayList<>(valores));
    }

    public void agregarFila(String... valores) {
        agregarFila(Arrays.asList(valores));
    }

    public String getTitulo() {
        return titulo;
    }

    public List<String> getColumnas() {
        return columnas;
    }

    public List<List<String>> getFilas() {
        return filas;
    }
}
