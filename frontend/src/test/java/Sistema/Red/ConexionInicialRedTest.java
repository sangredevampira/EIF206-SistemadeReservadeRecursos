
package Sistema.Red;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ConexionInicialRedTest {

    @TempDir
    Path carpetaTemporal;

    @AfterEach
    void limpiar() {
        ClienteRed.getInstancia().cerrar();
    }

    @Test
    void conectaCorrectamenteConServidorDisponible()
            throws Exception {

        try (ServerSocket servidor = new ServerSocket(0)) {

            ConfiguracionRed config =
                    crearConfiguracion(servidor.getLocalPort());

            ConexionInicialRed.conectar(config);

            assertTrue(
                    ClienteRed.getInstancia().estaConectado()
            );
        }
    }

    @Test
    void detectaCuandoServidorNoEstaDisponible()
            throws Exception {

        int puerto;

        // Obtener un puerto libre y cerrarlo para
        // simular que no existe un servidor escuchando.
        try (ServerSocket temporal = new ServerSocket(0)) {
            puerto = temporal.getLocalPort();
        }

        ConfiguracionRed config = crearConfiguracion(puerto);

        assertThrows(
                IOException.class,
                () -> ConexionInicialRed.conectar(config)
        );

        assertFalse(
                ClienteRed.getInstancia().estaConectado()
        );
    }

    private ConfiguracionRed crearConfiguracion(int puerto)
            throws Exception {

        Path archivo = carpetaTemporal.resolve(
                "config.properties"
        );

        Files.writeString(
                archivo,
                "servidor.host=127.0.0.1\n"
                        + "servidor.puerto=" + puerto + "\n"
        );

        return ConfiguracionRed.cargar(archivo);
    }
}
