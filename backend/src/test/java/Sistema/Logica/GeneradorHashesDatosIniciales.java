package Sistema.Logica;

/**
 * Utilidad de desarrollo: imprime el INSERT de la tabla usuario para
 * bd/02_datos_iniciales.sql, con las claves del Proyecto 1 y el mismo
 * HashClave que despues verifica el login.
 *
 * Esta en src/test: no entra al jar del Backend ni se ejecuta en produccion.
 * Surefire no la corre porque no es una prueba (su nombre no termina en Test).
 * Cada corrida da hashes distintos por la sal aleatoria: se genera una vez y
 * se pega en el script.
 *
 * Ejecutar desde IntelliJ (flecha verde del main) o, desde la raiz:
 *   mvn -pl backend test-compile
 *   java -cp "backend/target/classes;backend/target/test-classes" Sistema.Logica.GeneradorHashesDatosIniciales
 */
public class GeneradorHashesDatosIniciales {

    // id, clave del Proyecto 1 (usuarios.json), rol
    private static final String[][] USUARIOS = {
            {"admin", "admin", "ADMINISTRADOR"},
            {"44444", "44444", "FUNCIONARIO"},
            {"123", "1234", "FUNCIONARIO"},
            {"77777", "gigi", "FUNCIONARIO"},
            {"222222", "222222", "FUNCIONARIO"},
    };

    public static void main(String[] args) {
        System.out.println("INSERT INTO usuario (id_usuario, clave_hash, rol, activo) VALUES");
        for (int i = 0; i < USUARIOS.length; i++) {
            String[] usuario = USUARIOS[i];
            String fin = (i < USUARIOS.length - 1) ? "," : ";";
            System.out.println("    ('" + usuario[0] + "', '" + HashClave.generar(usuario[1]) + "', '"
                    + usuario[2] + "', 1)" + fin);
        }
    }
}
