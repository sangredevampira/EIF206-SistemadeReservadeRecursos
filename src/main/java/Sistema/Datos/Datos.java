package Sistema.Datos;

import Sistema.DTO.RecursoDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class Datos {
    private String rutaArchivo;

    public Datos(){}
    public Datos(String rutaArchivo) { this.rutaArchivo = rutaArchivo; }

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
}
