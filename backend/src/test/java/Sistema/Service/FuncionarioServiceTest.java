package Sistema.Service;

import Sistema.DTO.Funcionario;
import Sistema.DTO.ResultadoOperacionDTO;
import Sistema.Logica.FuncionarioLogica;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas de FuncionarioService: solo comprueban que delega en la Logica
 * (la capa Service no valida nada). Las reglas se prueban en
 * FuncionarioLogicaTest y el SQL en FuncionarioDatosIT.
 *
 * Las 4 pruebas de eliminar que estaban @Disabled (dependian de
 * reservas.json) se rehicieron en FuncionarioLogicaTest, con dobles y sin MySQL.
 */
class FuncionarioServiceTest {

    /** Fake de FuncionarioLogica: devuelve lo configurado y guarda lo recibido. */
    static class FuncionarioLogicaFake extends FuncionarioLogica {
        ResultadoOperacionDTO resultado = ResultadoOperacionDTO.fallo("respuesta de la logica");
        List<Funcionario> lista = List.of(new Funcionario("44444", "Ana Perez", "8888-9999"));
        Object recibido;
        String nombreRecibido;

        FuncionarioLogicaFake() {
            super(null, null, null, null);   // no usa Datos: todos sus metodos estan sobrescritos
        }

        @Override
        public ResultadoOperacionDTO registrar(Funcionario funcionario) {
            recibido = funcionario;
            return resultado;
        }

        @Override
        public ResultadoOperacionDTO modificar(Funcionario funcionario) {
            recibido = funcionario;
            return resultado;
        }

        @Override
        public ResultadoOperacionDTO eliminar(String id) {
            recibido = id;
            return resultado;
        }

        @Override
        public List<Funcionario> buscar(String idParcial, String nombreParcial) {
            recibido = idParcial;
            nombreRecibido = nombreParcial;
            return lista;
        }

        @Override
        public List<Funcionario> listarTodos() {
            return lista;
        }
    }

    private final FuncionarioLogicaFake logica = new FuncionarioLogicaFake();
    private final FuncionarioService service = new FuncionarioService(logica);

    @Test
    void registrarDelegaYDevuelveElMismoResultado() {
        Funcionario funcionario = new Funcionario("66666", "Carla Ruiz", "8555-1111");

        assertSame(logica.resultado, service.registrar(funcionario));
        assertSame(funcionario, logica.recibido);
    }

    @Test
    void modificarDelegaYDevuelveElMismoResultado() {
        logica.resultado = ResultadoOperacionDTO.exito();
        Funcionario funcionario = new Funcionario("44444", "Ana Maria", "8000-0000");

        assertSame(logica.resultado, service.modificar(funcionario));
        assertSame(funcionario, logica.recibido);
    }

    @Test
    void eliminarDelegaYDevuelveElMismoResultado() {
        assertSame(logica.resultado, service.eliminar("44444"));
        assertEquals("44444", logica.recibido);
    }

    @Test
    void buscarDelegaLosDosFiltrosSinCambiarlos() {
        assertSame(logica.lista, service.buscar(" 44 ", "ana"));
        assertEquals(" 44 ", logica.recibido);
        assertEquals("ana", logica.nombreRecibido);
    }

    @Test
    void listarTodosDelega() {
        assertSame(logica.lista, service.listarTodos());
    }
}
