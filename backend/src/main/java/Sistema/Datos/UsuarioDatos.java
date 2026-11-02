package Sistema.Datos;

import Sistema.DTO.Rol;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Acceso a la tabla usuario con JDBC.
 *
 * Cada operacion tiene dos versiones (acuerdo con Kristel):
 *   - la que RECIBE la conexion no la cierra y lanza SQLException: para
 *     transacciones y para las pruebas con rollback;
 *   - la que no la recibe abre y cierra la suya con ConexionBD y convierte
 *     la SQLException en un mensaje legible. El detalle tecnico queda en
 *     la causa, nunca en el mensaje que ve el usuario.
 */
public class UsuarioDatos {

    // public: las clases de Logica y las demas de Datos tambien lo usan
    public static final String MENSAJE_ERROR_BD ="No se pudo acceder a la base de datos. Intente de nuevo más tarde.";

    // --- buscar por ID ---

    // devuelve el ID tal como esta guardado (la collation no distingue mayusculas)
    public UsuarioRegistro buscarPorId(String id) {
        try (Connection conexion = ConexionBD.obtenerConexion()) {
            return buscarPorId(conexion, id);
        } catch (SQLException e) {
            throw new IllegalStateException(MENSAJE_ERROR_BD, e);
        }
    }

    public UsuarioRegistro buscarPorId(Connection conexion, String id) throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement(
                "SELECT id_usuario, clave_hash, rol, activo FROM usuario WHERE id_usuario = ?")) {
            sentencia.setString(1, id);
            try (ResultSet resultado = sentencia.executeQuery()) {
                if (!resultado.next()) {
                    return null;
                }
                return new UsuarioRegistro(
                        resultado.getString("id_usuario"),
                        resultado.getString("clave_hash"),
                        Rol.valueOf(resultado.getString("rol")),
                        resultado.getBoolean("activo"));
            }
        }
    }

    // --- insertar (siempre activo; solo dentro de la transaccion del registro) ---
    // Solo existe con conexion: el usuario de un funcionario se guarda junto
    // con su fila de funcionario (FuncionarioLogica.registrar). Error que
    // interpreta FuncionarioLogica: 1062 (ID repetido, sin distinguir mayusculas).

    public void insertar(Connection conexion, String id, String claveHash, Rol rol) throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement(
                "INSERT INTO usuario (id_usuario, clave_hash, rol, activo) VALUES (?, ?, ?, 1)")) {
            sentencia.setString(1, id);
            sentencia.setString(2, claveHash);
            sentencia.setString(3, rol.name());
            sentencia.executeUpdate();
        }
    }

    // --- desactivar el usuario de un funcionario (baja logica) ---
    // Solo existe con conexion: va en la misma transaccion que la baja del
    // funcionario. "AND rol = 'FUNCIONARIO'": por aqui nunca se desactiva al
    // administrador. Sin "AND activo = 1": Connector/J cuenta las filas
    // encontradas, asi que da true aunque ya estuviera inactivo.

    // true si encontro el usuario (y es un funcionario)
    public boolean desactivarFuncionario(Connection conexion, String id) throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement(
                "UPDATE usuario SET activo = 0 WHERE id_usuario = ? AND rol = 'FUNCIONARIO'")) {
            sentencia.setString(1, id);
            return sentencia.executeUpdate() == 1;
        }
    }

    // --- actualizar el hash de la clave ---

    // true si cambio exactamente una fila
    public boolean actualizarClaveHash(String id, String claveHash) {
        try (Connection conexion = ConexionBD.obtenerConexion()) {
            return actualizarClaveHash(conexion, id, claveHash);
        } catch (SQLException e) {
            throw new IllegalStateException(MENSAJE_ERROR_BD, e);
        }
    }

    public boolean actualizarClaveHash(Connection conexion, String id, String claveHash) throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement(
                "UPDATE usuario SET clave_hash = ? WHERE id_usuario = ?")) {
            sentencia.setString(1, claveHash);
            sentencia.setString(2, id);
            return sentencia.executeUpdate() == 1;
        }
    }
}
