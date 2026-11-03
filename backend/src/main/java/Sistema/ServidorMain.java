package Sistema;

import Sistema.Red.DespachadorPeticiones;
import Sistema.Red.GestorClientes;
import Sistema.Red.ManejadorAuth;
import Sistema.Red.ManejadorActividades;
import Sistema.Red.ManejadorCalendarizacion;
import Sistema.Red.ManejadorCategorias;
import Sistema.Red.ManejadorEstadisticas;
import Sistema.Red.ManejadorFuncionarios;
import Sistema.Red.ManejadorRecursos;
import Sistema.Red.ManejadorReservas;
import Sistema.Red.ServidorReservas;
import Sistema.Service.AuthService;
import Sistema.Service.CalendarizacionService;
import Sistema.Service.CategoriaService;
import Sistema.Service.EstadisticasActividadesService;
import Sistema.Service.EstadisticasRecursosService;
import Sistema.Service.FuncionarioService;
import Sistema.Service.ProgramacionActividadesService;
import Sistema.Service.RecursoService;
import Sistema.Service.ReservaService;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

// Arranca el servidor de reservas. Se ejecuta con el main de esta clase.
public class ServidorMain {

    private static final int PUERTO_POR_DEFECTO = 5050;
    private static final String PROPIEDAD_CONFIG = "servidor.config";   // la misma que usa ConexionBD

    public static void main(String[] args) throws IOException {
        int puerto = leerPuerto();

        DespachadorPeticiones despachador = new DespachadorPeticiones();
        GestorClientes gestor = new GestorClientes();
        ServidorReservas servidor = new ServidorReservas(despachador, gestor);

        // Manejadores: cada uno registra sus propias operaciones
        new ManejadorAuth(new AuthService()).registrarEn(despachador);
        new ManejadorFuncionarios(new FuncionarioService()).registrarEn(despachador);
        new ManejadorCategorias(new CategoriaService()).registrarEn(despachador);
        new ManejadorRecursos(new RecursoService()).registrarEn(despachador);
        new ManejadorReservas(new ReservaService(), gestor).registrarEn(despachador);
        new ManejadorEstadisticas(
                new EstadisticasRecursosService(),
                new EstadisticasActividadesService(),
                new ReservaService()
        ).registrarEn(despachador);
        new ManejadorCalendarizacion(new CalendarizacionService()).registrarEn(despachador);
        new ManejadorActividades(new ProgramacionActividadesService()).registrarEn(despachador);

        servidor.iniciar(puerto);
        System.out.println("Servidor escuchando en el puerto " + servidor.getPuerto());
    }

    private static int leerPuerto() {
        // Si se indicó un archivo y no existe, se avisa (no se busca en otro lado)
        String indicada = System.getProperty(PROPIEDAD_CONFIG);
        if (indicada != null && !indicada.isBlank() && !Files.isRegularFile(Path.of(indicada))) {
            throw new IllegalStateException("No existe el archivo indicado en " + PROPIEDAD_CONFIG + ".");
        }

        Properties propiedades = new Properties();
        for (Path ruta : rutasPosibles()) {
            if (Files.isRegularFile(ruta)) {
                try (Reader lector = Files.newBufferedReader(ruta, StandardCharsets.UTF_8)) {
                    propiedades.load(lector);
                } catch (IOException e) {
                    throw new IllegalStateException("No se pudo leer " + ruta, e);
                }
                break;
            }
        }

        String valor = propiedades.getProperty("puerto");
        if (valor == null || valor.isBlank()) {
            return PUERTO_POR_DEFECTO;
        }
        try {
            int puerto = Integer.parseInt(valor.trim());
            if (puerto < 0 || puerto > 65535) {
                throw new IllegalStateException("El puerto debe estar entre 0 y 65535.");
            }
            return puerto;
        } catch (NumberFormatException e) {
            throw new IllegalStateException("El puerto del archivo de configuración no es un número: " + valor);
        }
    }

    // Si se indicó servidor.config se usa SOLO esa ruta; si no, ./, ./backend/ y ../backend/
    private static List<Path> rutasPosibles() {
        String indicada = System.getProperty(PROPIEDAD_CONFIG);
        if (indicada != null && !indicada.isBlank()) {
            return List.of(Path.of(indicada));
        }
        return List.of(Path.of("servidor.properties"),
                Path.of("backend", "servidor.properties"),
                Path.of("..", "backend", "servidor.properties"));
    }
}