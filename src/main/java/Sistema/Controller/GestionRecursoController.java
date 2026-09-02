package Sistema.Controller;

import Sistema.DTO.RecursoDTO;
import Sistema.Service.RecursoService;
import java.util.List;

public class GestionRecursoController {

    private RecursoService recursoService;

    public GestionRecursoController() { this.recursoService = new RecursoService(); }

    public RecursoDTO buscarRecursoID(String id){
        return recursoService.buscarRecursoID(id);
    }

    public String generarNuevoId() { return recursoService.generarNuevoId(); }

    public int registrarRecurso(RecursoDTO nuevoRecurso) { return recursoService.registrarRecurso(nuevoRecurso); }

    public List<RecursoDTO> listarRecursos() { return recursoService.listarRecursos(); }

    public boolean modificarRecurso(RecursoDTO recurso) { return recursoService.modificarRecurso(recurso); }

    public boolean eliminarRecurso(String id) {
        // TODO: cuando exista GestionReservaController, coordinar aquí para
        // impedir eliminar un recurso con reservas futuras activas, por ejemplo:
        //
        // if (gestionReservaController.tieneReservasFuturas(id)) {
        //     throw new IllegalArgumentException("No se puede eliminar: tiene reservas futuras activas.");
        // }
        return recursoService.eliminarRecurso(id);
    }

    public List<RecursoDTO> filtrarPorCategoria(String categoria) { return recursoService.filtrarPorCategoria(categoria); }
}
