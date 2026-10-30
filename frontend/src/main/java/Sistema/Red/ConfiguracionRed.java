
package Sistema.Red;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class ConfiguracionRed {

    private final String host;
    private final int puerto;

    private ConfiguracionRed(String host, int puerto) {
        this.host = host;
        this.puerto = puerto;
    }

    public String getHost() {
        return host;
    }

    public int getPuerto() {
        return puerto;
    }

    public static ConfiguracionRed cargar() throws IOException {

        // Permite ejecutar Maven desde la raiz
        // o directamente desde el modulo frontend.

        Path ruta = Path.of("frontend", "config.properties");

        if (!Files.exists(ruta)) {
            ruta = Path.of("config.properties");
        }

        return cargar(ruta);
    }

    public static ConfiguracionRed cargar(Path ruta)
            throws IOException {

        Properties propiedades = new Properties();

        if (Files.exists(ruta)) {
            try (InputStream entrada =
                         Files.newInputStream(ruta)) {

                propiedades.load(entrada);
            }
        }

        String host = propiedades
                .getProperty("servidor.host", "127.0.0.1")
                .trim();

        String textoPuerto = propiedades
                .getProperty("servidor.puerto", "5050")
                .trim();

        if (host.isBlank()) {
            throw new IllegalArgumentException(
                    "La dirección del servidor no puede estar vacía."
            );
        }

        int puerto;

        try {
            puerto = Integer.parseInt(textoPuerto);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "El puerto del servidor debe ser numérico.",
                    e
            );
        }

        if (puerto < 1 || puerto > 65535) {
            throw new IllegalArgumentException(
                    "El puerto debe estar entre 1 y 65535."
            );
        }

        return new ConfiguracionRed(host, puerto);
    }
}
