package Sistema.Datos;

import Sistema.DTO.Rol;
import Sistema.DTO.Usuario;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

// unica clase autorizada para tocar usuarios.json (base de datos),
// lee, busca, guarda y actualiza la clave. Hereda serializar/deserializar
// de Datos<T>; sobreescribe crearArchivoSiNoExiste() porque este archivo
// no debe nacer vacio -- necesita el usuario admin inicial.

public class UsuarioDatos extends Datos<Usuario> {

    private static final String RUTA_ARCHIVO = "src/main/resources/Sistema/data/usuarios.json";

    public UsuarioDatos() {
        super(RUTA_ARCHIVO, Usuario.class);
    }

    @Override
    protected void crearArchivoSiNoExiste() {
        File archivo = new File(RUTA_ARCHIVO);
        if (archivo.exists()) {
            return;
        }
        try {
            if (archivo.getParentFile() != null) {
                archivo.getParentFile().mkdirs();
            }
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

    public void eliminar(String id) {
        List<Usuario> usuarios = deserializar();
        usuarios.removeIf(usuario -> usuario.getId().equals(id));
        serializar(usuarios);
    }
}