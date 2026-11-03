package Sistema.Service.Red;

import Sistema.DTO.ResultadoOperacionDTO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Apoyo comun de los proxies de Derek (ProxyAuthService, ProxyFuncionarioService,
 * ProxyCategoriaService y ProxyRecursoService).
 *
 * Los ayudantes de ProxyReservaService son privados y de Kristel, por eso no se
 * comparten: esta clase repite la idea en un solo lugar para los cuatro proxies.
 *
 * Regla de excepciones (igual que ProxyBase):
 *  - IllegalArgumentException = el servidor respondio exito=false (regla de negocio,
 *    permiso o datos no validos). Lleva el mensaje para el usuario.
 *  - IllegalStateException = problema tecnico (red, respuesta que no se puede leer).
 *
 * De paquete y sin estado: solo metodos estaticos.
 */
final class ApoyoProxies {

    private ApoyoProxies() {}

    /**
     * Arma los "datos" de una solicitud a partir de pares campo, valor.
     * Un valor null NO se envia (se omite el campo): el servidor lee igual un campo
     * que falta y uno en null (DatosSolicitud.textoOpcional y texto). No se usa
     * Map.of porque lanza NullPointerException con un valor null.
     */
    static Map<String, Object> datos(String... camposYValores) {
        if (camposYValores.length % 2 != 0) {
            // error de programacion del proxy; no debe confundirse con un fallo del servidor
            throw new IllegalStateException("Los datos deben ir en pares campo, valor.");
        }
        Map<String, Object> datos = new LinkedHashMap<>();
        for (int i = 0; i < camposYValores.length; i += 2) {
            String valor = camposYValores[i + 1];
            if (valor != null) {
                datos.put(camposYValores[i], valor);
            }
        }
        return datos;
    }

    /**
     * Convierte los "datos" de la respuesta en una lista (como aLista de ProxyReservaService).
     * Si no es un arreglo o no se puede convertir es un problema tecnico, no una regla
     * de negocio: IllegalStateException.
     *
     * @param json  el ObjectMapper del proxy (ProxyBase.json)
     * @param nombre lo que se esperaba, en plural (por ejemplo "categorías")
     */
    static <T> List<T> aLista(ObjectMapper json, JsonNode nodo, TypeReference<List<T>> tipo, String nombre) {
        if (nodo == null || !nodo.isArray()) {
            throw new IllegalStateException("El servidor no devolvió una lista válida de " + nombre + ".");
        }
        try {
            return json.convertValue(nodo, tipo);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("No fue posible convertir la lista de " + nombre + " recibida.", e);
        }
    }

    /**
     * Para las operaciones que devuelven ResultadoOperacionDTO (registrar, modificar,
     * eliminar, cambiarClave). Si salen bien, el servidor responde con datos en null:
     * se devuelve exito(). Si el servidor responde exito=false, ProxyBase lanza
     * IllegalArgumentException y aqui se convierte en fallo(mensaje), para que el
     * controller se comporte igual que con el Service del Backend.
     * Un IllegalStateException (red caida, respuesta invalida) se deja pasar.
     */
    static ResultadoOperacionDTO resultado(Runnable envio) {
        try {
            envio.run();
            return ResultadoOperacionDTO.exito();
        } catch (IllegalArgumentException e) {
            return ResultadoOperacionDTO.fallo(e.getMessage());
        }
    }

    /**
     * Para las respuestas que siempre deben traer datos (login, buscarRecursoID,
     * generarNuevoId): si llegan en null, la respuesta no sirve.
     *
     * @param queEsperaba por ejemplo "los datos del inicio de sesión"
     */
    static <T> T obligatorio(T valor, String queEsperaba) {
        if (valor == null) {
            throw new IllegalStateException("El servidor no devolvió " + queEsperaba + ".");
        }
        return valor;
    }
}
