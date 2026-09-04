package Sistema.Datos;

import Sistema.DTO.Funcionario;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

// unica clase autorizada para tocar funcionarios.json (base de datos).
// lee, busca (por id o nombre), guarda, modifica y borra. No valida
// reglas de negocio -- eso es trabajo de FuncionarioLogica.

public class FuncionarioDatos {

    private static final String RUTA_ARCHIVO = "src/main/resources/Sistema/data/funcionarios.json";
    private final ObjectMapper mapper = new ObjectMapper();

    public FuncionarioDatos() {
        crearArchivoSiNoExiste();
    }

    private void crearArchivoSiNoExiste() {
        File archivo = new File(RUTA_ARCHIVO);
        if (archivo.exists()) {
            return;
        }
        try {
            archivo.getParentFile().mkdirs();
            serializar(new ArrayList<>());
        } catch (Exception e) {
            throw new RuntimeException("No se pudo crear funcionarios.json: " + e.getMessage(), e);
        }
    }

    public Funcionario buscarPorId(String id) {
        List<Funcionario> funcionarios = deserializar();
        for (Funcionario funcionario : funcionarios) {
            if (funcionario.getId().equals(id)) {
                return funcionario;
            }
        }
        return null;
    }

    // busqueda parcial e insensible a mayusculas, como pide el enunciado
    // ("busqueda de funcionarios por id o nombre")
    public List<Funcionario> buscar(String idParcial, String nombreParcial) {
        List<Funcionario> funcionarios = deserializar();
        List<Funcionario> resultado = new ArrayList<>();

        for (Funcionario funcionario : funcionarios) {
            boolean coincideId = idParcial == null || idParcial.isBlank()
                    || funcionario.getId().toLowerCase().contains(idParcial.toLowerCase());
            boolean coincideNombre = nombreParcial == null || nombreParcial.isBlank()
                    || funcionario.getNombre().toLowerCase().contains(nombreParcial.toLowerCase());

            if (coincideId && coincideNombre) {
                resultado.add(funcionario);
            }
        }
        return resultado;
    }

    public List<Funcionario> listarTodos() {
        return deserializar();
    }

    public void guardar(Funcionario funcionarioNuevo) {
        List<Funcionario> funcionarios = deserializar();
        funcionarios.add(funcionarioNuevo);
        serializar(funcionarios);
    }

    public void modificar(Funcionario funcionarioModificado) {
        List<Funcionario> funcionarios = deserializar();
        for (int i = 0; i < funcionarios.size(); i++) {
            if (funcionarios.get(i).getId().equals(funcionarioModificado.getId())) {
                funcionarios.set(i, funcionarioModificado);
                break;
            }
        }
        serializar(funcionarios);
    }

    public void eliminar(String id) {
        List<Funcionario> funcionarios = deserializar();
        funcionarios.removeIf(funcionario -> funcionario.getId().equals(id));
        serializar(funcionarios);
    }

    private void serializar(List<Funcionario> funcionarios) {
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(new File(RUTA_ARCHIVO), funcionarios);
        } catch (IOException e) {
            throw new RuntimeException("No se pudo guardar funcionarios.json: " + e.getMessage(), e);
        }
    }

    private List<Funcionario> deserializar() {
        try {
            return mapper.readValue(new File(RUTA_ARCHIVO), new TypeReference<List<Funcionario>>() {});
        } catch (IOException e) {
            throw new RuntimeException("No se pudo leer funcionarios.json: " + e.getMessage(), e);
        }
    }
}