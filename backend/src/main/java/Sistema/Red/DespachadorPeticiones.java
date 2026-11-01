package Sistema.Red;

import Sistema.DTO.ResultadoOperacionDTO;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

// Recibe una SOLICITUD y devuelve SIEMPRE una RESPUESTA con el mismo idSolicitud.
public class DespachadorPeticiones {

    private static final Logger LOG = Logger.getLogger(DespachadorPeticiones.class.getName());

    // ConcurrentHashMap: varios hilos (uno por cliente) lo leen a la vez sin problema
    private final Map<String, Manejador> manejadores = new ConcurrentHashMap<>();

    public void registrar(String operacion, Manejador manejador) { manejadores.put(operacion, manejador); }

    public MensajeRed despachar(MensajeRed solicitud, ContextoSesion contexto) {
        String id = solicitud.getIdSolicitud();
        String operacion = solicitud.getOperacion();

        if (solicitud.getTipo() != TipoMensaje.SOLICITUD)
            return error(id, operacion, "El mensaje no es una solicitud.");

        if (operacion == null)
            return error(id, operacion, "Falta la operación.");

        if (!contexto.estaAutenticado() && !esOperacionPublica(operacion))
            return error(id, operacion, "Debe iniciar sesión.");

        Manejador manejador = manejadores.get(operacion);
        if (manejador == null)
            return error(id, operacion, "Operación no reconocida.");

        try {
            Object resultado = manejador.manejar(normalizar(solicitud.getDatos()), contexto);

            // Un ResultadoOperacionDTO con exito=false se responde como error
            if (resultado instanceof ResultadoOperacionDTO r) {
                if (!r.isExito())
                    return error(id, operacion, r.getMensaje());
                return MensajeRed.respuestaExito(id, operacion, null);
            }
            JsonNode datos = (resultado == null) ? null : JsonRed.aNodo(resultado);
            return MensajeRed.respuestaExito(id, operacion, datos);

        } catch (IllegalArgumentException e) {
            // Fallo de regla de negocio: el mensaje es para el usuario
            return error(id, operacion, e.getMessage());
        } catch (Exception e) {
            // Cualquier otra cosa: se registra aquí y el usuario recibe un texto genérico
            LOG.log(Level.SEVERE, "Error al atender " + operacion, e);
            return error(id, operacion, "Error interno del servidor. Intente de nuevo.");
        }
    }

    // Antes del login solo se permiten estas dos
    private boolean esOperacionPublica(String operacion) {
        return Operaciones.AUTH_LOGIN.equals(operacion)
                || Operaciones.AUTH_CAMBIAR_CLAVE.equals(operacion);
    }

    // "datos" ausente o con null llega igual al manejador: como null
    private JsonNode normalizar(JsonNode datos) {
        if (datos == null || datos.isNull() || datos.isMissingNode()) {
            return null;
        }
        return datos;
    }

    private MensajeRed error(String id, String operacion, String mensaje) {
        String texto = (mensaje == null || mensaje.isBlank()) ? "No se pudo completar la solicitud." : mensaje;
        return MensajeRed.respuestaError(id, operacion, texto);
    }
}