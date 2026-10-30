package Sistema.Service;

import Sistema.DTO.Funcionario;
import Sistema.Logica.FuncionarioLogica;

import java.util.List;

// Capa Service: solo enviar y recibir solicitud desde y hacia el
// controlador, en comunicacion con la capa inferior (Logica). Sin
// validaciones propias, igual que AuthService.

public class FuncionarioService {

    private final FuncionarioLogica funcionarioLogica = new FuncionarioLogica();

    public FuncionarioLogica.ResultadoOperacion registrar(Funcionario funcionario) {
        return funcionarioLogica.registrar(funcionario);
    }

    public FuncionarioLogica.ResultadoOperacion modificar(Funcionario funcionario) {
        return funcionarioLogica.modificar(funcionario);
    }

    public FuncionarioLogica.ResultadoOperacion eliminar(String id) {
        return funcionarioLogica.eliminar(id);
    }

    public List<Funcionario> buscar(String idParcial, String nombreParcial) {
        return funcionarioLogica.buscar(idParcial, nombreParcial);
    }

    public List<Funcionario> listarTodos() {
        return funcionarioLogica.listarTodos();
    }
}
