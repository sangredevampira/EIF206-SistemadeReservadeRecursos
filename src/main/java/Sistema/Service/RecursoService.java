package Sistema.Service;

import Sistema.Logica.RecursoLogica;
import Sistema.DTO.RecursoDTO;

import java.util.List;

public class RecursoService {

    private RecursoLogica recursoLogica = new RecursoLogica();

    public RecursoService(){}
    public RecursoService(RecursoLogica recursoLogica) { this.recursoLogica = recursoLogica; }

    public int registrarRecurso(RecursoDTO nuevoRecurso) { return recursoLogica.registrarRecurso(nuevoRecurso); }

    public String generarNuevoId() { return recursoLogica.generarNuevoId(); }

    public RecursoDTO buscarRecursoID(String id) { return recursoLogica.buscarRecursoID(id); }

    public List<RecursoDTO> filtrarPorCategoria(String categoria) { return recursoLogica.filtrarPorCategoria(categoria); }

    public List<RecursoDTO> listarRecursos() { return recursoLogica.listarRecursos(); }

    public boolean modificarRecurso(RecursoDTO recurso){ return recursoLogica.modificarRecurso(recurso); }

    public boolean eliminarRecurso(String id){ return recursoLogica.eliminarRecurso(id); }
}
