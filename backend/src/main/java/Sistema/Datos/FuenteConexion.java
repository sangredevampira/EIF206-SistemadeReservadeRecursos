package Sistema.Datos;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * "De donde saca la Logica una conexion nueva".
 *
 * Que es: una interfaz funcional (un solo metodo), asi que se puede
 * escribir como referencia a metodo o como lambda:
 *   - en la aplicacion:  ConexionBD::obtenerConexion  (conexion real a MySQL)
 *   - en las pruebas:    () -> conexionFalsa          (sin MySQL)
 *
 * Por que existe: RecursoLogica abre la conexion de sus transacciones
 * (acuerdo con Kristel: la Logica abre, apaga el autocommit y termina con
 * commit o rollback). Si llamara directo a ConexionBD.obtenerConexion(),
 * que es estatico, sus pruebas de unidad necesitarian MySQL encendido.
 * Recibiendo una FuenteConexion en el constructor, la prueba le pasa una
 * conexion falsa y puede revisar que se llamo setAutoCommit(false),
 * commit, rollback y close, sin base de datos.
 *
 * Como funciona: quien recibe la conexion de abrir() es su dueno y la
 * cierra con try-with-resources, igual que con ConexionBD.
 */
@FunctionalInterface
public interface FuenteConexion {

    Connection abrir() throws SQLException;
}
