package Sistema.Logica;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import java.util.Base64;
import java.util.HexFormat;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class HashClaveTest {

    // casi todas las pruebas usan pocas iteraciones para ir rapido;
    // solo claveCorrectaVerifica y formatoDelTextoGuardado usan las 600 000 reales
    private static final int POCAS_ITERACIONES = 1000;

    private static final Base64.Encoder BASE64 = Base64.getEncoder();
    private static final String SAL = BASE64.encodeToString(new byte[16]);
    private static final String HASH = BASE64.encodeToString(new byte[32]);

    @Test
    void claveCorrectaVerifica() {
        String guardado = HashClave.generar("admin");

        assertTrue(HashClave.verificar("admin", guardado));
    }

    @Test
    void claveIncorrectaNoVerifica() {
        String guardado = HashClave.generar("admin", POCAS_ITERACIONES);

        assertFalse(HashClave.verificar("otra", guardado));
    }

    @Test
    void laClaveDistingueMayusculas() {
        String guardado = HashClave.generar("admin", POCAS_ITERACIONES);

        assertFalse(HashClave.verificar("Admin", guardado));
    }

    @Test
    void mismaClaveDaHashesDistintosPorLaSalYLosDosVerifican() {
        String primero = HashClave.generar("admin", POCAS_ITERACIONES);
        String segundo = HashClave.generar("admin", POCAS_ITERACIONES);

        assertNotEquals(primero, segundo);
        assertTrue(HashClave.verificar("admin", primero));
        assertTrue(HashClave.verificar("admin", segundo));
    }

    @Test
    void formatoDelTextoGuardado() {
        String guardado = HashClave.generar("admin");
        String[] partes = guardado.split("\\$");

        assertTrue(guardado.startsWith("pbkdf2_sha256$600000$"));
        assertEquals(4, partes.length);
        assertEquals(16, Base64.getDecoder().decode(partes[2]).length);
        assertEquals(32, Base64.getDecoder().decode(partes[3]).length);
        assertTrue(guardado.length() <= 255);
    }

    static Stream<String> textosInvalidos() {
        return Stream.of(
                "texto",                                                  // sin separadores
                "pbkdf2_sha1$1000$" + SAL + "$" + HASH,                   // otro algoritmo
                "pbkdf2_sha256$1000$" + SAL,                              // 3 partes
                "pbkdf2_sha256$1000$" + SAL + "$" + HASH + "$extra",      // 5 partes
                "pbkdf2_sha256$abc$" + SAL + "$" + HASH,                  // iteraciones no numericas
                "pbkdf2_sha256$0$" + SAL + "$" + HASH,                    // iteraciones en cero
                "pbkdf2_sha256$-1$" + SAL + "$" + HASH,                   // iteraciones negativas
                "pbkdf2_sha256$10000001$" + SAL + "$" + HASH,             // mas del limite
                "pbkdf2_sha256$99999999999$" + SAL + "$" + HASH,          // no cabe en int
                "pbkdf2_sha256$1000$%%%%$" + HASH,                        // sal no es Base64
                "pbkdf2_sha256$1000$" + SAL + "$no-es-base64!",           // hash no es Base64
                "pbkdf2_sha256$1000$$" + HASH,                            // sal vacia
                "pbkdf2_sha256$1000$" + SAL + "$" + BASE64.encodeToString(new byte[31])  // hash de 31 bytes
        );
    }

    @ParameterizedTest
    @NullAndEmptySource
    @MethodSource("textosInvalidos")
    void formatoInvalidoDevuelveFalseSinExcepcion(String guardado) {
        assertFalse(HashClave.verificar("admin", guardado));
    }

    @Test
    void claveNulaEnVerificarDevuelveFalse() {
        String guardado = HashClave.generar("admin", POCAS_ITERACIONES);

        assertFalse(HashClave.verificar(null, guardado));
    }

    @ParameterizedTest
    @NullAndEmptySource
    void generarConClaveNulaOVaciaLanzaExcepcion(String clave) {
        assertThrows(IllegalArgumentException.class, () -> HashClave.generar(clave));
    }

    @Test
    void verificaElVectorDePruebaDelRfc7914() {
        // RFC 7914, seccion 11: PBKDF2-HMAC-SHA256, P="passwd", S="salt", c=1.
        // Se toman los primeros 32 bytes de la salida (dkLen=64 en el RFC).
        byte[] esperado = HexFormat.of().parseHex(
                "55ac046e56e3089fec1691c22544b605f94185216dde0465e68b9d57c20dacbc");
        String guardado = "pbkdf2_sha256$1$" + BASE64.encodeToString("salt".getBytes())
                + "$" + BASE64.encodeToString(esperado);

        assertTrue(HashClave.verificar("passwd", guardado));
    }
}
