package Sistema.Service;

import Sistema.DTO.CategoriaDTO;
import Sistema.Logica.CategoriaLogica;

import java.util.List;

// Puro passthrough hacia CategoriaLogica -- "en esta capa NO se realizan
// validaciones" (cita textual del diagrama del profesor).
public class CategoriaService {

    private final CategoriaLogica categoriaLogica;

    public CategoriaService() { this(new CategoriaLogica()); }

    // para pruebas: permite pasar una CategoriaLogica con datos falsos
    public CategoriaService(CategoriaLogica categoriaLogica) { this.categoriaLogica = categoriaLogica; }

    public CategoriaLogica.ResultadoOperacion registrar(CategoriaDTO categoria) {
        return categoriaLogica.registrar(categoria);
    }

    public CategoriaLogica.ResultadoOperacion modificar(CategoriaDTO categoria) {
        return categoriaLogica.modificar(categoria);
    }

    public CategoriaLogica.ResultadoOperacion eliminar(String id) {
        return categoriaLogica.eliminar(id);
    }

    public List<CategoriaDTO> buscar(String descripcionParcial) {
        return categoriaLogica.buscar(descripcionParcial);
    }

    public List<CategoriaDTO> listarTodos() {
        return categoriaLogica.listarTodos();
    }
}
