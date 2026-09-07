package Sistema.util;

import Sistema.DTO.ReportTableDTO;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.util.ArrayList;
import java.util.List;

/**
 * Convierte cualquier TableView de JavaFX en una estructura
 * reutilizable para generar reportes PDF.
 */
public final class ReportTableViewUtil {
    private ReportTableViewUtil() {
    }
    public static <T> ReportTableDTO convertir(String titulo, TableView<T> tableView) {
        if (tableView == null) {
            throw new IllegalArgumentException("La tabla no puede ser nula.");
        }

        List<String> columnas = new ArrayList<>();
        for (TableColumn<T, ?> columna : tableView.getColumns()) {
            columnas.add(columna.getText());
        }

        if (columnas.isEmpty()) {
            throw new IllegalArgumentException("La tabla debe contener al menos una columna.");
        }

        ReportTableDTO reporte = new ReportTableDTO(titulo, columnas);
        for (T elemento : tableView.getItems()) {
            List<String> fila = new ArrayList<>();
            for (TableColumn<T, ?> columna : tableView.getColumns()) {
                Object valor = columna.getCellData(elemento);
                fila.add(valor == null ? "" : valor.toString());
            }
            reporte.agregarFila(fila);
        }

        return reporte;
    }
}