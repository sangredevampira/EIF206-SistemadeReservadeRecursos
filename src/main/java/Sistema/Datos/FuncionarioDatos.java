package Sistema.Datos;

import Sistema.DTO.Funcionario;

import java.util.ArrayList;
import java.util.List;

// unica clase autorizada para tocar funcionarios.json (base de datos).
// lee, busca (por id o nombre), guarda, modifica y borra. No valida
// reglas de negocio -- eso es trabajo de FuncionarioLogica.
// Hereda serializar/deserializar/crearArchivoSiNoExiste de Datos<T>.

public class FuncionarioDatos extends Datos<Funcionario> {

    private static final String RUTA_ARCHIVO = "src/main/resources/Sistema/data/funcionarios.json";

    public FuncionarioDatos() {
        super(RUTA_ARCHIVO, Funcionario.class);
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
}