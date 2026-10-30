
package Sistema.Red;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ConfiguracionRedTest {

    @TempDir
    Path carpetaTemporal;

    @Test
    void cargaConfiguracionCorrectamente() throws Exception {

        Path archivo = carpetaTemporal.resolve("config.properties");

        Files.writeString(archivo, """
                servidor.host=192.168.1.100
                servidor.puerto=5050
                """);

        ConfiguracionRed config =
                ConfiguracionRed.cargar(archivo);

        assertEquals("192.168.1.100", config.getHost());
        assertEquals(5050, config.getPuerto());
    }

    @Test
    void utilizaValoresPredeterminadosSiNoExisteArchivo()
            throws Exception {

        Path archivo = carpetaTemporal.resolve("inexistente.properties");

        ConfiguracionRed config =
                ConfiguracionRed.cargar(archivo);

        assertEquals("127.0.0.1", config.getHost());
        assertEquals(5050, config.getPuerto());
    }

    @Test
    void rechazaPuertoNoNumerico() throws IOException {

        Path archivo = carpetaTemporal.resolve("config.properties");

        Files.writeString(archivo, """
                servidor.host=localhost
                servidor.puerto=abc
                """);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> ConfiguracionRed.cargar(archivo)
        );

        assertTrue(error.getMessage().contains("numérico"));
    }

    @Test
    void rechazaPuertoFueraDeRango() throws IOException {

        Path archivo = carpetaTemporal.resolve("config.properties");

        Files.writeString(archivo, """
                servidor.host=localhost
                servidor.puerto=70000
                """);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> ConfiguracionRed.cargar(archivo)
        );

        assertTrue(error.getMessage().contains("65535"));
    }

    @Test
    void rechazaHostVacio() throws IOException {

        Path archivo = carpetaTemporal.resolve("config.properties");

        Files.writeString(archivo, """
                servidor.host=
                servidor.puerto=5050
                """);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> ConfiguracionRed.cargar(archivo)
        );

        assertTrue(error.getMessage().contains("vacía"));
    }
}
