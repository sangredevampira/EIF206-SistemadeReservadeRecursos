package Sistema.Service;

import Sistema.DTO.Funcionario;
import Sistema.DTO.ResultadoOperacionDTO;

import java.util.List;

// contrato de funcionarios (solo administrador). Lo implementan el
// FuncionarioService del Backend y, en el Frontend, el proxy que envia
// FUNC_CREAR, FUNC_EDITAR, FUNC_BAJA, FUNC_BUSCAR y FUNC_LISTAR.
public interface IFuncionarioService {

    ResultadoOperacionDTO registrar(Funcionario funcionario);

    ResultadoOperacionDTO modificar(Funcionario funcionario);

    // baja logica de funcionario y usuario
    ResultadoOperacionDTO eliminar(String id);

    // "contiene", sin distinguir mayusculas; null o vacio = sin ese filtro. Solo activos.
    List<Funcionario> buscar(String idParcial, String nombreParcial);

    // solo activos
    List<Funcionario> listarTodos();
}
