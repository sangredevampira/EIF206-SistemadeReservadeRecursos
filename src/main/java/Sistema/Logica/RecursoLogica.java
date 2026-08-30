package Sistema.Logica;

import Sistema.DTO.RecursoDTO;
import Sistema.Datos.RecursoDatos;

import java.util.Random;

public class RecursoLogica {

    private RecursoDatos recursoDatos;

    public RecursoLogica() { this.recursoDatos = new RecursoDatos("recursos.json"); }
    public RecursoLogica(String rutaArchivo) { this.recursoDatos = new RecursoDatos(rutaArchivo); }

    private void validarCampos(RecursoDTO recurso){     // evaluar como se validan los errores (sujeto a cambios)
        if(recurso.getNumeroActivo() == null || recurso.getNumeroActivo().isBlank())
            throw new IllegalArgumentException("El numero de activo no puede estar vacio");
        if(recurso.getIdCategoria() == null || recurso.getIdCategoria().isBlank())
            throw new IllegalArgumentException("Debe seleccionar una categoría.");
        if (recurso.getDescripcion() == null || recurso.getDescripcion().isBlank())
            throw new IllegalArgumentException("La descripción no puede estar vacía.");
    }

    public int registrarRecurso(RecursoDTO nuevoRecurso){
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
}
