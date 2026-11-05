package Sistema.Red;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Lectura de los "datos" de una solicitud para los manejadores de Derek
 * (ManejadorFuncionarios, ManejadorCategorias y ManejadorRecursos).
 *
 * Mismo criterio que el ayudante texto() de ManejadorAuth (que es privado y
 * de Kristel, por eso no se comparte): si los datos no sirven, se lanza
 * IllegalArgumentException con MENSAJE_INVALIDOS y el despachador responde
 * exito=false con ese texto.
 *
 * De paquete y sin estado: solo metodos estaticos.
 */
final class DatosSolicitud {

    static final String MENSAJE_INVALIDOS = "Los datos de la solicitud no son válidos.";

    private DatosSolicitud() {}

    // Campo de texto obligatorio. Falta, es null o no es un valor simple -> datos no validos.
    static String texto(JsonNode datos, String campo) {
        String valor = textoOpcional(datos, campo);
        if (valor == null) {
            throw new IllegalArgumentException(MENSAJE_INVALIDOS);
        }
        return valor;
    }

    // Campo de texto opcional (un filtro): si falta o es null devuelve null.
    // Si viene un objeto o una lista en lugar de un valor simple, los datos no son validos.
    static String textoOpcional(JsonNode datos, String campo) {
        JsonNode valor = (datos == null) ? null : datos.get(campo);
        if (valor == null || valor.isNull()) {
            return null;
        }
        if (!valor.isValueNode()) {
            throw new IllegalArgumentException(MENSAJE_INVALIDOS);
        }
        return valor.asText();
    }

    // "datos" completo como un DTO (por ejemplo, Funcionario en FUNC_CREAR).
    // Sin datos, con un tipo equivocado o con una propiedad que el DTO no tiene -> datos no validos.
    static <T> T objeto(JsonNode datos, Class<T> clase) {
        if (datos == null || !datos.isObject()) {
            throw new IllegalArgumentException(MENSAJE_INVALIDOS);
        }
        try {
            return JsonRed.desde(datos, clase);
        } catch (IllegalArgumentException e) {
            // JsonRed usa el mismo texto sin punto final: se unifica con ManejadorAuth
            throw new IllegalArgumentException(MENSAJE_INVALIDOS, e);
        }
    }
}
