package Sistema.Datos;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas de unidad de GeneradorIdsBD, sin MySQL: la lista cerrada de
 * combinaciones y el formato del ID. El SQL se prueba en GeneradorIdsBDIT.
 */
public class GeneradorIdsBDTest {

    @Test
    void aceptaLasDosCombinacionesPermitidas() {
        assertDoesNotThrow(() -> GeneradorIdsBD.validarCombinacion("reserva", "id_reserva", "RES"));
        assertDoesNotThrow(() -> GeneradorIdsBD.validarCombinacion("categoria", "id_categoria", "CAT"));
    }

    @ParameterizedTest
    @CsvSource({
            "reserva, id_reserva, CAT",                      // prefijo de otra tabla
            "categoria, id_categoria, RES",                  // prefijo de otra tabla
            "usuario, id_usuario, USR",                      // tabla fuera de la lista
            "Reserva, id_reserva, RES",                      // la comparacion es exacta
            "'reserva; DROP TABLE reserva', id_reserva, RES", // intento de inyeccion
            "reserva, 'id_reserva) FROM usuario --', RES"    // intento de inyeccion
    })
    void rechazaCombinacionesNoPermitidas(String tabla, String columna, String prefijo) {
        assertThrows(IllegalArgumentException.class,
                () -> GeneradorIdsBD.validarCombinacion(tabla, columna, prefijo));
    }

    @Test
    void rechazaValoresNulos() {
        assertThrows(IllegalArgumentException.class,
                () -> GeneradorIdsBD.validarCombinacion(null, "id_reserva", "RES"));
        assertThrows(IllegalArgumentException.class,
                () -> GeneradorIdsBD.validarCombinacion("reserva", null, "RES"));
        assertThrows(IllegalArgumentException.class,
                () -> GeneradorIdsBD.validarCombinacion("reserva", "id_reserva", null));
    }

    @Test
    void conexionNulaLanzaIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> GeneradorIdsBD.siguienteId(null, "reserva", "id_reserva", "RES"));
    }

    @ParameterizedTest
    @CsvSource({
            "0, RES-000001",        // tabla vacia
            "11, RES-000012",
            "999998, RES-999999"    // el ultimo posible
    })
    void formateaElSiguienteNumeroConSeisDigitos(long ultimo, String esperado) {
        assertEquals(esperado, GeneradorIdsBD.formatear("RES", ultimo));
    }

    @Test
    void despuesDe999999LanzaIllegalStateException() {
        assertThrows(IllegalStateException.class, () -> GeneradorIdsBD.formatear("RES", 999_999));
    }
}
