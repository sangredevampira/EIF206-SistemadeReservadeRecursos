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

    private void validarCampos(RecursoDTO recurso) {     // evaluar como se validan los errores (sujeto a cambios)
        if (recurso.getNumeroActivo() == null || recurso.getNumeroActivo().isBlank())
            throw new IllegalArgumentException("El numero de activo no puede estar vacio");
        if (recurso.getIdCategoria() == null || recurso.getIdCategoria().isBlank())
            throw new IllegalArgumentException("Debe seleccionar una categoría.");
        if (recurso.getDescripcion() == null || recurso.getDescripcion().isBlank())
            throw new IllegalArgumentException("La descripción no puede estar vacía.");
    }

    public int registrarRecurso(RecursoDTO nuevoRecurso) {
        int registrado = 0;
        validarCampos(nuevoRecurso);

        if (!recursoDatos.numeroActivoExistente(nuevoRecurso.getNumeroActivo())) {
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

    public RecursoDTO buscarRecursoID(String id) { return recursoDatos.buscarRecursoID(id); }

    public List<RecursoDTO> filtrarPorCategoria(String categoria) { return recursoDatos.filtrarPorCategoria(categoria); }

    public boolean modificarRecurso(RecursoDTO recurso){
        validarCampos(recurso);

        if(!recursoDatos.recursoExistente(recurso.getId()))
            throw new IllegalArgumentException("El recurso que intenta modificar no existe");

        RecursoDTO actual = recursoDatos.buscarRecursoID(recurso.getId());
        boolean cambioNumeroActivo = !actual.getNumeroActivo().equals(recurso.getNumeroActivo());

        if(cambioNumeroActivo && recursoDatos.numeroActivoExistente(recurso.getNumeroActivo()))
            throw new IllegalArgumentException("Ya existe otro recurso con ese número de activo");

        return recursoDatos.modificarRecurso(recurso);
    }

    public boolean eliminarRecurso(String id){
        if(!recursoDatos.recursoExistente(id))
            throw new IllegalArgumentException("El recurso que intenta eliminar no existe");

        //TODO: coordinar con ReservaLogica para impedir eliminar
        // si el recurso tiene reservas futuras activas

        return recursoDatos.eliminarRecurso(id);
    }
}