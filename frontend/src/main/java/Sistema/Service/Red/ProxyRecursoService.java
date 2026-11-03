package Sistema.Service.Red;

import Sistema.DTO.RecursoDTO;
import Sistema.DTO.ResultadoOperacionDTO;
import Sistema.Red.Operaciones;
import Sistema.Red.ProxyBase;
import Sistema.Service.IRecursoService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * Representa en el Frontend al RecursoService del Backend (REC_*;
 * docs/00_contratos.md, seccion 3). Sin reglas de negocio: manda cada pedido por
 * la red y devuelve el resultado.
 *
 * Las tres lecturas de listas usan REC_LISTAR {idCategoria?, descripcion?}:
 * sin filtros el servidor llama listarRecursos(); con alguno, filtrarRecursos.
 * REC_BUSCAR es por ID. REC_LISTAR acepta cualquier sesion (Calendarizacion);
 * las demas son de administrador.
 */
public class ProxyRecursoService extends ProxyBase implements IRecursoService {

    // El RecursoDTO viaja tal cual: {id, descripcion, categoria {id, descripcion}}
    @Override
    public ResultadoOperacionDTO registrarRecurso(RecursoDTO recurso) {
        return ApoyoProxies.resultado(() -> ejecutar(Operaciones.REC_CREAR, recurso));
    }

    @Override
    public ResultadoOperacionDTO modificarRecurso(RecursoDTO recurso) {
        return ApoyoProxies.resultado(() -> ejecutar(Operaciones.REC_EDITAR, recurso));
    }

    // Baja logica
    @Override
    public ResultadoOperacionDTO eliminarRecurso(String id) {
        return ApoyoProxies.resultado(() -> ejecutar(Operaciones.REC_ELIMINAR,
                ApoyoProxies.datos("idRecurso", id)));
    }

    // Si no existe, el servidor responde exito=false y ProxyBase lanza
    // IllegalArgumentException con su mensaje (igual que el Service): se deja pasar.
    @Override
    public RecursoDTO buscarRecursoID(String id) {
        RecursoDTO recurso = ejecutar(Operaciones.REC_BUSCAR,
                ApoyoProxies.datos("idRecurso", id), RecursoDTO.class);
        return ApoyoProxies.obligatorio(recurso, "el recurso solicitado");
    }

    // Solo idCategoria (null = todas las categorias)
    @Override
    public List<RecursoDTO> filtrarRecursos(String idCategoria) {
        return aLista(ejecutar(Operaciones.REC_LISTAR,
                ApoyoProxies.datos("idCategoria", idCategoria)));
    }

    // Un filtro null no se envia (= sin ese filtro)
    @Override
    public List<RecursoDTO> filtrarRecursos(String idCategoria, String descripcion) {
        return aLista(ejecutar(Operaciones.REC_LISTAR,
                ApoyoProxies.datos("idCategoria", idCategoria, "descripcion", descripcion)));
    }

    @Override
    public List<RecursoDTO> listarRecursos() {
        return aLista(ejecutar(Operaciones.REC_LISTAR, null));
    }

    @Override
    public String generarNuevoId() {
        String id = ejecutar(Operaciones.REC_NUEVO_ID, null, String.class);
        return ApoyoProxies.obligatorio(id, "un ID nuevo para el recurso");
    }

    private List<RecursoDTO> aLista(JsonNode nodo) {
        return ApoyoProxies.aLista(json, nodo, new TypeReference<List<RecursoDTO>>() { }, "recursos");
    }
}
