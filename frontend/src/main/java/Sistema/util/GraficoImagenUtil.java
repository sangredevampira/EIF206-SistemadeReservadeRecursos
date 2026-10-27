package Sistema.util;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Clase utilitaria encargada de convertir componentes gráficos
 * de JavaFX en imágenes PNG representadas como un arreglo de bytes,
 * para incorporarlas en reportes PDF.
 */

public final class GraficoImagenUtil {

    private GraficoImagenUtil() {}

    public static byte[] convertirAPng(Node grafico) throws IOException {
        if (grafico == null) throw new IllegalArgumentException("El gráfico no puede ser nulo.");
        WritableImage imagenJavaFx = grafico.snapshot(new SnapshotParameters(), null);
        try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            boolean imagenCreada = ImageIO.write(SwingFXUtils.fromFXImage(imagenJavaFx, null), "png", salida);
            if (!imagenCreada) throw new IOException("No fue posible convertir el gráfico a PNG.");
            return salida.toByteArray();
        }
    }
}
