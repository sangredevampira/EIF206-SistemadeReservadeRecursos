package Sistema.Datos;

import Sistema.DTO.CategoriaDTO;

import java.util.ArrayList;
import java.util.List;

// unica clase autorizada para tocar categorias.json (base de datos).
// lee, busca (por descripcion), guarda, modifica y borra. No valida
// reglas de negocio -- eso es trabajo de CategoriaLogica.
// Hereda serializar/deserializar/crearArchivoSiNoExiste de Datos<T>,
// igual que FuncionarioDatos y UsuarioDatos.

public class CategoriaDatos extends Datos<CategoriaDTO> {

    private static final String RUTA_ARCHIVO = "src/data/categorias.json";

    public CategoriaDatos() {
        super(RUTA_ARCHIVO, CategoriaDTO.class);
    }

    public CategoriaDTO buscarPorId(String id) {
        for (CategoriaDTO categoria : deserializar()) {
            if (categoria.getId().equals(id)) {
                return categoria;
            }
        }
        return null;
    }

    // busqueda parcial e insensible a mayusculas, como pide el enunciado
    // ("busqueda de categorias por descripcion")
    public List<CategoriaDTO> buscar(String descripcionParcial) {
        List<CategoriaDTO> resultado = new ArrayList<>();
        boolean sinFiltro = descripcionParcial == null || descripcionParcial.isBlank();

        for (CategoriaDTO categoria : deserializar()) {
            boolean coincide = sinFiltro
                    || categoria.getDescripcion().toLowerCase().contains(descripcionParcial.toLowerCase());
            if (coincide) {
                resultado.add(categoria);
            }
        }
        return resultado;
    }

    public List<CategoriaDTO> listarTodos() {
        return deserializar();
    }

    public void guardar(CategoriaDTO categoriaNueva) {
        List<CategoriaDTO> categorias = deserializar();
        categorias.add(categoriaNueva);
        serializar(categorias);
    }

    public void modificar(CategoriaDTO categoriaModificada) {
        List<CategoriaDTO> categorias = deserializar();
        for (int i = 0; i < categorias.size(); i++) {
            if (categorias.get(i).getId().equals(categoriaModificada.getId())) {
                categorias.set(i, categoriaModificada);
                break;
            }
        }
        serializar(categorias);
    }

    public void eliminar(String id) {
        List<CategoriaDTO> categorias = deserializar();
        categorias.removeIf(categoria -> categoria.getId().equals(id));
        serializar(categorias);
    }

    // Genera el siguiente ID tipo CAT-000001, CAT-000002... (ver enunciado,
    // funcionalidad 4: "su id (autogenerado)"), buscando el numero mas alto
    // ya usado en vez de contar cuantas categorias hay -- asi no se repite
    // un ID si alguna categoria intermedia fue borrada.
    public String generarSiguienteId() {
        int maximo = 0;
        for (CategoriaDTO categoria : deserializar()) {
            try {
                int numero = Integer.parseInt(categoria.getId().replace("CAT-", ""));
                maximo = Math.max(maximo, numero);
            } catch (NumberFormatException ignorado) {
                // Si algun ID no sigue el formato esperado, simplemente no
                // cuenta para el calculo del siguiente numero.
            }
        }
        return String.format("CAT-%06d", maximo + 1);
    }
}
