package Sistema.Datos;

import java.util.List;
import java.util.ArrayList;
import Sistema.DTO.RecursoDTO;

public class RecursoDatos extends Datos {

    public RecursoDatos() {}
    public RecursoDatos(String rutaArchivo) { super(rutaArchivo); }

    public void agregarRecurso(RecursoDTO recursoNuevo){
       List<RecursoDTO> recursos = deserializar();
       recursos.add(recursoNuevo);
       serializar(recursos);
    }

    public RecursoDTO buscarRecursoID(String id){
        List<RecursoDTO> recursos = deserializar();
        RecursoDTO buscado = null;
        boolean encontrado = false;

        for(RecursoDTO r : recursos)    // para cada RecursoDTO en la lista, lo guardamos temporalmente en r
            if(!encontrado && r.getId().equals(id)){
                buscado = r;
                encontrado = true;
            }

        return buscado;
    }

    public boolean modificarRecurso(RecursoDTO recurso) {
        List<RecursoDTO> recursos = deserializar();
        boolean modificado = false;

        for (int i = 0; i < recursos.size() && !modificado; i++)
            if (recursos.get(i).getId().equals(recurso.getId())) {
                recursos.set(i, recurso);
                modificado = true;
            }

        if (modificado) serializar(recursos);

        return modificado;
    }

    public boolean eliminarRecurso(String id){
        List<RecursoDTO> recursos = deserializar();
        boolean eliminado = recursos.removeIf(r -> r.getId().equals(id));
        if(eliminado) serializar(recursos);
        return eliminado;
    }

    public List<RecursoDTO> filtrarPorCategoria(String categoria){
        List<RecursoDTO> recursos = deserializar();
        List<RecursoDTO> recursosFiltrados = new ArrayList<>();
        for(RecursoDTO r : recursos)
            if(r.getCategoria().getId().equalsIgnoreCase(categoria))
                recursosFiltrados.add(r);

        return recursosFiltrados;
    }
    public List<RecursoDTO> listarRecursos(){ return deserializar(); }

    // metodo de comprobacion
     public boolean recursoExistente(String id){
        List<RecursoDTO> recursos = deserializar();
        for(RecursoDTO r : recursos)
            if(r.getId().equals(id))
                return true;

        return false;
    }
}