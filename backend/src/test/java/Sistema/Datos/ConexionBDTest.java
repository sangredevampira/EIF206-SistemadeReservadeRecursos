package Sistema.Datos;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas de unidad de la lectura de configuracion de ConexionBD.
 * No usan MySQL: cada prueba escribe un .properties en una carpeta temporal.
 */
public class ConexionBDTest {

    @TempDir
    Path carpeta;

    private Path escribir(String contenido) throws IOException {
        Path archivo = carpeta.resolve("servidor.properties");
        Files.writeString(archivo, contenido, StandardCharsets.UTF_8);
        return archivo;
    }

    @Test
    void archivoInexistenteLanzaExcepcionConLaRuta() {
        Path inexistente = carpeta.resolve("no-existe.properties");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> ConexionBD.cargarConfiguracion(inexistente));
        assertTrue(ex.getMessage().contains("no-existe.properties"));
    }

    @Test
    void faltaBdUrlNombraLaClaveYNoMuestraLaContrasena() throws IOException {
        Path archivo = escribir("bd.usuario=root\nbd.clave=secretoMuyPrivado\n");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> ConexionBD.cargarConfiguracion(archivo));
        assertTrue(ex.getMessage().contains("bd.url"));
        assertFalse(ex.getMessage().contains("secretoMuyPrivado"));
    }

    @Test
    void faltaBdClaveLanzaExcepcion() throws IOException {
        Path archivo = escribir("bd.url=jdbc:mysql://localhost:3306/reservas_test\nbd.usuario=root\n");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> ConexionBD.cargarConfiguracion(archivo));
        assertTrue(ex.getMessage().contains("bd.clave"));
    }

    @Test
    void claveVaciaSePermite() throws IOException {
        Path archivo = escribir("bd.url=jdbc:mysql://localhost:3306/reservas_test\nbd.usuario=root\nbd.clave=\n");

        Properties config = ConexionBD.cargarConfiguracion(archivo);

        assertEquals("", config.getProperty("bd.clave"));
    }

    @Test
    void quitaEspaciosDeUrlYUsuarioPeroNoDeLaClaveYLeeUtf8() throws IOException {
        Path archivo = escribir("bd.url=jdbc:mysql://localhost:3306/reservas_test   \n"
                + "bd.usuario=root  \n"
                + "bd.clave=ñandú 1 \n");

        Properties config = ConexionBD.cargarConfiguracion(archivo);

        assertEquals("jdbc:mysql://localhost:3306/reservas_test", config.getProperty("bd.url"));
        assertEquals("root", config.getProperty("bd.usuario"));
        assertEquals("ñandú 1 ", config.getProperty("bd.clave"));
    }
}
