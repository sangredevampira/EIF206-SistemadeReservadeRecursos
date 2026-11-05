package Sistema.Red;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

// Mensaje del protocolo (docs/00_contratos.md, sección 2). Los campos nulos se omiten.
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MensajeRed {

    private TipoMensaje tipo;
    private String idSolicitud;
    private String operacion;
    private Boolean exito;
    private String mensaje;
    private JsonNode datos;

    public MensajeRed() {}

    public static MensajeRed solicitud(String operacion, JsonNode datos) {
        MensajeRed m = new MensajeRed();
        m.tipo = TipoMensaje.SOLICITUD;
        m.operacion = operacion;
        m.datos = datos;
        return m;
    }

    public static MensajeRed respuestaExito(String idSolicitud, String operacion, JsonNode datos) {
        MensajeRed m = new MensajeRed();
        m.tipo = TipoMensaje.RESPUESTA;
        m.idSolicitud = idSolicitud;
        m.operacion = operacion;
        m.exito = true;
        m.datos = datos;
        return m;
    }

    public static MensajeRed respuestaError(String idSolicitud, String operacion, String mensaje) {
        MensajeRed m = new MensajeRed();
        m.tipo = TipoMensaje.RESPUESTA;
        m.idSolicitud = idSolicitud;
        m.operacion = operacion;
        m.exito = false;
        m.mensaje = mensaje;
        return m;
    }

    public static MensajeRed evento(String operacion, JsonNode datos) {
        MensajeRed m = new MensajeRed();
        m.tipo = TipoMensaje.EVENTO;
        m.operacion = operacion;
        m.datos = datos;
        return m;
    }

    public TipoMensaje getTipo() { return tipo; }
    public void setTipo(TipoMensaje tipo) { this.tipo = tipo; }

    public String getIdSolicitud() { return idSolicitud; }
    public void setIdSolicitud(String idSolicitud) { this.idSolicitud = idSolicitud; }

    public String getOperacion() { return operacion; }
    public void setOperacion(String operacion) { this.operacion = operacion; }

    public Boolean getExito() { return exito; }
    public void setExito(Boolean exito) { this.exito = exito; }

    public String getMensaje() { return mensaje; }
    public void setMensaje(String mensaje) { this.mensaje = mensaje; }

    public JsonNode getDatos() { return datos; }
    public void setDatos(JsonNode datos) { this.datos = datos; }
}
