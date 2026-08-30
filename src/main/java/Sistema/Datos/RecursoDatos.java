package Sistema.Datos;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import java.util.List;
import Sistema.DTO.RecursoDTO;
import java.util.List;

public class RecursoDatos {

    private String rutaArchivo;

    public RecursoDatos(){}

    public RecursoDatos(String rutaArchivo) { this.rutaArchivo = rutaArchivo; }

    public String getRutaArchivo() { return rutaArchivo; }
    public void setRutaArchivo(String rutaArchivo) { this.rutaArchivo = rutaArchivo; }

    public void serializar(List<RecursoDTO> recursos){
        ObjectMapper mapper = new ObjectMapper();
        try {
            mapper.writeValue(new File(rutaArchivo), recursos);
        } catch (IOException error) { System.out.println("Error al guardar los recursos en JSON"); }
    }

    public List<RecursoDTO> deserializar(){
        ObjectMapper mapper = new ObjectMapper();
        List<RecursoDTO> recursos = null;

        try{
            recursos = mapper.readValue(
                    new File("recursos.json"),
                    new TypeReference<List<RecursoDTO>>() {}
            );

        } catch (JsonProcessingException error){ System.out.println("Error al procesar el JSON"); }
        catch (IOException error){ System.out.println("Error al leer el archivo JSON"); }

        return recursos;
    }

    public void agregarRecurso(RecursoDTO recursoNuevo){
       List<RecursoDTO> recursos = deserializar();
       recursos.add(recursoNuevo);
       serializar(recursos);
    }

}