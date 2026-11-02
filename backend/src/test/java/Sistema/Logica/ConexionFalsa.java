package Sistema.Logica;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

/**
 * Connection falsa hecha con java.lang.reflect.Proxy, para las pruebas de
 * unidad de la Logica sin MySQL. La usan RecursoLogicaTest,
 * CategoriaLogicaTest y FuncionarioLogicaTest. Es de
 * paquete: solo existe para las pruebas de Sistema.Logica.
 *
 * Que es: java.sql.Connection es una interfaz con mas de 50 metodos.
 * Escribir una clase que los implemente todos seria enorme, y Mockito no
 * esta en el proyecto. Proxy.newProxyInstance crea, mientras corre el
 * programa, un objeto que SI implementa Connection, y manda CADA llamada
 * a un unico metodo: invoke(...) de esta clase (el InvocationHandler).
 *
 * Como funciona: si la Logica llama conexion.commit(), Java ejecuta
 * invoke(proxy, <el Method "commit">, null). Aqui se anota el nombre en
 * la lista "llamadas" y se responde lo que haria una conexion real:
 * nada para commit/rollback/close, el valor guardado para
 * getAutoCommit, etc. Un metodo no previsto (por ejemplo prepareStatement)
 * lanza UnsupportedOperationException: asi una prueba falla si la Logica
 * intentara usar SQL directo en vez de pasar por su clase de Datos.
 *
 * Por que existe: la Logica recibe una FuenteConexion por constructor; la
 * prueba le pasa () -> conexionFalsa.conexion y despues revisa en
 * "llamadas" que se apago el autocommit, que hubo commit si todo salio
 * bien o rollback si algo fallo, y que siempre se cerro la conexion.
 */
class ConexionFalsa implements InvocationHandler {

    // en orden: "setAutoCommit(false)", "commit", "rollback", "close"
    final List<String> llamadas = new ArrayList<>();
    private boolean autoCommit = true;
    private boolean cerrada = false;

    // el objeto que se le entrega a la Logica a traves de FuenteConexion
    final Connection conexion = (Connection) Proxy.newProxyInstance(
            ConexionFalsa.class.getClassLoader(),
            new Class<?>[] { Connection.class },
            this);

    @Override
    public Object invoke(Object proxy, Method metodo, Object[] argumentos) {
        String nombre = metodo.getName();
        switch (nombre) {
            case "setAutoCommit" -> {
                autoCommit = (Boolean) argumentos[0];
                llamadas.add("setAutoCommit(" + autoCommit + ")");
                return null;
            }
            case "getAutoCommit" -> { return autoCommit; }
            case "commit", "rollback" -> {
                llamadas.add(nombre);
                return null;
            }
            case "close" -> {
                cerrada = true;
                llamadas.add(nombre);
                return null;
            }
            case "isClosed" -> { return cerrada; }
            // metodos de Object, por si una asercion imprime o compara la conexion
            case "toString" -> { return "ConexionFalsa" + llamadas; }
            case "hashCode" -> { return System.identityHashCode(proxy); }
            case "equals" -> { return proxy == argumentos[0]; }
            default -> throw new UnsupportedOperationException("La conexion falsa no implementa " + nombre);
        }
    }
}
