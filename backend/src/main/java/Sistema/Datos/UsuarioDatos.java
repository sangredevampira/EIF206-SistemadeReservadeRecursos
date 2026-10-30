package Sistema.Datos;

import Sistema.DTO.Funcionario;
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
 *     transacciones (paso 5) y para las pruebas con rollback;
 *   - la que no la recibe abre y cierra la suya con ConexionBD y convierte
 *     la SQLException en un mensaje legible. El detalle tecnico queda en
 *     la causa, nunca en el mensaje que ve el usuario.
 */
public class UsuarioDatos {

    static final String MENSAJE_ERROR_BD = "No se pudo acceder a la base de datos. Intente de nuevo más tarde.";

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

    // --- funcionario asociado ---
    // TEMPORAL: se pasa a FuncionarioDatos (JDBC) en el paso 5. Esta aqui porque
    // FuncionarioDatos todavia es JSON y el login ya lee MySQL.

    public Funcionario buscarFuncionarioAsociado(String id) {
        try (Connection conexion = ConexionBD.obtenerConexion()) {
            return buscarFuncionarioAsociado(conexion, id);
        } catch (SQLException e) {
            throw new IllegalStateException(MENSAJE_ERROR_BD, e);
        }
    }

    public Funcionario buscarFuncionarioAsociado(Connection conexion, String id) throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement(
                "SELECT id_funcionario, nombre, telefono FROM funcionario WHERE id_funcionario = ?")) {
            sentencia.setString(1, id);
            try (ResultSet resultado = sentencia.executeQuery()) {
                if (!resultado.next()) {
                    return null;
                }
                return new Funcionario(
                        resultado.getString("id_funcionario"),
                        resultado.getString("nombre"),
                        resultado.getString("telefono"));
            }
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
