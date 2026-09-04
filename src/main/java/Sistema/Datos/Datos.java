package Sistema.Datos;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

// Clase padre generica para todas las clases Datos del proyecto. Elimina
// la duplicacion de serializar/deserializar que tenia cada X-Datos por
// separado (idea original de Kris). Dos correcciones sobre esa version:
//
//  1. Lanza RuntimeException real en vez de solo hacer System.out.println
//     -- necesario para que ui/ pueda atraparla con un Alert y cumplir la
//     regla del enunciado de "reportar adecuadamente cualquier error".
//  2. crearArchivoSiNoExiste() vive aca (generico, sirve para cualquier T)
//     en vez de repetirse en cada subclase.
public class Datos<T> {

    private String rutaArchivo;
    private Class<T> tipoClase;
    protected ObjectMapper mapper = new ObjectMapper();

    public Datos() {
    }

    public Datos(String rutaArchivo, Class<T> tipoClase) {
        this.rutaArchivo = rutaArchivo;
        this.tipoClase = tipoClase;
        crearArchivoSiNoExiste();
    }

    public String getRutaArchivo() { return rutaArchivo; }
    public void setRutaArchivo(String rutaArchivo) { this.rutaArchivo = rutaArchivo; }

    // Si el archivo no existe todavia, lo crea con una lista vacia para
    // que el primer deserializar() no falle. Subclases con una necesidad
    // distinta (ej. UsuarioDatos, que siembra un admin inicial) pueden
    // sobreescribir este metodo.
    protected void crearArchivoSiNoExiste() {
        File archivo = new File(rutaArchivo);
        if (archivo.exists()) {
            return;
        }
        try {
            if (archivo.getParentFile() != null) {
                archivo.getParentFile().mkdirs();
            }
            serializar(new ArrayList<>());
        } catch (Exception e) {
            throw new RuntimeException("No se pudo crear " + rutaArchivo + ": " + e.getMessage(), e);
        }
    }

    public void serializar(List<T> datos) {
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(new File(rutaArchivo), datos);
        } catch (IOException error) {
            throw new RuntimeException("No se pudo guardar " + rutaArchivo + ": " + error.getMessage(), error);
        }
    }

    public List<T> deserializar() {
        try {
            CollectionType tipoLista = mapper.getTypeFactory()
                    .constructCollectionType(List.class, tipoClase);
            return mapper.readValue(new File(rutaArchivo), tipoLista);
        } catch (IOException error) {
            throw new RuntimeException("No se pudo leer " + rutaArchivo + ": " + error.getMessage(), error);
        }
    }
}
