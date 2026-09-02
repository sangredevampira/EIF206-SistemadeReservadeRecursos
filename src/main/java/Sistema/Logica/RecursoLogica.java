package Sistema.Logica;

import Sistema.DTO.RecursoDTO;
import Sistema.Datos.RecursoDatos;

import java.util.List;
import java.util.Random;

public class RecursoLogica {

    private RecursoDatos recursoDatos;

    public RecursoLogica() {
        this.recursoDatos = new RecursoDatos("recursos.json");
    }
    public RecursoLogica(String rutaArchivo) {
        this.recursoDatos = new RecursoDatos(rutaArchivo);
    }

    private void validarCampos(RecursoDTO recurso) {             // evalua como se validan los errores (sujeto a cambios del throw)
        if (recurso.getCategoria() == null)
            throw new IllegalArgumentException("Debe seleccionar una categoría");
        if (recurso.getDescripcion() == null || recurso.getDescripcion().isBlank())
            throw new IllegalArgumentException("La descripción no puede estar vacía");
    }

    public int registrarRecurso(RecursoDTO nuevoRecurso) {
        if(nuevoRecurso == null)
            throw new IllegalArgumentException("El recurso que se intenta registrar no existe");

        int registrado = 0;
        validarCampos(nuevoRecurso);

        if (!recursoDatos.recursoExistente(nuevoRecurso.getId())) {
            Random random = new Random();
            int id;

            do {
                id = random.nextInt(900000) + 100000;
            } while (recursoDatos.recursoExistente(String.valueOf(id)));

            nuevoRecurso.setId(String.valueOf(id));
            recursoDatos.agregarRecurso(nuevoRecurso);
            registrado = 1;
        }

        return registrado;
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
        validarCampos(recurso);

        if(!recursoDatos.recursoExistente(recurso.getId()))
            throw new IllegalArgumentException("El recurso que se intenta modificar no existe");

        RecursoDTO actual = recursoDatos.buscarRecursoID(recurso.getId());
        boolean cambioNumeroActivo = !actual.getId().equals(recurso.getId());

        if(cambioNumeroActivo && recursoDatos.recursoExistente(recurso.getId()))
            throw new IllegalArgumentException("Ya existe otro recurso con ese ID");

        if (!recurso.getId().matches("\\d+"))
            throw new IllegalArgumentException("El ID del recurso debe contener únicamente números");

        return recursoDatos.modificarRecurso(recurso);
    }

    public boolean eliminarRecurso(String id){
        if(!recursoDatos.recursoExistente(id))
            throw new IllegalArgumentException("El recurso que intenta eliminar no existe");

        //TODO: coordinar con ReservaLogica para impedir eliminar
        // si el recurso tiene reservas futuras activas

        //if (reservaLogica.tieneReservasFuturasActivas(id))
        //    throw new IllegalArgumentException(
        //        "No se puede eliminar el recurso porque tiene reservas futuras activas");

        return recursoDatos.eliminarRecurso(id);
    }

    // metodo que crea unicamente el ID
    public String generarNuevoId() {
        Random random = new Random();
        int id;
        do {
            id = random.nextInt(900000) + 100000;
        } while (recursoDatos.recursoExistente(String.valueOf(id)));

        return String.valueOf(id);
    }
}