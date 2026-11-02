package Sistema.Red;

import Sistema.DTO.CategoriaDTO;
import Sistema.Service.ICategoriaService;
import com.fasterxml.jackson.databind.JsonNode;

// Operaciones de categorias: CAT_CREAR, CAT_EDITAR, CAT_ELIMINAR, CAT_BUSCAR y
// CAT_LISTAR (docs/00_contratos.md, seccion 3). Sigue el patron de ManejadorAuth.
//
// Permisos: CAT_LISTAR solo exige sesion (el despachador ya la exige), porque
// la usan pantallas de funcionario (Reservas y Calendarizacion). Las demas son
// de administrador.
//
// En el servidor hay una sola instancia para todos los hilos: no guarda estado propio.
public class ManejadorCategorias {

    private final ICategoriaService servicio;

    public ManejadorCategorias(ICategoriaService servicio) {
        this.servicio = servicio;
    }

    // Registra sus cinco operaciones en el despachador
    public void registrarEn(DespachadorPeticiones despachador) {
        despachador.registrar(Operaciones.CAT_CREAR, this::crear);
        despachador.registrar(Operaciones.CAT_EDITAR, this::editar);
        despachador.registrar(Operaciones.CAT_ELIMINAR, this::eliminar);
        despachador.registrar(Operaciones.CAT_BUSCAR, this::buscar);
        despachador.registrar(Operaciones.CAT_LISTAR, this::listar);
    }

    // datos = CategoriaDTO {descripcion}; el id lo genera el Backend -> ResultadoOperacionDTO
    private Object crear(JsonNode datos, ContextoSesion contexto) {
        contexto.exigirAdministrador();
        return servicio.registrar(DatosSolicitud.objeto(datos, CategoriaDTO.class));
    }

    // datos = CategoriaDTO {id, descripcion} -> ResultadoOperacionDTO
    private Object editar(JsonNode datos, ContextoSesion contexto) {
        contexto.exigirAdministrador();
        return servicio.modificar(DatosSolicitud.objeto(datos, CategoriaDTO.class));
    }

    // datos = {idCategoria} -> ResultadoOperacionDTO
    private Object eliminar(JsonNode datos, ContextoSesion contexto) {
        contexto.exigirAdministrador();
        return servicio.eliminar(DatosSolicitud.texto(datos, "idCategoria"));
    }

    // datos = {descripcion?} -> lista de CategoriaDTO (solo la usa la pantalla de categorias)
    private Object buscar(JsonNode datos, ContextoSesion contexto) {
        contexto.exigirAdministrador();
        return servicio.buscar(DatosSolicitud.textoOpcional(datos, "descripcion"));
    }

    // sin datos -> lista de CategoriaDTO. Cualquier sesion.
    private Object listar(JsonNode datos, ContextoSesion contexto) {
        return servicio.listarTodos();
    }
}
