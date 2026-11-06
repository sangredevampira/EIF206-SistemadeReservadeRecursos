package Sistema.Logica;

import Sistema.DTO.ReservaDTO;
import Sistema.DTO.EstadoReserva;
import Sistema.Datos.ConexionBD;
import Sistema.Datos.ReservaDatos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Prueba de integracion de registrarReserva con MySQL REAL (base reservas_test): ReservaLogica abre sus
 * propias conexiones, asi que los datos de apoyo se CONFIRMAN (commit) y se borran al terminar cada prueba.
 * Solo se tocan filas con los IDs "it-conc-..."; antes de empezar se comprueba que la base sea reservas_test.
 * Se salta igual que ReservaDatosIT si no existe servidor-test.properties.
 */
public class ReservaLogicaIT {

    private static final String ID_FUNCIONARIO = "it-conc-func";
    private static final String ID_CATEGORIA = "CAT-ITCONC";
    private static final String ID_RECURSO = "it-conc-rec";
    private static final int HILOS = 5;
    private static final String PROPIEDAD_CONFIG = "servidor.config";   // la que ponen Surefire y Failsafe

    private boolean datosCreados = false;

    @BeforeEach
    void prepararDatos() throws SQLException {
        String ruta = System.getProperty(PROPIEDAD_CONFIG);
        assumeTrue(ruta != null && Files.isRegularFile(Path.of(ruta)),
                "Se salta: no existe backend/servidor-test.properties (copie servidor-test.properties.example)"
                        + " o no se corre con mvn verify.");

        try (Connection conexion = ConexionBD.obtenerConexion()) {
            // antes de tocar datos: nunca trabajar sobre reservas_db
            assertEquals("reservas_test", baseActual(conexion),
                    "La prueba no corre sobre reservas_test; no se toca ningun dato.");
            borrarDatosDePrueba(conexion);   // por si una corrida anterior se interrumpio
            ejecutar(conexion, "INSERT INTO usuario (id_usuario, clave_hash, rol) VALUES (?, ?, ?)",
                    ID_FUNCIONARIO, "no-es-un-hash", "FUNCIONARIO");
            ejecutar(conexion, "INSERT INTO funcionario (id_funcionario, nombre, telefono) VALUES (?, ?, ?)",
                    ID_FUNCIONARIO, "Prueba ReservaLogicaIT", "0000-0000");
            ejecutar(conexion, "INSERT INTO categoria (id_categoria, descripcion) VALUES (?, ?)",
                    ID_CATEGORIA, "Categoria ReservaLogicaIT");
            ejecutar(conexion, "INSERT INTO recurso (id_recurso, descripcion, id_categoria) VALUES (?, ?, ?)",
                    ID_RECURSO, "Recurso ReservaLogicaIT", ID_CATEGORIA);   // autocommit: queda confirmado
            datosCreados = true;
        }
    }

    @AfterEach
    void borrarDatos() throws SQLException {
        if (!datosCreados) {
            return;
        }
        try (Connection conexion = ConexionBD.obtenerConexion()) {
            borrarDatosDePrueba(conexion);
        }
    }

    // --- pruebas ---

    @Test
    void conVariosHilosPidiendoElMismoRecursoALaMismaHoraSoloUnoLoConsigue() throws Exception {
        LocalDate fecha = LocalDate.now().plusDays(1);
        ExecutorService hilos = Executors.newFixedThreadPool(HILOS);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<Object>> resultados = new ArrayList<>();

        for (int i = 0; i < HILOS; i++) {
            Callable<Object> tarea = () -> {
                ReservaLogica logica = new ReservaLogica();   // una instancia por hilo: el candado es estatico
                salida.await();                               // todos arrancan a la vez
                try {
                    return logica.registrarReserva(ID_FUNCIONARIO, "Hilo", "Reunion", fecha,
                            LocalTime.of(8, 0), LocalTime.of(10, 0), List.of(ID_CATEGORIA));
                } catch (IllegalArgumentException e) {
                    return e;
                }
            };
            resultados.add(hilos.submit(tarea));
        }
        salida.countDown();

        int exitos = 0;
        int rechazos = 0;
        for (Future<Object> resultado : resultados) {
            Object valor = resultado.get(30, TimeUnit.SECONDS);
            if (valor instanceof ReservaDTO) {
                exitos++;
            } else {
                rechazos++;
                assertEquals("No hay recursos disponibles en las categorías: Categoria ReservaLogicaIT",
                        ((IllegalArgumentException) valor).getMessage());
            }
        }
        hilos.shutdown();

        assertEquals(1, exitos, "Solo un hilo debia conseguir el recurso.");
        assertEquals(HILOS - 1, rechazos);
        assertEquals(1, contarReservasDelFuncionario(ID_FUNCIONARIO));
    }

    @Test
    void registrarReservaGuardaLaReservaConSuRecursoYDevuelveLaReservaCreada() {
        ReservaLogica logica = new ReservaLogica();

        ReservaDTO creada = logica.registrarReserva(ID_FUNCIONARIO, "Ignorado", "Reunion",
                LocalDate.now().plusDays(1), LocalTime.of(8, 0), LocalTime.of(10, 0), List.of(ID_CATEGORIA));

        assertTrue(creada.getIdReserva().startsWith("RES-"));
        assertEquals(ID_RECURSO, creada.getRecursosReservados().get(0).getId());

        ReservaDTO guardada = new ReservaDatos().buscarReservaID(creada.getIdReserva());
        assertNotNull(guardada);
        assertEquals("Prueba ReservaLogicaIT", guardada.getNombreFuncionario());   // sale de la tabla funcionario
        assertEquals(1, guardada.getRecursosReservados().size());
    }

    @Test
    void siElFuncionarioNoExisteHayMensajeClaroYNoQuedaNada() throws Exception {
        ReservaLogica logica = new ReservaLogica();

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                logica.registrarReserva("it-conc-nadie", "Nadie", "Reunion",
                        LocalDate.now().plusDays(1), LocalTime.of(8, 0), LocalTime.of(10, 0), List.of(ID_CATEGORIA)));

        assertEquals("El funcionario de la reserva no existe o está dado de baja.", error.getMessage());
        assertEquals(0, contarReservasDelFuncionario("it-conc-nadie"));
        // el rollback no dejo el recurso ocupado: otra reserva valida si se puede hacer
        assertNotNull(logica.registrarReserva(ID_FUNCIONARIO, "Ana", "Reunion",
                LocalDate.now().plusDays(1), LocalTime.of(8, 0), LocalTime.of(10, 0), List.of(ID_CATEGORIA)));
    }

    @Test
    void siElFuncionarioFueDadoDeBajaNoPuedeRegistrarReservas() throws Exception {
        try (Connection conexion = ConexionBD.obtenerConexion()) {   // autocommit: la baja queda confirmada
            ejecutar(conexion, "UPDATE funcionario SET activo = 0 WHERE id_funcionario = ?", ID_FUNCIONARIO);
        }
        ReservaLogica logica = new ReservaLogica();

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                logica.registrarReserva(ID_FUNCIONARIO, "Ana", "Reunion",
                        LocalDate.now().plusDays(1), LocalTime.of(8, 0), LocalTime.of(10, 0), List.of(ID_CATEGORIA)));

        assertEquals("El funcionario de la reserva no existe o está dado de baja.", error.getMessage());
        assertEquals(0, contarReservasDelFuncionario(ID_FUNCIONARIO));
    }

    @Test
    void dosCancelacionesConcurrentesSoloConfirmanUnaTransicion() throws Exception {
        ReservaDTO creada = new ReservaLogica().registrarReserva(
                ID_FUNCIONARIO,
                "Ignorado",
                "Cancelación concurrente",
                LocalDate.now().plusDays(1),
                LocalTime.of(8, 0),
                LocalTime.of(10, 0),
                List.of(ID_CATEGORIA)
        );
        ReservaDatos datos = new DatosConLecturasSincronizadas();
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<Object>> resultados = new ArrayList<>();

        try {
            for (int i = 0; i < 2; i++) {
                resultados.add(hilos.submit(() -> {
                    salida.await();
                    try {
                        return new ReservaLogica(datos, null, null)
                                .cancelarReserva(creada.getIdReserva());
                    } catch (IllegalArgumentException e) {
                        return e;
                    }
                }));
            }
            salida.countDown();

            int exitos = 0;
            int rechazos = 0;
            for (Future<Object> resultado : resultados) {
                Object valor = resultado.get(30, TimeUnit.SECONDS);
                if (Boolean.TRUE.equals(valor)) {
                    exitos++;
                } else {
                    rechazos++;
                    assertEquals(
                            "Esta reserva ya se encuentra cancelada",
                            ((IllegalArgumentException) valor).getMessage()
                    );
                }
            }

            assertEquals(1, exitos, "Solo una solicitud debe cambiar ACTIVA a CANCELADA.");
            assertEquals(1, rechazos);
        } finally {
            hilos.shutdownNow();
        }

        ReservaDTO guardada = new ReservaDatos().buscarReservaID(creada.getIdReserva());
        assertNotNull(guardada);
        assertEquals(EstadoReserva.CANCELADA, guardada.getEstado());
        assertEquals(1, guardada.getRecursosReservados().size());
        assertEquals(ID_RECURSO, guardada.getRecursosReservados().get(0).getId());
        assertEquals(1, contarRelacionesDeReserva(creada.getIdReserva()));
    }

    // --- apoyo ---

    private static String baseActual(Connection conexion) throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement("SELECT DATABASE()");
             ResultSet resultado = sentencia.executeQuery()) {
            resultado.next();
            return resultado.getString(1);
        }
    }

    // solo las filas de esta prueba, en orden de las FK (reserva_recurso se borra en cascada con la reserva)
    private static void borrarDatosDePrueba(Connection conexion) throws SQLException {
        ejecutar(conexion, "DELETE FROM reserva WHERE id_funcionario IN (?, ?)", ID_FUNCIONARIO, "it-conc-nadie");
        ejecutar(conexion, "DELETE FROM recurso WHERE id_recurso = ?", ID_RECURSO);
        ejecutar(conexion, "DELETE FROM categoria WHERE id_categoria = ?", ID_CATEGORIA);
        ejecutar(conexion, "DELETE FROM funcionario WHERE id_funcionario = ?", ID_FUNCIONARIO);
        ejecutar(conexion, "DELETE FROM usuario WHERE id_usuario = ?", ID_FUNCIONARIO);
    }

    @SuppressWarnings({"SqlResolve", "SqlNoDataSourceInspection"})   // el IDE no conoce las tablas (no hay fuente de datos)
    private static int contarReservasDelFuncionario(String idFuncionario) throws SQLException {
        try (Connection conexion = ConexionBD.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(
                     "SELECT COUNT(*) FROM reserva WHERE id_funcionario = ?")) {
            sentencia.setString(1, idFuncionario);
            try (ResultSet resultado = sentencia.executeQuery()) {
                resultado.next();
                return resultado.getInt(1);
            }
        }
    }

    private static int contarRelacionesDeReserva(String idReserva) throws SQLException {
        try (Connection conexion = ConexionBD.obtenerConexion();
             PreparedStatement sentencia = conexion.prepareStatement(
                     "SELECT COUNT(*) FROM reserva_recurso WHERE id_reserva = ?")) {
            sentencia.setString(1, idReserva);
            try (ResultSet resultado = sentencia.executeQuery()) {
                resultado.next();
                return resultado.getInt(1);
            }
        }
    }

    private static final class DatosConLecturasSincronizadas extends ReservaDatos {
        private final CountDownLatch lecturas = new CountDownLatch(2);

        @Override
        public ReservaDTO buscarReservaID(String id) {
            ReservaDTO reserva = super.buscarReservaID(id);
            lecturas.countDown();
            try {
                if (!lecturas.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Las dos cancelaciones no alcanzaron la lectura inicial.");
                }
                return reserva;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("La prueba de cancelación fue interrumpida.", e);
            }
        }
    }

    private static void ejecutar(Connection conexion, String sql, Object... parametros) throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            for (int i = 0; i < parametros.length; i++) {
                sentencia.setObject(i + 1, parametros[i]);
            }
            sentencia.executeUpdate();
        }
    }
}
