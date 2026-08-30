package Sistema.Datos;

import Sistema.DTO.Rol;
import Sistema.DTO.Usuario;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;


//unica clase autorizada para tocar usuarios.json(base de datos), lee, busca, guarda y actualiza la clave

public class UsuarioDatos {

    private static final String RUTA_ARCHIVO = "src/main/resources/Sistema/data/usuarios.json";

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

            JSONObject raiz = new JSONObject();
            raiz.put("usuarios", new JSONArray());

            Usuario admin = new Usuario("admin", "admin", Rol.ADMINISTRADOR);
            agregarUsuarioAlArreglo(raiz, admin);

            guardarDocumento(raiz);
        } catch (Exception e) {
            throw new RuntimeException("No se pudo crear usuarios.json: " + e.getMessage(), e);
        }
    }

    public Usuario buscarPorId(String id) {
        JSONObject raiz = cargarDocumento();
        JSONArray usuarios = raiz.getJSONArray("usuarios");

        for (int i = 0; i < usuarios.length(); i++) {
            JSONObject nodo = usuarios.getJSONObject(i);
            if (nodo.getString("id").equals(id)) {
                return convertirAUsuario(nodo);
            }
        }
        return null;
    }

    public void guardar(Usuario usuario) {
        JSONObject raiz = cargarDocumento();
        agregarUsuarioAlArreglo(raiz, usuario);
        guardarDocumento(raiz);
    }

    public void actualizarClave(String id, String nuevaClave) {
        JSONObject raiz = cargarDocumento();
        JSONArray usuarios = raiz.getJSONArray("usuarios");

        for (int i = 0; i < usuarios.length(); i++) {
            JSONObject nodo = usuarios.getJSONObject(i);
            if (nodo.getString("id").equals(id)) {
                nodo.put("clave", nuevaClave);
                break;
            }
        }
        guardarDocumento(raiz);
    }

    private void agregarUsuarioAlArreglo(JSONObject raiz, Usuario usuario) {
        JSONObject nodo = new JSONObject();
        nodo.put("id", usuario.getId());
        nodo.put("clave", usuario.getClave());
        nodo.put("rol", usuario.getRol().name());
        raiz.getJSONArray("usuarios").put(nodo);
    }

    private Usuario convertirAUsuario(JSONObject nodo) {
        String id = nodo.getString("id");
        String clave = nodo.getString("clave");
        String rolTexto = nodo.getString("rol");
        return new Usuario(id, clave, Rol.valueOf(rolTexto));
    }

    private JSONObject cargarDocumento() {
        try {
            String contenido = Files.readString(new File(RUTA_ARCHIVO).toPath());
            return new JSONObject(contenido);
        } catch (IOException e) {
            throw new RuntimeException("No se pudo leer usuarios.json: " + e.getMessage(), e);
        }
    }

    private void guardarDocumento(JSONObject raiz) {
        try (FileWriter escritor = new FileWriter(RUTA_ARCHIVO)) {
            escritor.write(raiz.toString(4));
        } catch (IOException e) {
            throw new RuntimeException("No se pudo guardar usuarios.json: " + e.getMessage(), e);
        }
    }
}