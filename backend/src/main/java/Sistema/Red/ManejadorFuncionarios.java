package Sistema.Red;

import Sistema.DTO.Funcionario;
import Sistema.Service.IFuncionarioService;
import com.fasterxml.jackson.databind.JsonNode;

// Operaciones de funcionarios: FUNC_CREAR, FUNC_EDITAR, FUNC_BAJA, FUNC_BUSCAR
// y FUNC_LISTAR (docs/00_contratos.md, seccion 3). Todas son solo de
// administrador. Sigue el patron de ManejadorAuth.
//
// Recibe la interfaz (no FuncionarioService) para que las pruebas usen un
// Service falso. En el servidor hay una sola instancia para todos los hilos:
// no guarda estado propio.
public class ManejadorFuncionarios {

    private final IFuncionarioService servicio;

    public ManejadorFuncionarios(IFuncionarioService servicio) {
        this.servicio = servicio;
    }

    // Registra sus cinco operaciones en el despachador
    public void registrarEn(DespachadorPeticiones despachador) {
        despachador.registrar(Operaciones.FUNC_CREAR, this::crear);
        despachador.registrar(Operaciones.FUNC_EDITAR, this::editar);
        despachador.registrar(Operaciones.FUNC_BAJA, this::baja);
        despachador.registrar(Operaciones.FUNC_BUSCAR, this::buscar);
        despachador.registrar(Operaciones.FUNC_LISTAR, this::listar);
    }

    // El permiso va primero: un funcionario recibe "No tiene permiso..." aunque mande datos malos.

    // datos = Funcionario {id, nombre, telefono} -> ResultadoOperacionDTO
    private Object crear(JsonNode datos, ContextoSesion contexto) {
        contexto.exigirAdministrador();
        return servicio.registrar(DatosSolicitud.objeto(datos, Funcionario.class));
    }

    // datos = Funcionario {id, nombre, telefono}; el id no cambia -> ResultadoOperacionDTO
    private Object editar(JsonNode datos, ContextoSesion contexto) {
        contexto.exigirAdministrador();
        return servicio.modificar(DatosSolicitud.objeto(datos, Funcionario.class));
    }

    // datos = {idFuncionario} -> ResultadoOperacionDTO (baja logica de funcionario y usuario)
    private Object baja(JsonNode datos, ContextoSesion contexto) {
        contexto.exigirAdministrador();
        return servicio.eliminar(DatosSolicitud.texto(datos, "idFuncionario"));
    }

    // datos = {idParcial?, nombreParcial?} -> lista de Funcionario activos
    private Object buscar(JsonNode datos, ContextoSesion contexto) {
        contexto.exigirAdministrador();
        return servicio.buscar(
                DatosSolicitud.textoOpcional(datos, "idParcial"),
                DatosSolicitud.textoOpcional(datos, "nombreParcial"));
    }

    // sin datos -> lista de Funcionario activos
    private Object listar(JsonNode datos, ContextoSesion contexto) {
        contexto.exigirAdministrador();
        return servicio.listarTodos();
    }
}
