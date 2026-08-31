package Sistema.util;

import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;

/**
 * Permite seleccionar el archivo de destino para un reporte PDF
 * y garantiza que el nombre termine con la extensión .pdf.
 */
public final class SelectorArchivoPdfUtil {

    private SelectorArchivoPdfUtil() {}

    public static File seleccionar(Window ventana, String titulo, String nombreInicial) {
        FileChooser selector = new FileChooser();
        selector.setTitle(titulo);
        selector.getExtensionFilters().add(new FileChooser.ExtensionFilter("Documento PDF", "*.pdf"));
        selector.setInitialFileName(nombreInicial);

        File archivo = selector.showSaveDialog(ventana);

        if (archivo != null && !archivo.getName().toLowerCase().endsWith(".pdf")) {
            archivo = new File(archivo.getAbsolutePath() + ".pdf");
        }
        return archivo;
    }
}
