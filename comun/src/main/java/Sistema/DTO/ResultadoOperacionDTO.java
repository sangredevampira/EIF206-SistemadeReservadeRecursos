package Sistema.DTO;

// resultado de una operacion (registrar, modificar, eliminar, cambiar clave).
// Reemplaza las clases Resultado* anidadas del Proyecto 1.
// Viaja como JSON: Jackson necesita el constructor vacio y los getters/setters.

public class ResultadoOperacionDTO {
    private boolean exito;
    private String mensaje;

    public ResultadoOperacionDTO() {}

    public ResultadoOperacionDTO(boolean exito, String mensaje) {
        this.exito = exito;
        this.mensaje = mensaje;
    }

    public static ResultadoOperacionDTO exito() {
        return new ResultadoOperacionDTO(true, null);
    }

    public static ResultadoOperacionDTO fallo(String mensaje) {
        return new ResultadoOperacionDTO(false, mensaje);
    }

    public boolean isExito() { return exito; }
    public void setExito(boolean exito) { this.exito = exito; }
    public String getMensaje() { return mensaje; }
    public void setMensaje(String mensaje) { this.mensaje = mensaje; }
}
