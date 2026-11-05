package Sistema.Datos;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Properties;

/**
 * Conexion a MySQL. Cada llamada a obtenerConexion() abre una conexion
 * nueva (acuerdo con Kristel): quien la abre la cierra con try-with-resources.
 *
 * Los datos de conexion (bd.url, bd.usuario, bd.clave) se leen de
 * servidor.properties, que no esta en Git (decision 15):
 *   1. Si existe la propiedad del sistema servidor.config, se usa
 *      exactamente esa ruta (Failsafe y Surefire la ponen en
 *      servidor-test.properties, base reservas_test).
 *   2. Si no, se busca servidor.properties en ./, ./backend/ y ../backend/
 *      (el directorio de trabajo cambia segun como se ejecute).
 */
public final class ConexionBD {

    static final String PROPIEDAD_CONFIG = "servidor.config";
    private static final String ARCHIVO = "servidor.properties";

    // ../backend/ es solo para la transicion: mientras frontend dependa de
    // backend, este codigo corre dentro del proceso del Frontend, cuyo
    // directorio de trabajo es frontend/. Se revisa en el paso 7.
    private static final List<Path> UBICACIONES = List.of(
            Path.of(ARCHIVO),
            Path.of("backend", ARCHIVO),
            Path.of("..", "backend", ARCHIVO));

    // se lee una sola vez; si la lectura falla no se guarda y se reintenta
    private static Properties configuracion;

    private ConexionBD() {}

    public static Connection obtenerConexion() throws SQLException {
        Properties config = configuracion();
        return DriverManager.getConnection(
                config.getProperty("bd.url"),
                config.getProperty("bd.usuario"),
                config.getProperty("bd.clave"));
    }

    // synchronized: la usan varios hilos del servidor a la vez
    private static synchronized Properties configuracion() {
        if (configuracion == null) {
            configuracion = cargarConfiguracion(ubicarArchivo());
        }
        return configuracion;
    }

    private static Path ubicarArchivo() {
        String indicado = System.getProperty(PROPIEDAD_CONFIG);
        if (indicado != null && !indicado.isBlank()) {
            return Path.of(indicado);
        }
        for (Path candidata : UBICACIONES) {
            if (Files.isRegularFile(candidata)) {
                return candidata;
            }
        }
        throw new IllegalStateException("No se encontro " + ARCHIVO
                + ". Directorio de trabajo: " + Path.of("").toAbsolutePath()
                + ". Se busco en: " + UBICACIONES
                + ". Copie backend/servidor.properties.example como backend/servidor.properties"
                + " y complete bd.usuario y bd.clave.");
    }

    // de paquete: ConexionBDTest la prueba con archivos temporales.
    // Los mensajes de error nunca incluyen los valores leidos.
    static Properties cargarConfiguracion(Path archivo) {
        if (!Files.isRegularFile(archivo)) {
            throw new IllegalStateException("No existe el archivo de configuracion "
                    + archivo.toAbsolutePath()
                    + ". Copie el .example correspondiente y complete bd.usuario y bd.clave.");
        }
        Properties leidas = new Properties();
        // como Reader UTF-8: load(InputStream) usa ISO-8859-1 y una clave con ñ llegaria mal
        try (Reader lector = Files.newBufferedReader(archivo, StandardCharsets.UTF_8)) {
            leidas.load(lector);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer " + archivo.toAbsolutePath()
                    + " (debe estar guardado en UTF-8).", e);
        }

        Properties config = new Properties();
        config.setProperty("bd.url", obligatoria(leidas, "bd.url", archivo));
        config.setProperty("bd.usuario", obligatoria(leidas, "bd.usuario", archivo));

        // la clave puede estar vacia (root sin contrasena) pero tiene que existir;
        // no se recorta: un espacio puede ser parte de la contrasena
        String clave = leidas.getProperty("bd.clave");
        if (clave == null) {
            throw new IllegalStateException("Falta 'bd.clave' en " + archivo.toAbsolutePath() + ".");
        }
        config.setProperty("bd.clave", clave);
        return config;
    }

    private static String obligatoria(Properties leidas, String nombre, Path archivo) {
        String valor = leidas.getProperty(nombre);
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException("Falta '" + nombre + "' en " + archivo.toAbsolutePath() + ".");
        }
        return valor.trim();
    }
}
