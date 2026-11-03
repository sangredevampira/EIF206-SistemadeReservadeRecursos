package Sistema.Service.Red;

import Sistema.DTO.CategoriaDTO;
import Sistema.DTO.ResultadoOperacionDTO;
import Sistema.Red.Operaciones;
import Sistema.Red.ProxyBase;
import Sistema.Service.ICategoriaService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * Representa en el Frontend al CategoriaService del Backend (CAT_*;
 * docs/00_contratos.md, seccion 3). Sin reglas de negocio: manda cada pedido por
 * la red y devuelve el resultado. CAT_LISTAR acepta cualquier sesion (la usan
 * Reservas y Calendarizacion); las demas son de administrador.
 */
public class ProxyCategoriaService extends ProxyBase implements ICategoriaService {

    // La CategoriaDTO viaja tal cual; el id va en null porque lo genera el Backend
    @Override
    public ResultadoOperacionDTO registrar(CategoriaDTO categoria) {
        return ApoyoProxies.resultado(() -> ejecutar(Operaciones.CAT_CREAR, categoria));
    }

    @Override
    public ResultadoOperacionDTO modificar(CategoriaDTO categoria) {
        return ApoyoProxies.resultado(() -> ejecutar(Operaciones.CAT_EDITAR, categoria));
    }

    @Override
    public ResultadoOperacionDTO eliminar(String id) {
        return ApoyoProxies.resultado(() -> ejecutar(Operaciones.CAT_ELIMINAR,
                ApoyoProxies.datos("idCategoria", id)));
    }

    // Un filtro null no se envia (= todas)
    @Override
    public List<CategoriaDTO> buscar(String descripcionParcial) {
        return aLista(ejecutar(Operaciones.CAT_BUSCAR,
                ApoyoProxies.datos("descripcion", descripcionParcial)));
    }

    @Override
    public List<CategoriaDTO> listarTodos() {
        return aLista(ejecutar(Operaciones.CAT_LISTAR, null));
    }

    private List<CategoriaDTO> aLista(JsonNode nodo) {
        return ApoyoProxies.aLista(json, nodo, new TypeReference<List<CategoriaDTO>>() { }, "categorías");
    }
}
