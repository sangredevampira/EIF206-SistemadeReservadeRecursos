package Sistema.Datos;

import Sistema.DTO.Funcionario;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Acceso a la tabla funcionario con JDBC (paso 5c). Mismo patron que
 * RecursoDatos y CategoriaDatos:
 *   - la version que RECIBE la conexion no la cierra y lanza SQLException:
 *     para las transacciones de FuncionarioLogica y las pruebas con rollback;
 *   - la que no la recibe abre y cierra la suya con ConexionBD y convierte
 *     la SQLException en el mensaje generico (el detalle queda en la causa).
 *
 * Las listas y busquedas devuelven SOLO funcionarios activos (baja logica).
 * buscarFuncionarioAsociado no filtra activo: el login ya rechaza al usuario
 * inactivo, y la baja desactiva usuario y funcionario en la misma transaccion.
 *
 * insertar y desactivar existen solo con conexion: un funcionario nunca se
 * guarda ni se da de baja sin su usuario (van juntos en una transaccion de
 * FuncionarioLogica).
 *
 * El constructor no abre conexiones ni archivos: los fakes de las pruebas
 * heredan de esta clase sin necesitar MySQL.
 */
public class FuncionarioDatos {

    private static final String SELECT_ACTIVOS =
            "SELECT id_funcionario, nombre, telefono FROM funcionario WHERE activo = 1";

    private static final String ORDEN = " ORDER BY id_funcionario";

    // --- listar activos ---

    public List<Funcionario> listarActivos() {
        try (Connection conexion = ConexionBD.obtenerConexion()) {
            return listarActivos(conexion);
        } catch (SQLException e) {
            throw new IllegalStateException(UsuarioDatos.MENSAJE_ERROR_BD, e);
        }
    }

    public List<Funcionario> listarActivos(Connection conexion) throws SQLException {
        return buscar(conexion, null, null);
    }

    // --- buscar por ID y nombre: "contiene" (null = sin ese filtro) ---
    // Como en el Proyecto 1, los dos filtros se combinan con AND. La collation
    // no distingue mayusculas, asi que no hace falta toLowerCase.

    public List<Funcionario> buscar(String idParcial, String nombreParcial) {
        try (Connection conexion = ConexionBD.obtenerConexion()) {
            return buscar(conexion, idParcial, nombreParcial);
        } catch (SQLException e) {
            throw new IllegalStateException(UsuarioDatos.MENSAJE_ERROR_BD, e);
        }
    }

    public List<Funcionario> buscar(Connection conexion, String idParcial, String nombreParcial)
            throws SQLException {
        // solo se agregan fragmentos fijos; los valores siempre van como "?"
        StringBuilder sql = new StringBuilder(SELECT_ACTIVOS);
        List<String> valores = new ArrayList<>();
        if (idParcial != null) {
            sql.append(" AND id_funcionario LIKE ? ESCAPE '!'");
            valores.add("%" + TextoSql.escaparLike(idParcial) + "%");
        }
        if (nombreParcial != null) {
            sql.append(" AND nombre LIKE ? ESCAPE '!'");
            valores.add("%" + TextoSql.escaparLike(nombreParcial) + "%");
        }
        sql.append(ORDEN);

        try (PreparedStatement sentencia = conexion.prepareStatement(sql.toString())) {
            for (int i = 0; i < valores.size(); i++) {
                sentencia.setString(i + 1, valores.get(i));
            }
            try (ResultSet resultado = sentencia.executeQuery()) {
                List<Funcionario> funcionarios = new ArrayList<>();
                while (resultado.next()) {
                    funcionarios.add(leer(resultado));
                }
                return funcionarios;
            }
        }
    }

    // --- buscar por ID (un inactivo se trata como no encontrado) ---

    public Funcionario buscarActivoPorId(String id) {
        try (Connection conexion = ConexionBD.obtenerConexion()) {
            return buscarActivoPorId(conexion, id);
        } catch (SQLException e) {
            throw new IllegalStateException(UsuarioDatos.MENSAJE_ERROR_BD, e);
        }
    }

    public Funcionario buscarActivoPorId(Connection conexion, String id) throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement(SELECT_ACTIVOS + " AND id_funcionario = ?")) {
            sentencia.setString(1, id);
            try (ResultSet resultado = sentencia.executeQuery()) {
                return resultado.next() ? leer(resultado) : null;
            }
        }
    }

    // --- funcionario asociado a un usuario (para el login; no filtra activo) ---
    // Movido desde UsuarioDatos en el paso 5c, con el mismo SQL.

    public Funcionario buscarFuncionarioAsociado(String id) {
        try (Connection conexion = ConexionBD.obtenerConexion()) {
            return buscarFuncionarioAsociado(conexion, id);
        } catch (SQLException e) {
            throw new IllegalStateException(UsuarioDatos.MENSAJE_ERROR_BD, e);
        }
    }

    public Funcionario buscarFuncionarioAsociado(Connection conexion, String id) throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement(
                "SELECT id_funcionario, nombre, telefono FROM funcionario WHERE id_funcionario = ?")) {
            sentencia.setString(1, id);
            try (ResultSet resultado = sentencia.executeQuery()) {
                return resultado.next() ? leer(resultado) : null;
            }
        }
    }

    // --- insertar (siempre activo; solo dentro de la transaccion del registro) ---
    // Errores que interpreta FuncionarioLogica: 1062 (ID repetido). Un 1452 (sin
    // usuario) no deberia ocurrir: el usuario se inserta antes, en la misma transaccion.

    public void insertar(Connection conexion, Funcionario funcionario) throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement(
                "INSERT INTO funcionario (id_funcionario, nombre, telefono, activo) VALUES (?, ?, ?, 1)")) {
            sentencia.setString(1, funcionario.getId());
            sentencia.setString(2, funcionario.getNombre());
            sentencia.setString(3, funcionario.getTelefono());
            sentencia.executeUpdate();
        }
    }

    // --- actualizar nombre y telefono (solo si esta activo) ---

    // true si cambio exactamente una fila; el ID nunca se modifica
    public boolean actualizar(Funcionario funcionario) {
        try (Connection conexion = ConexionBD.obtenerConexion()) {
            return actualizar(conexion, funcionario);
        } catch (SQLException e) {
            throw new IllegalStateException(UsuarioDatos.MENSAJE_ERROR_BD, e);
        }
    }

    public boolean actualizar(Connection conexion, Funcionario funcionario) throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement(
                "UPDATE funcionario SET nombre = ?, telefono = ? WHERE id_funcionario = ? AND activo = 1")) {
            sentencia.setString(1, funcionario.getNombre());
            sentencia.setString(2, funcionario.getTelefono());
            sentencia.setString(3, funcionario.getId());
            return sentencia.executeUpdate() == 1;
        }
    }

    // --- desactivar (baja logica; solo dentro de la transaccion de la baja) ---

    // true si estaba activo y quedo inactivo
    public boolean desactivar(Connection conexion, String id) throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement(
                "UPDATE funcionario SET activo = 0 WHERE id_funcionario = ? AND activo = 1")) {
            sentencia.setString(1, id);
            return sentencia.executeUpdate() == 1;
        }
    }

    // --- apoyo ---

    private static Funcionario leer(ResultSet resultado) throws SQLException {
        return new Funcionario(
                resultado.getString("id_funcionario"),
                resultado.getString("nombre"),
                resultado.getString("telefono"));
    }
}
