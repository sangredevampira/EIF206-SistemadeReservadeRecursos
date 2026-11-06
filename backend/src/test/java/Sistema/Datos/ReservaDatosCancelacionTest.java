package Sistema.Datos;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReservaDatosCancelacionTest {

    @Test
    void cancelacionCondicionaLaActualizacionAlEstadoActivo() throws Exception {
        JdbcFalso jdbc = new JdbcFalso(1);

        boolean cancelada = new ReservaDatos()
                .cancelarReservaActiva(jdbc.conexion(), "RES-000001");

        assertTrue(cancelada);
        assertEquals(
                "UPDATE reserva SET estado = 'CANCELADA'"
                        + " WHERE id_reserva = ? AND estado = 'ACTIVA'",
                jdbc.sql.get()
        );
        assertEquals("RES-000001", jdbc.idReserva.get());
    }

    @Test
    void ceroFilasAfectadasNoConfirmaLaCancelacion() throws Exception {
        JdbcFalso jdbc = new JdbcFalso(0);

        assertFalse(new ReservaDatos()
                .cancelarReservaActiva(jdbc.conexion(), "RES-000001"));
    }

    private static final class JdbcFalso {
        private final int filasAfectadas;
        private final AtomicReference<String> sql = new AtomicReference<>();
        private final AtomicReference<String> idReserva = new AtomicReference<>();

        private JdbcFalso(int filasAfectadas) {
            this.filasAfectadas = filasAfectadas;
        }

        private Connection conexion() {
            PreparedStatement sentencia = (PreparedStatement) Proxy.newProxyInstance(
                    PreparedStatement.class.getClassLoader(),
                    new Class<?>[]{PreparedStatement.class},
                    (proxy, metodo, argumentos) -> switch (metodo.getName()) {
                        case "setString" -> {
                            idReserva.set((String) argumentos[1]);
                            yield null;
                        }
                        case "executeUpdate" -> filasAfectadas;
                        case "close" -> null;
                        case "isClosed" -> false;
                        default -> valorPredeterminado(metodo.getReturnType());
                    }
            );

            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    (proxy, metodo, argumentos) -> {
                        if (metodo.getName().equals("prepareStatement")) {
                            sql.set((String) argumentos[0]);
                            return sentencia;
                        }
                        return valorPredeterminado(metodo.getReturnType());
                    }
            );
        }

        private static Object valorPredeterminado(Class<?> tipo) {
            if (!tipo.isPrimitive()) return null;
            if (tipo == boolean.class) return false;
            if (tipo == byte.class) return (byte) 0;
            if (tipo == short.class) return (short) 0;
            if (tipo == int.class) return 0;
            if (tipo == long.class) return 0L;
            if (tipo == float.class) return 0F;
            if (tipo == double.class) return 0D;
            if (tipo == char.class) return '\0';
            return null;
        }
    }
}
