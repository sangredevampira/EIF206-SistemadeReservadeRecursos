package Sistema.Logica;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Hash de claves con PBKDF2WithHmacSHA256 y sal aleatoria (decision 4).
 * Un hash es de una sola via: la clave nunca se descifra, se vuelve a
 * calcular el hash con la misma sal y se compara.
 *
 * Formato guardado en usuario.clave_hash (maximo 94 caracteres):
 *   pbkdf2_sha256$<iteraciones>$<sal en Base64>$<hash en Base64>
 * La sal no es secreta; las iteraciones se guardan para poder subirlas
 * mas adelante sin invalidar los hashes viejos.
 */
public final class HashClave {

    static final String ALGORITMO = "pbkdf2_sha256";
    static final int ITERACIONES = 600_000;          // recomendacion OWASP para PBKDF2-HMAC-SHA256
    static final int MAX_ITERACIONES = 10_000_000;   // un dato danado no debe trabar el login

    private static final String ALGORITMO_JAVA = "PBKDF2WithHmacSHA256";
    private static final int BYTES_SAL = 16;
    private static final int BYTES_HASH = 32;
    private static final SecureRandom ALEATORIO = new SecureRandom();   // seguro para varios hilos

    private HashClave() {}

    public static String generar(String clave) {
        return generar(clave, ITERACIONES);
    }

    // de paquete: las pruebas lo usan con pocas iteraciones para ir rapido
    static String generar(String clave, int iteraciones) {
        if (clave == null || clave.isEmpty()) {
            throw new IllegalArgumentException("La clave no puede estar vacia.");
        }
        byte[] sal = new byte[BYTES_SAL];
        ALEATORIO.nextBytes(sal);
        byte[] hash = derivar(clave, sal, iteraciones);

        Base64.Encoder base64 = Base64.getEncoder();
        return ALGORITMO + "$" + iteraciones + "$" + base64.encodeToString(sal) + "$" + base64.encodeToString(hash);
    }

    /**
     * Devuelve true solo si la clave corresponde al hash guardado. Cualquier
     * dato invalido (nulo, formato, iteraciones, Base64, longitudes) devuelve
     * false, sin excepcion ni detalles: para el usuario es igual que una
     * clave incorrecta.
     */
    public static boolean verificar(String clave, String guardado) {
        if (clave == null || clave.isEmpty() || guardado == null) {
            return false;
        }
        String[] partes = guardado.split("\\$", -1);
        if (partes.length != 4 || !ALGORITMO.equals(partes[0])) {
            return false;
        }
        try {
            int iteraciones = Integer.parseInt(partes[1]);
            if (iteraciones <= 0 || iteraciones > MAX_ITERACIONES) {
                return false;
            }
            Base64.Decoder base64 = Base64.getDecoder();
            byte[] sal = base64.decode(partes[2]);
            byte[] esperado = base64.decode(partes[3]);
            if (sal.length == 0 || esperado.length != BYTES_HASH) {
                return false;
            }
            // tiempo constante: no revela cuantos bytes coinciden
            return MessageDigest.isEqual(derivar(clave, sal, iteraciones), esperado);
        } catch (IllegalArgumentException e) {   // incluye NumberFormatException y Base64 invalido
            return false;
        }
    }

    private static byte[] derivar(String clave, byte[] sal, int iteraciones) {
        char[] caracteres = clave.toCharArray();
        PBEKeySpec spec = new PBEKeySpec(caracteres, sal, iteraciones, BYTES_HASH * 8);
        try {
            return SecretKeyFactory.getInstance(ALGORITMO_JAVA).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            // no pasa en un JDK estandar: es un problema de instalacion, no de datos
            throw new IllegalStateException(ALGORITMO_JAVA + " no esta disponible en este JDK.", e);
        } finally {
            spec.clearPassword();
            Arrays.fill(caracteres, '\0');
        }
    }
}
