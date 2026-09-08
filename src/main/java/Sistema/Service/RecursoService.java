package Sistema.Service;

import java.util.List;

import Sistema.DTO.RecursoDTO;
import Sistema.Logica.RecursoLogica;

public class RecursoService {

    private RecursoLogica recursoLogica = new RecursoLogica();

    public RecursoService(){}
    public RecursoService(RecursoLogica recursoLogica) { this.recursoLogica = recursoLogica; }

    public int registrarRecurso(RecursoDTO nuevoRecurso) { return recursoLogica.registrarRecurso(nuevoRecurso); }

    public String generarNuevoId() { return recursoLogica.generarNuevoId(); }

    public RecursoDTO buscarRecursoID(String id) { return recursoLogica.buscarRecursoID(id); }

    public List<RecursoDTO> filtrarRecursos(String idCategoria) { return recursoLogica.filtrarRecursos(idCategoria); }

    public List<RecursoDTO> filtrarRecursos(String idCategoria, String descripcion) { return recursoLogica.filtrarRecursos(idCategoria, descripcion); }

    public List<RecursoDTO> listarRecursos() { return recursoLogica.listarRecursos(); }

    public boolean modificarRecurso(RecursoDTO recurso){ return recursoLogica.modificarRecurso(recurso); }

    public boolean eliminarRecurso(String id){ return recursoLogica.eliminarRecurso(id); }
}
