package Sistema.Datos;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;

import java.io.File;
import java.util.List;
import java.io.IOException;
import java.util.ArrayList;

public class Datos<T> {

    private String rutaArchivo;
    private Class<T> tipoClase;
    protected ObjectMapper mapper = new ObjectMapper(); // permite convertir de objeto Java a JSON

    public Datos(){}
    public Datos(String rutaArchivo,  Class<T> tipoClase)
    {
        this.rutaArchivo = rutaArchivo;
        this.tipoClase = tipoClase;
    }

    public String getRutaArchivo() { return rutaArchivo; }
    public void setRutaArchivo(String rutaArchivo) { this.rutaArchivo = rutaArchivo; }

    public void serializar(List<T> datos){
        try {
            mapper.writeValue(new File(rutaArchivo), datos);
        } catch (IOException error) { System.out.println("Error al guardar los datos en JSON"); }
    }

    public List<T> deserializar(){
        List<T> datos = null;
        try{
            CollectionType tipoLista = mapper.getTypeFactory()
                    .constructCollectionType(List.class, tipoClase);
            datos = mapper.readValue(new File(rutaArchivo), tipoLista);

        } catch (JsonProcessingException error){ System.out.println("Error al procesar el JSON"); }
        catch (IOException error){ System.out.println("Error al leer el archivo"); }

        return datos != null ? datos : new ArrayList<>();
    }
}
