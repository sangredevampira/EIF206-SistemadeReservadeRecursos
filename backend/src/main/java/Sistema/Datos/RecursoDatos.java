package Sistema.Datos;

import Sistema.DTO.CategoriaDTO;
import Sistema.DTO.RecursoDTO;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Acceso a la tabla recurso con JDBC (paso 5a). Mismo patron que UsuarioDatos:
 *   - la version que RECIBE la conexion no la cierra y lanza SQLException:
 *     para las transacciones de RecursoLogica y las pruebas con rollback;
 *   - la que no la recibe abre y cierra la suya con ConexionBD y convierte
 *     la SQLException en el mensaje generico (el detalle queda en la causa).
 *
 * Las listas y busquedas devuelven SOLO recursos activos (acuerdo con
 * Kristel). existeId y categoriaTieneRecursos cuentan tambien los
 * inactivos, porque la PK y la FK RESTRICT los incluyen.
 *
 * El constructor no abre conexiones ni archivos: los fakes de las pruebas
 * crean RecursoLogica() sin necesitar MySQL.
 */
public class RecursoDatos {

    // recursos activos con su categoria (JOIN); los filtros se agregan despues
    private static final String SELECT_ACTIVOS =
            "SELECT r.id_recurso, r.descripcion, c.id_categoria, c.descripcion AS descripcion_categoria"
                    + " FROM recurso r JOIN categoria c ON c.id_categoria = r.id_categoria"
                    + " WHERE r.activo = 1";

    private static final String ORDEN = " ORDER BY r.id_recurso";

    // --- listar activos ---

    public List<RecursoDTO> listarActivos() {
        try (Connection conexion = ConexionBD.obtenerConexion()) {
            return listarActivos(conexion);
        } catch (SQLException e) {
            throw new IllegalStateException(UsuarioDatos.MENSAJE_ERROR_BD, e);
        }
    }

    public List<RecursoDTO> listarActivos(Connection conexion) throws SQLException {
        return filtrar(conexion, null, null);
    }

    // --- filtrar por categoria (null = todas) ---

    public List<RecursoDTO> filtrarPorCategoria(String idCategoria) {
        try (Connection conexion = ConexionBD.obtenerConexion()) {
            return filtrarPorCategoria(conexion, idCategoria);
        } catch (SQLException e) {
            throw new IllegalStateException(UsuarioDatos.MENSAJE_ERROR_BD, e);
        }
    }

    public List<RecursoDTO> filtrarPorCategoria(Connection conexion, String idCategoria) throws SQLException {
        return filtrar(conexion, idCategoria, null);
    }

    // --- filtrar por categoria y descripcion (null = sin ese filtro) ---
    // La descripcion se busca como "contiene". La collation no distingue
    // mayusculas, asi que no hace falta toLowerCase.

    public List<RecursoDTO> filtrar(String idCategoria, String descripcion) {
        try (Connection conexion = ConexionBD.obtenerConexion()) {
            return filtrar(conexion, idCategoria, descripcion);
        } catch (SQLException e) {
            throw new IllegalStateException(UsuarioDatos.MENSAJE_ERROR_BD, e);
        }
    }

    public List<RecursoDTO> filtrar(Connection conexion, String idCategoria, String descripcion)
            throws SQLException {
        // solo se agregan fragmentos fijos; los valores siempre van como "?"
        StringBuilder sql = new StringBuilder(SELECT_ACTIVOS);
        List<String> valores = new ArrayList<>();
        if (idCategoria != null) {
            sql.append(" AND r.id_categoria = ?");
            valores.add(idCategoria);
        }
        if (descripcion != null) {
            sql.append(" AND r.descripcion LIKE ? ESCAPE '!'");
            valores.add("%" + escaparLike(descripcion) + "%");
        }
        sql.append(ORDEN);

        try (PreparedStatement sentencia = conexion.prepareStatement(sql.toString())) {
            for (int i = 0; i < valores.size(); i++) {
                sentencia.setString(i + 1, valores.get(i));
            }
            try (ResultSet resultado = sentencia.executeQuery()) {
                List<RecursoDTO> recursos = new ArrayList<>();
                while (resultado.next()) {
                    recursos.add(leer(resultado));
                }
                return recursos;
            }
        }
    }

    // --- buscar por ID (un inactivo se trata como no encontrado) ---

    public RecursoDTO buscarActivoPorId(String id) {
        try (Connection conexion = ConexionBD.obtenerConexion()) {
            return buscarActivoPorId(conexion, id);
        } catch (SQLException e) {
            throw new IllegalStateException(UsuarioDatos.MENSAJE_ERROR_BD, e);
        }
    }

    public RecursoDTO buscarActivoPorId(Connection conexion, String id) throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement(SELECT_ACTIVOS + " AND r.id_recurso = ?")) {
            sentencia.setString(1, id);
            try (ResultSet resultado = sentencia.executeQuery()) {
                return resultado.next() ? leer(resultado) : null;
            }
        }
    }

    // --- existe el ID (incluye inactivos: la PK tambien los incluye) ---

    public boolean existeId(String id) {
        try (Connection conexion = ConexionBD.obtenerConexion()) {
            return existeId(conexion, id);
        } catch (SQLException e) {
            throw new IllegalStateException(UsuarioDatos.MENSAJE_ERROR_BD, e);
        }
    }

    public boolean existeId(Connection conexion, String id) throws SQLException {
        return existe(conexion, "SELECT EXISTS (SELECT 1 FROM recurso WHERE id_recurso = ?)", id);
    }

    // --- insertar (siempre activo) ---
    // Errores que interpreta RecursoLogica: 1062 (ID repetido) y 1452 (categoria inexistente)

    public void insertar(RecursoDTO recurso) {
        try (Connection conexion = ConexionBD.obtenerConexion()) {
            insertar(conexion, recurso);
        } catch (SQLException e) {
            throw new IllegalStateException(UsuarioDatos.MENSAJE_ERROR_BD, e);
        }
    }

    public void insertar(Connection conexion, RecursoDTO recurso) throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement(
                "INSERT INTO recurso (id_recurso, descripcion, id_categoria, activo) VALUES (?, ?, ?, 1)")) {
            sentencia.setString(1, recurso.getId());
            sentencia.setString(2, recurso.getDescripcion());
            sentencia.setString(3, recurso.getCategoria().getId());
            sentencia.executeUpdate();
        }
    }

    // --- actualizar descripcion y categoria (solo si esta activo) ---

    // true si cambio exactamente una fila; el ID nunca se modifica
    public boolean actualizar(RecursoDTO recurso) {
        try (Connection conexion = ConexionBD.obtenerConexion()) {
            return actualizar(conexion, recurso);
        } catch (SQLException e) {
            throw new IllegalStateException(UsuarioDatos.MENSAJE_ERROR_BD, e);
        }
    }

    public boolean actualizar(Connection conexion, RecursoDTO recurso) throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement(
                "UPDATE recurso SET descripcion = ?, id_categoria = ? WHERE id_recurso = ? AND activo = 1")) {
            sentencia.setString(1, recurso.getDescripcion());
            sentencia.setString(2, recurso.getCategoria().getId());
            sentencia.setString(3, recurso.getId());
            return sentencia.executeUpdate() == 1;
        }
    }

    // --- desactivar (baja logica) ---

    // true si estaba activo y quedo inactivo
    public boolean desactivar(String id) {
        try (Connection conexion = ConexionBD.obtenerConexion()) {
            return desactivar(conexion, id);
        } catch (SQLException e) {
            throw new IllegalStateException(UsuarioDatos.MENSAJE_ERROR_BD, e);
        }
    }

    public boolean desactivar(Connection conexion, String id) throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement(
                "UPDATE recurso SET activo = 0 WHERE id_recurso = ? AND activo = 1")) {
            sentencia.setString(1, id);
            return sentencia.executeUpdate() == 1;
        }
    }

    // --- la categoria tiene recursos (incluye inactivos, por la FK RESTRICT) ---

    public boolean categoriaTieneRecursos(String idCategoria) {
        try (Connection conexion = ConexionBD.obtenerConexion()) {
            return categoriaTieneRecursos(conexion, idCategoria);
        } catch (SQLException e) {
            throw new IllegalStateException(UsuarioDatos.MENSAJE_ERROR_BD, e);
        }
    }

    public boolean categoriaTieneRecursos(Connection conexion, String idCategoria) throws SQLException {
        return existe(conexion, "SELECT EXISTS (SELECT 1 FROM recurso WHERE id_categoria = ?)", idCategoria);
    }

    // --- apoyo ---

    private static boolean existe(Connection conexion, String sql, String valor) throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, valor);
            try (ResultSet resultado = sentencia.executeQuery()) {
                resultado.next();
                return resultado.getBoolean(1);
            }
        }
    }

    private static RecursoDTO leer(ResultSet resultado) throws SQLException {
        return new RecursoDTO(
                resultado.getString("id_recurso"),
                resultado.getString("descripcion"),
                new CategoriaDTO(resultado.getString("id_categoria"), resultado.getString("descripcion_categoria")));
    }

    // Con ESCAPE '!' los comodines del usuario se buscan como texto: "50%"
    // no encuentra "500". Primero se escapa el propio '!'. Se usa '!' y no
    // '\' porque el significado de '\' depende del sql_mode (NO_BACKSLASH_ESCAPES).
    static String escaparLike(String texto) {
        return texto.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
