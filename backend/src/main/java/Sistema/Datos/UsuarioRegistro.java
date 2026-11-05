package Sistema.Datos;

import Sistema.DTO.Rol;

/**
 * Una fila de la tabla usuario, tal como la lee el Backend.
 * Lleva el hash de la clave, por eso vive solo en el Backend: no esta en
 * comun, no llega al classpath del Frontend y nunca viaja por la red
 * (al Frontend solo va LoginResultadoDTO).
 */
public record UsuarioRegistro(String id, String claveHash, Rol rol, boolean activo) {

    // el toString de un record mostraria todos los campos: se oculta el hash
    // por si alguien lo imprime en un mensaje o en la consola del servidor
    @Override
    public String toString() {
        return "UsuarioRegistro[id=" + id + ", rol=" + rol + ", activo=" + activo + "]";
    }
}
