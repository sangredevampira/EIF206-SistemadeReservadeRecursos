package Sistema.Service;

import java.util.List;

import Sistema.DTO.RecursoDTO;
import Sistema.DTO.ResultadoOperacionDTO;
import Sistema.Logica.RecursoLogica;

// Capa Service: solo pasa las solicitudes a RecursoLogica, sin validaciones.
// En el servidor hay una sola instancia para todos los hilos: no guarda estado.
public class RecursoService implements IRecursoService {

    private final RecursoLogica recursoLogica;

    public RecursoService() { this(new RecursoLogica()); }

    // para pruebas: permite pasar una RecursoLogica falsa
    public RecursoService(RecursoLogica recursoLogica) { this.recursoLogica = recursoLogica; }

    @Override
    public ResultadoOperacionDTO registrarRecurso(RecursoDTO nuevoRecurso) { return recursoLogica.registrarRecurso(nuevoRecurso); }

    @Override
    public String generarNuevoId() { return recursoLogica.generarNuevoId(); }

    @Override
    public RecursoDTO buscarRecursoID(String id) { return recursoLogica.buscarRecursoID(id); }

    @Override
    public List<RecursoDTO> filtrarRecursos(String idCategoria) { return recursoLogica.filtrarRecursos(idCategoria); }

    @Override
    public List<RecursoDTO> filtrarRecursos(String idCategoria, String descripcion) { return recursoLogica.filtrarRecursos(idCategoria, descripcion); }

    @Override
    public List<RecursoDTO> listarRecursos() { return recursoLogica.listarRecursos(); }

    @Override
    public ResultadoOperacionDTO modificarRecurso(RecursoDTO recurso) { return recursoLogica.modificarRecurso(recurso); }

    @Override
    public ResultadoOperacionDTO eliminarRecurso(String id) { return recursoLogica.eliminarRecurso(id); }
}
