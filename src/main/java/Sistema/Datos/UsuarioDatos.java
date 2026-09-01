package Sistema.Datos;

import Sistema.DTO.Rol;
import Sistema.DTO.Usuario;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

// unica clase autorizada para tocar usuarios.json (base de datos),
// lee, busca, guarda y actualiza la clave. Usa Jackson, mismo patron
// que RecursoDatos, para mantener una sola libreria JSON en el equipo.

public class UsuarioDatos {

    private static final String RUTA_ARCHIVO = "src/main/resources/Sistema/data/usuarios.json";
    private final ObjectMapper mapper = new ObjectMapper();

    public UsuarioDatos() {
        crearArchivoSiNoExiste();
    }

    private void crearArchivoSiNoExiste() {
        File archivo = new File(RUTA_ARCHIVO);
        if (archivo.exists()) {
            return;
        }
        try {
            archivo.getParentFile().mkdirs();

            List<Usuario> usuarios = new ArrayList<>();
            usuarios.add(new Usuario("admin", "admin", Rol.ADMINISTRADOR));

            serializar(usuarios);
        } catch (Exception e) {
            throw new RuntimeException("No se pudo crear usuarios.json: " + e.getMessage(), e);
        }
    }

    public Usuario buscarPorId(String id) {
        List<Usuario> usuarios = deserializar();
        for (Usuario usuario : usuarios) {
            if (usuario.getId().equals(id)) {
                return usuario;
            }
        }
        return null;
    }

    public void guardar(Usuario usuarioNuevo) {
        List<Usuario> usuarios = deserializar();
        usuarios.add(usuarioNuevo);
        serializar(usuarios);
    }

    public void actualizarClave(String id, String nuevaClave) {
        List<Usuario> usuarios = deserializar();
        for (Usuario usuario : usuarios) {
            if (usuario.getId().equals(id)) {
                usuario.setClave(nuevaClave);
                break;
            }
        }
        serializar(usuarios);
    }

    private void serializar(List<Usuario> usuarios) {
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(new File(RUTA_ARCHIVO), usuarios);
        } catch (IOException e) {
            throw new RuntimeException("No se pudo guardar usuarios.json: " + e.getMessage(), e);
        }
    }

    private List<Usuario> deserializar() {
        try {
            return mapper.readValue(new File(RUTA_ARCHIVO), new TypeReference<List<Usuario>>() {});
        } catch (IOException e) {
            throw new RuntimeException("No se pudo leer usuarios.json: " + e.getMessage(), e);
        }
    }
}