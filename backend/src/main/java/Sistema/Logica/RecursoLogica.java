package Sistema.Logica;

import Sistema.DTO.RecursoDTO;
import Sistema.Datos.RecursoDatos;

import java.util.List;
import java.util.Random;

public class RecursoLogica {

    private final RecursoDatos recursoDatos;
    private ReservaLogica reservaLogica;

    public RecursoLogica() { this.recursoDatos = new RecursoDatos("src/data/recursos.json"); }

    private ReservaLogica getReservaLogica() {
        if (reservaLogica == null) reservaLogica = new ReservaLogica();
        return reservaLogica;
    }

    public int registrarRecurso(RecursoDTO nuevoRecurso) {
        if(nuevoRecurso == null)
            throw new IllegalArgumentException("El recurso que se intenta registrar no existe");

        validarDatosGenerales(nuevoRecurso);

        if (nuevoRecurso.getId() == null || nuevoRecurso.getId().isBlank())
            throw new IllegalArgumentException("El recurso no tiene un ID asignado");

        recursoDatos.agregarRecurso(nuevoRecurso);
        return 1;
    }

    public RecursoDTO buscarRecursoID(String id) {
        RecursoDTO recurso = recursoDatos.buscarRecursoID(id);
        if(recurso == null)
            throw new IllegalArgumentException("El recurso solicitado no fue encontrado");
        return recurso;
    }

    // sobrecarga para usar en CategoriaLogica
    public List<RecursoDTO> filtrarRecursos(String idCategoria) {
        String categoriaLimpia = (idCategoria != null && !idCategoria.isBlank())
                ? idCategoria.trim()
                : null;

        List<RecursoDTO> filtrados = recursoDatos.filtrarRecursos(categoriaLimpia);

        if (filtrados == null)
            throw new IllegalArgumentException("No se pudieron filtrar los recursos por categoría");

        return filtrados;
    }

    public List<RecursoDTO> filtrarRecursos(String idCategoria, String descripcion) {
        String categoriaLimpia = (idCategoria != null && !idCategoria.isBlank())
                ? idCategoria.trim()
                : null;

        String descripcionLimpia = (descripcion != null && !descripcion.isBlank())
                ? descripcion.trim().toLowerCase()
                : null;

        List<RecursoDTO> filtrados = recursoDatos.filtrarRecursos(categoriaLimpia, descripcionLimpia);

        if (filtrados == null)
            throw new IllegalArgumentException("No se pudieron filtrar los recursos correctamente");

        return filtrados;
    }

    public List<RecursoDTO> listarRecursos() {
        List<RecursoDTO> listaRecursos = recursoDatos.listarRecursos();
        if(listaRecursos == null)
            throw new IllegalArgumentException("Los recursos no se pudieron listar correctamente");
        return listaRecursos;
    }

    public boolean modificarRecurso(RecursoDTO recurso){
        validarDatosGenerales(recurso);

        if(!recursoExistente(recurso.getId()))
            throw new IllegalArgumentException("El recurso que se intenta modificar no existe");

        RecursoDTO actual = recursoDatos.buscarRecursoID(recurso.getId());
        boolean cambioNumeroActivo = !actual.getId().equals(recurso.getId());

        if(cambioNumeroActivo && recursoExistente(recurso.getId()))
            throw new IllegalArgumentException("Ya existe otro recurso con ese ID");

        if (!recurso.getId().matches("\\d+"))
            throw new IllegalArgumentException("El ID del recurso debe contener únicamente números");

        return recursoDatos.modificarRecurso(recurso);
    }

    public boolean eliminarRecurso(String id){
        // 1. validacion propia de Recurso
        if (!recursoExistente(id))
            throw new IllegalArgumentException("El recurso que se intenta eliminar no existe");

        // 2. coordinacion con reserva
        if (getReservaLogica().tieneReservasFuturasActivas(id))
            throw new IllegalArgumentException("No se puede eliminar el recurso porque tiene reservas futuras activas");

        // 3. si no hay ningun problema eliminamos
        return recursoDatos.eliminarRecurso(id);
    }

    // metodo que crea unicamente el ID
    public String generarNuevoId() {
        Random random = new Random();
        int id;
        do {
            id = random.nextInt(900000) + 100000;
        } while (recursoExistente(String.valueOf(id)));

        return String.valueOf(id);
    }

    // metodos de comprobacion y validacion
    private void validarDatosGenerales(RecursoDTO recurso) {
        if (recurso.getCategoria() == null)
            throw new IllegalArgumentException("Debe seleccionar una categoría");
        if (recurso.getDescripcion() == null || recurso.getDescripcion().isBlank())
            throw new IllegalArgumentException("La descripción no puede estar vacía");
    }

    private boolean recursoExistente(String id){
        List<RecursoDTO> recursos = recursoDatos.listarRecursos();
        for(RecursoDTO r : recursos)
            if(r.getId().equals(id))
                return true;

        return false;
    }
}