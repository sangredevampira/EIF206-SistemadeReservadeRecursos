package Sistema.Logica;

import Sistema.DTO.RecursoDTO;
import Sistema.Datos.RecursoDatos;

import java.util.List;
import java.util.Random;

public class RecursoLogica {

    private RecursoDatos recursoDatos;
    private ReservaLogica reservaLogica;

    public RecursoLogica() {
        this.recursoDatos = new RecursoDatos("recursos.json");
    }
    public RecursoLogica(String rutaArchivo) {
        this.recursoDatos = new RecursoDatos(rutaArchivo);
    }

    private void validarInfomacion(RecursoDTO recurso) {             // evalua como se validan los errores (sujeto a cambios del throw)
        if (recurso.getCategoria() == null)
            throw new IllegalArgumentException("Debe seleccionar una categoría");
        if (recurso.getDescripcion() == null || recurso.getDescripcion().isBlank())
            throw new IllegalArgumentException("La descripción no puede estar vacía");
    }

    public int registrarRecurso(RecursoDTO nuevoRecurso) {
        if(nuevoRecurso == null)
            throw new IllegalArgumentException("El recurso que se intenta registrar no existe");

        validarInfomacion(nuevoRecurso);

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

    public List<RecursoDTO> filtrarPorCategoria(String categoria) {
        List<RecursoDTO> listaRecursos = recursoDatos.filtrarPorCategoria(categoria);
        if(listaRecursos == null)
            throw new IllegalArgumentException("Los recursos no se pudieron filtrar correctamente");
        return listaRecursos;
    }

    public List<RecursoDTO> listarRecursos() {
        List<RecursoDTO> listaRecursos = recursoDatos.listarRecursos();
        if(listaRecursos == null)
            throw new IllegalArgumentException("Los recursos no se pudieron listar correctamente");
        return listaRecursos;
    }

    public boolean modificarRecurso(RecursoDTO recurso){
        validarInfomacion(recurso);

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
        if(!recursoExistente(id))
            throw new IllegalArgumentException("El recurso que intenta eliminar no existe");

        //TODO: coordinar con ReservaLogica/Service para impedir eliminar
        // si el recurso tiene reservas futuras activas

        // 1. validacion propia de Recurso
        //if (!recursoDatos.recursoExistente(id)) {
        //        throw new IllegalArgumentException(
        //            "El recurso que intenta eliminar no existe" );
        //    }

        // 2. regla de negocio que involucra Reserva
        //if (reservaLogica.tieneReservasFuturasActivas(id))
        //    throw new IllegalArgumentException(
        //        "No se puede eliminar el recurso porque tiene reservas futuras activas");

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

    // metodo de comprobacion
    private boolean recursoExistente(String id){
        List<RecursoDTO> recursos = recursoDatos.deserializar();
        for(RecursoDTO r : recursos)
            if(r.getId().equals(id))
                return true;

        return false;
    }
}