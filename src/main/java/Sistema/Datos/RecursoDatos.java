package Sistema.Datos;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;

import java.util.List;
import java.util.ArrayList;
import Sistema.DTO.RecursoDTO;

public class RecursoDatos {

    private String rutaArchivo;

    public RecursoDatos(){}
    public RecursoDatos(String rutaArchivo) { this.rutaArchivo = rutaArchivo; }

    public String getRutaArchivo() { return rutaArchivo; }
    public void setRutaArchivo(String rutaArchivo) { this.rutaArchivo = rutaArchivo; }

    public void serializar(List<RecursoDTO> recursos){
        ObjectMapper mapper = new ObjectMapper();   // permite convertir de objeto Java a JSON

        try {
            mapper.writeValue(new File(rutaArchivo), recursos);
        } catch (IOException error) { System.out.println("Error al guardar los recursos en JSON"); }
    }

    public List<RecursoDTO> deserializar(){
        ObjectMapper mapper = new ObjectMapper();
        List<RecursoDTO> recursos = null;

        try{
            recursos = mapper.readValue(
                    new File(rutaArchivo),
                    new TypeReference<List<RecursoDTO>>() {}
            );

        } catch (JsonProcessingException error){ System.out.println("Error al procesar el JSON"); }
        catch (IOException error){ System.out.println("Error al leer el archivo de Recursos"); }

        return recursos;
    }

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
            if(r.getIdCategoria().equalsIgnoreCase(categoria))
                recursosFiltrados.add(r);

        return recursosFiltrados;
    }

    // metodos de comprobacion
    public boolean numeroActivoExistente(String numeroActivo){
        List<RecursoDTO> recursos = deserializar();
        for(RecursoDTO r : recursos)
            if(r.getNumeroActivo().equals(numeroActivo)) return true;

        return false;
    }

    public boolean recursoExistente(String id){     // comprobar si es necesario usarlo
        List<RecursoDTO> recursos = deserializar();
        for(RecursoDTO r : recursos)
            if(r.getId().equals(id))
                return true;

        return false;
    }
}