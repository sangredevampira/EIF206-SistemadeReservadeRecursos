package Sistema.Service;

import Sistema.DTO.Funcionario;
import Sistema.DTO.ResultadoOperacionDTO;
import Sistema.Logica.FuncionarioLogica;

import java.util.List;

// Capa Service: solo enviar y recibir solicitud desde y hacia el
// controlador, en comunicacion con la capa inferior (Logica). Sin
// validaciones propias, igual que AuthService y CategoriaService.

public class FuncionarioService {

    private final FuncionarioLogica funcionarioLogica;

    public FuncionarioService() {
        this(new FuncionarioLogica());
    }

    // para pruebas: FuncionarioServiceTest pasa una Logica falsa
    public FuncionarioService(FuncionarioLogica funcionarioLogica) {
        this.funcionarioLogica = funcionarioLogica;
    }

    public ResultadoOperacionDTO registrar(Funcionario funcionario) {
        return funcionarioLogica.registrar(funcionario);
    }

    public ResultadoOperacionDTO modificar(Funcionario funcionario) {
        return funcionarioLogica.modificar(funcionario);
    }

    public ResultadoOperacionDTO eliminar(String id) {
        return funcionarioLogica.eliminar(id);
    }

    public List<Funcionario> buscar(String idParcial, String nombreParcial) {
        return funcionarioLogica.buscar(idParcial, nombreParcial);
    }

    public List<Funcionario> listarTodos() {
        return funcionarioLogica.listarTodos();
    }
}
