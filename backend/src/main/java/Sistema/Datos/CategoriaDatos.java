package Sistema.Datos;

import Sistema.DTO.CategoriaDTO;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Acceso a la tabla categoria con JDBC (paso 5b). Mismo patron que
 * RecursoDatos y UsuarioDatos:
 *   - la version que RECIBE la conexion no la cierra y lanza SQLException:
 *     para las transacciones de CategoriaLogica y las pruebas con rollback;
 *   - la que no la recibe abre y cierra la suya con ConexionBD y convierte
 *     la SQLException en el mensaje generico (el detalle queda en la causa).
 *
 * La tabla no tiene columna activo: eliminar es un borrado fisico. La
 * collation utf8mb4_0900_as_ci hace que "=" y LIKE no distingan mayusculas
 * pero si tildes, igual que el UNIQUE de descripcion.
 *
 * El constructor no abre conexiones ni archivos: los fakes de las pruebas
 * heredan de esta clase sin necesitar MySQL.
 */
public class CategoriaDatos {

    private static final String SELECT = "SELECT id_categoria, descripcion FROM categoria";
    private static final String ORDEN = " ORDER BY id_categoria";

    // --- listar todas, ordenadas por ID ---

    public List<CategoriaDTO> listarTodos() {
        try (Connection conexion = ConexionBD.obtenerConexion()) {
            return listarTodos(conexion);
        } catch (SQLException e) {
            throw new IllegalStateException(UsuarioDatos.MENSAJE_ERROR_BD, e);
        }
    }

    public List<CategoriaDTO> listarTodos(Connection conexion) throws SQLException {
        return buscar(conexion, null);
    }

    // --- buscar por descripcion: "contiene" (null = sin filtro) ---

    public List<CategoriaDTO> buscar(String descripcion) {
        try (Connection conexion = ConexionBD.obtenerConexion()) {
            return buscar(conexion, descripcion);
        } catch (SQLException e) {
            throw new IllegalStateException(UsuarioDatos.MENSAJE_ERROR_BD, e);
        }
    }

    public List<CategoriaDTO> buscar(Connection conexion, String descripcion) throws SQLException {
        // solo se agregan fragmentos fijos; el valor siempre va como "?"
        String sql = (descripcion == null)
                ? SELECT + ORDEN
                : SELECT + " WHERE descripcion LIKE ? ESCAPE '!'" + ORDEN;

        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            if (descripcion != null) {
                sentencia.setString(1, "%" + TextoSql.escaparLike(descripcion) + "%");
            }
            try (ResultSet resultado = sentencia.executeQuery()) {
                List<CategoriaDTO> categorias = new ArrayList<>();
                while (resultado.next()) {
                    categorias.add(leer(resultado));
                }
                return categorias;
            }
        }
    }

    // --- buscar por ID (null si no existe) ---

    public CategoriaDTO buscarPorId(String id) {
        try (Connection conexion = ConexionBD.obtenerConexion()) {
            return buscarPorId(conexion, id);
        } catch (SQLException e) {
            throw new IllegalStateException(UsuarioDatos.MENSAJE_ERROR_BD, e);
        }
    }

    public CategoriaDTO buscarPorId(Connection conexion, String id) throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement(SELECT + " WHERE id_categoria = ?")) {
            sentencia.setString(1, id);
            try (ResultSet resultado = sentencia.executeQuery()) {
                return resultado.next() ? leer(resultado) : null;
            }
        }
    }

    // --- existe la descripcion (misma comparacion que el UNIQUE) ---
    // idAExcluir: al modificar, la propia categoria no cuenta (null = ninguna)

    public boolean existeDescripcion(String descripcion, String idAExcluir) {
        try (Connection conexion = ConexionBD.obtenerConexion()) {
            return existeDescripcion(conexion, descripcion, idAExcluir);
        } catch (SQLException e) {
            throw new IllegalStateException(UsuarioDatos.MENSAJE_ERROR_BD, e);
        }
    }

    public boolean existeDescripcion(Connection conexion, String descripcion, String idAExcluir)
            throws SQLException {
        String sql = (idAExcluir == null)
                ? "SELECT EXISTS (SELECT 1 FROM categoria WHERE descripcion = ?)"
                : "SELECT EXISTS (SELECT 1 FROM categoria WHERE descripcion = ? AND id_categoria <> ?)";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, descripcion);
            if (idAExcluir != null) {
                sentencia.setString(2, idAExcluir);
            }
            try (ResultSet resultado = sentencia.executeQuery()) {
                resultado.next();
                return resultado.getBoolean(1);
            }
        }
    }

    // --- siguiente ID CAT-000001 (solo dentro de una transaccion) ---
    // Las constantes de tabla, columna y prefijo quedan aqui, en Datos. No hay
    // version sin conexion: GeneradorIdsBD exige la conexion del INSERT con
    // el autocommit apagado.

    public String siguienteId(Connection conexion) throws SQLException {
        return GeneradorIdsBD.siguienteId(conexion, "categoria", "id_categoria", "CAT");
    }

    // --- insertar ---
    // Errores que interpreta CategoriaLogica: 1062 (descripcion o ID repetido)

    public void insertar(CategoriaDTO categoria) {
        try (Connection conexion = ConexionBD.obtenerConexion()) {
            insertar(conexion, categoria);
        } catch (SQLException e) {
            throw new IllegalStateException(UsuarioDatos.MENSAJE_ERROR_BD, e);
        }
    }

    public void insertar(Connection conexion, CategoriaDTO categoria) throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement(
                "INSERT INTO categoria (id_categoria, descripcion) VALUES (?, ?)")) {
            sentencia.setString(1, categoria.getId());
            sentencia.setString(2, categoria.getDescripcion());
            sentencia.executeUpdate();
        }
    }

    // --- actualizar la descripcion (el ID nunca cambia) ---

    // true si encontro exactamente una fila
    public boolean actualizar(CategoriaDTO categoria) {
        try (Connection conexion = ConexionBD.obtenerConexion()) {
            return actualizar(conexion, categoria);
        } catch (SQLException e) {
            throw new IllegalStateException(UsuarioDatos.MENSAJE_ERROR_BD, e);
        }
    }

    public boolean actualizar(Connection conexion, CategoriaDTO categoria) throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement(
                "UPDATE categoria SET descripcion = ? WHERE id_categoria = ?")) {
            sentencia.setString(1, categoria.getDescripcion());
            sentencia.setString(2, categoria.getId());
            return sentencia.executeUpdate() == 1;
        }
    }

    // --- eliminar (borrado fisico) ---
    // Error que interpreta CategoriaLogica: 1451 (la FK RESTRICT de recurso)

    // true si borro exactamente una fila
    public boolean eliminar(String id) {
        try (Connection conexion = ConexionBD.obtenerConexion()) {
            return eliminar(conexion, id);
        } catch (SQLException e) {
            throw new IllegalStateException(UsuarioDatos.MENSAJE_ERROR_BD, e);
        }
    }

    public boolean eliminar(Connection conexion, String id) throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement(
                "DELETE FROM categoria WHERE id_categoria = ?")) {
            sentencia.setString(1, id);
            return sentencia.executeUpdate() == 1;
        }
    }

    // --- apoyo ---

    private static CategoriaDTO leer(ResultSet resultado) throws SQLException {
        return new CategoriaDTO(resultado.getString("id_categoria"), resultado.getString("descripcion"));
    }
}
