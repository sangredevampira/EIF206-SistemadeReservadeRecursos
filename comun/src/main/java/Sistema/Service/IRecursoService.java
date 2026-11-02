package Sistema.Service;

import Sistema.DTO.RecursoDTO;
import Sistema.DTO.ResultadoOperacionDTO;

import java.util.List;

// contrato de recursos. Lo implementan el RecursoService del Backend y, en el
// Frontend, el proxy que envia REC_CREAR, REC_EDITAR, REC_ELIMINAR, REC_BUSCAR,
// REC_LISTAR y REC_NUEVO_ID. Las listas devuelven solo recursos activos, con su
// categoria. filtrarRecursos(idCategoria) tambien lo usan las pantallas de
// funcionario (Calendarizacion).
public interface IRecursoService {

    ResultadoOperacionDTO registrarRecurso(RecursoDTO recurso);

    ResultadoOperacionDTO modificarRecurso(RecursoDTO recurso);

    // baja logica
    ResultadoOperacionDTO eliminarRecurso(String id);

    // si no existe o esta dado de baja lanza IllegalArgumentException con el mensaje
    RecursoDTO buscarRecursoID(String id);

    // null o vacio = todas las categorias
    List<RecursoDTO> filtrarRecursos(String idCategoria);

    // null o vacio = sin ese filtro; la descripcion se busca como "contiene"
    List<RecursoDTO> filtrarRecursos(String idCategoria, String descripcion);

    List<RecursoDTO> listarRecursos();

    // ID nuevo de 6 digitos que no usa ningun recurso (tampoco los dados de baja)
    String generarNuevoId();
}
