package Sistema.Red;

import Sistema.DTO.RecursoDTO;
import Sistema.Service.IRecursoService;
import com.fasterxml.jackson.databind.JsonNode;

// Operaciones de recursos: REC_CREAR, REC_EDITAR, REC_ELIMINAR, REC_BUSCAR,
// REC_LISTAR y REC_NUEVO_ID (docs/00_contratos.md, seccion 3). Sigue el patron
// de ManejadorAuth.
//
// Lecturas con las constantes que ya existen (sin pedir una nueva):
//   REC_LISTAR {idCategoria?, descripcion?}: sin filtros -> listarRecursos();
//              con alguno -> filtrarRecursos(idCategoria, descripcion).
//   REC_BUSCAR {idRecurso}: un recurso por ID -> buscarRecursoID.
//
// Permisos: REC_LISTAR solo exige sesion (Calendarizacion filtra recursos por
// categoria con un funcionario). Las demas son de administrador.
//
// En el servidor hay una sola instancia para todos los hilos: no guarda estado propio.
public class ManejadorRecursos {

    private final IRecursoService servicio;

    public ManejadorRecursos(IRecursoService servicio) {
        this.servicio = servicio;
    }

    // Registra sus seis operaciones en el despachador
    public void registrarEn(DespachadorPeticiones despachador) {
        despachador.registrar(Operaciones.REC_CREAR, this::crear);
        despachador.registrar(Operaciones.REC_EDITAR, this::editar);
        despachador.registrar(Operaciones.REC_ELIMINAR, this::eliminar);
        despachador.registrar(Operaciones.REC_BUSCAR, this::buscar);
        despachador.registrar(Operaciones.REC_LISTAR, this::listar);
        despachador.registrar(Operaciones.REC_NUEVO_ID, this::nuevoId);
    }

    // datos = RecursoDTO {id, descripcion, categoria {id, descripcion}} -> ResultadoOperacionDTO
    private Object crear(JsonNode datos, ContextoSesion contexto) {
        contexto.exigirAdministrador();
        return servicio.registrarRecurso(DatosSolicitud.objeto(datos, RecursoDTO.class));
    }

    // datos = RecursoDTO {id, descripcion, categoria} -> ResultadoOperacionDTO
    private Object editar(JsonNode datos, ContextoSesion contexto) {
        contexto.exigirAdministrador();
        return servicio.modificarRecurso(DatosSolicitud.objeto(datos, RecursoDTO.class));
    }

    // datos = {idRecurso} -> ResultadoOperacionDTO (baja logica)
    private Object eliminar(JsonNode datos, ContextoSesion contexto) {
        contexto.exigirAdministrador();
        return servicio.eliminarRecurso(DatosSolicitud.texto(datos, "idRecurso"));
    }

    // datos = {idRecurso} -> RecursoDTO. Si no existe, el Service lanza
    // IllegalArgumentException y el despachador responde exito=false con el mensaje.
    private Object buscar(JsonNode datos, ContextoSesion contexto) {
        contexto.exigirAdministrador();
        return servicio.buscarRecursoID(DatosSolicitud.texto(datos, "idRecurso"));
    }

    // datos = {idCategoria?, descripcion?} -> lista de RecursoDTO activos. Cualquier sesion.
    private Object listar(JsonNode datos, ContextoSesion contexto) {
        String idCategoria = DatosSolicitud.textoOpcional(datos, "idCategoria");
        String descripcion = DatosSolicitud.textoOpcional(datos, "descripcion");
        if (idCategoria == null && descripcion == null) {
            return servicio.listarRecursos();
        }
        return servicio.filtrarRecursos(idCategoria, descripcion);
    }

    // sin datos -> texto con el ID nuevo
    private Object nuevoId(JsonNode datos, ContextoSesion contexto) {
        contexto.exigirAdministrador();
        return servicio.generarNuevoId();
    }
}
