package Sistema.Datos;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

/**
 * Calcula el siguiente ID con formato PREFIJO-000001 (acuerdo con Kristel).
 *
 * Como usarlo:
 *   - dentro del candado de Java que corresponda (CANDADO_RESERVAS para RES-)
 *   - con la misma conexion de la transaccion (setAutoCommit(false)) que hace
 *     el INSERT, y despues commit o rollback. Esta clase no cierra la conexion.
 *
 * Tres protecciones contra IDs repetidos:
 *   1. el candado de Java pone en fila a los hilos del servidor;
 *   2. SELECT ... FOR UPDATE siempre lee lo ultimo confirmado (un SELECT
 *      normal en REPEATABLE READ podria leer una "foto" vieja);
 *   3. la PK de la tabla, como ultima proteccion.
 */
public final class GeneradorIdsBD {

    // Lista cerrada de (tabla, columna, prefijo). Tabla y columna se concatenan
    // en el SQL porque un "?" no puede reemplazar nombres, solo valores: por eso
    // solo pasan los que son exactamente iguales a estas constantes.
    private static final Set<List<String>> PERMITIDAS = Set.of(
            List.of("reserva", "id_reserva", "RES"),
            List.of("categoria", "id_categoria", "CAT"));

    static final int MAXIMO = 999_999;

    private GeneradorIdsBD() {}

    public static String siguienteId(Connection conexion, String tabla, String columnaId, String prefijo)
            throws SQLException {
        validarCombinacion(tabla, columnaId, prefijo);
        if (conexion == null) {
            throw new IllegalArgumentException("La conexion no puede ser nula.");
        }
        // sin transaccion, FOR UPDATE suelta el bloqueo al terminar la consulta
        if (conexion.getAutoCommit()) {
            throw new IllegalStateException("siguienteId debe llamarse dentro de una transaccion"
                    + " (setAutoCommit(false)), con la misma conexion del INSERT.");
        }

        // solo cuentan los IDs con el formato exacto PREFIJO-000000; los demas se ignoran
        String sql = "SELECT COALESCE(MAX(CAST(SUBSTRING(" + columnaId + ", ?) AS UNSIGNED)), 0)"
                + " FROM " + tabla
                + " WHERE " + columnaId + " REGEXP ?"
                + " FOR UPDATE";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setInt(1, prefijo.length() + 2);           // SQL cuenta desde 1 y salta el guion
            sentencia.setString(2, "^" + prefijo + "-[0-9]{6}$");
            try (ResultSet resultado = sentencia.executeQuery()) {
                resultado.next();
                return formatear(prefijo, resultado.getLong(1));
            }
        }
    }

    // de paquete: GeneradorIdsBDTest la prueba sin MySQL
    static void validarCombinacion(String tabla, String columnaId, String prefijo) {
        if (tabla == null || columnaId == null || prefijo == null
                || !PERMITIDAS.contains(List.of(tabla, columnaId, prefijo))) {
            throw new IllegalArgumentException("Combinacion no permitida para generar IDs: "
                    + tabla + ", " + columnaId + ", " + prefijo + ".");
        }
    }

    // de paquete: GeneradorIdsBDTest la prueba sin MySQL
    static String formatear(String prefijo, long ultimo) {
        // mas de 6 digitos romperia el formato y el REGEXP lo ignoraria en la
        // siguiente llamada, que chocaria con la PK: mejor fallar claramente
        if (ultimo >= MAXIMO) {
            throw new IllegalStateException("Se agotaron los IDs con prefijo " + prefijo + "-.");
        }
        return String.format("%s-%06d", prefijo, ultimo + 1);
    }
}
