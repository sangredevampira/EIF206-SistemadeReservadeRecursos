package Sistema.Service.Red;

import Sistema.DTO.Funcionario;
import Sistema.DTO.ResultadoOperacionDTO;
import Sistema.Red.Operaciones;
import Sistema.Red.ProxyBase;
import Sistema.Service.IFuncionarioService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * Representa en el Frontend al FuncionarioService del Backend (FUNC_*;
 * docs/00_contratos.md, seccion 3). Sin reglas de negocio: manda cada pedido por
 * la red y devuelve el resultado. Todas las operaciones son de administrador
 * (lo revisa ManejadorFuncionarios en el servidor).
 */
public class ProxyFuncionarioService extends ProxyBase implements IFuncionarioService {

    // El Funcionario viaja tal cual: {id, nombre, telefono} (reservas tiene @JsonIgnore)
    @Override
    public ResultadoOperacionDTO registrar(Funcionario funcionario) {
        return ApoyoProxies.resultado(() -> ejecutar(Operaciones.FUNC_CREAR, funcionario));
    }

    @Override
    public ResultadoOperacionDTO modificar(Funcionario funcionario) {
        return ApoyoProxies.resultado(() -> ejecutar(Operaciones.FUNC_EDITAR, funcionario));
    }

    // Baja logica de funcionario y usuario
    @Override
    public ResultadoOperacionDTO eliminar(String id) {
        return ApoyoProxies.resultado(() -> ejecutar(Operaciones.FUNC_BAJA,
                ApoyoProxies.datos("idFuncionario", id)));
    }

    // Un filtro null no se envia (= sin ese filtro)
    @Override
    public List<Funcionario> buscar(String idParcial, String nombreParcial) {
        return aLista(ejecutar(Operaciones.FUNC_BUSCAR,
                ApoyoProxies.datos("idParcial", idParcial, "nombreParcial", nombreParcial)));
    }

    @Override
    public List<Funcionario> listarTodos() {
        return aLista(ejecutar(Operaciones.FUNC_LISTAR, null));
    }

    private List<Funcionario> aLista(JsonNode nodo) {
        return ApoyoProxies.aLista(json, nodo, new TypeReference<List<Funcionario>>() { }, "funcionarios");
    }
}
