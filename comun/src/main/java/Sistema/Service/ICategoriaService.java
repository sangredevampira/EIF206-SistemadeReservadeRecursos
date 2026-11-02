package Sistema.Service;

import Sistema.DTO.CategoriaDTO;
import Sistema.DTO.ResultadoOperacionDTO;

import java.util.List;

// contrato de categorias. Lo implementan el CategoriaService del Backend y,
// en el Frontend, el proxy que envia CAT_CREAR, CAT_EDITAR, CAT_ELIMINAR,
// CAT_BUSCAR y CAT_LISTAR. listarTodos tambien lo usan las pantallas de
// funcionario (Reservas y Calendarizacion).
public interface ICategoriaService {

    // el ID lo genera el Backend
    ResultadoOperacionDTO registrar(CategoriaDTO categoria);

    ResultadoOperacionDTO modificar(CategoriaDTO categoria);

    ResultadoOperacionDTO eliminar(String id);

    // "contiene", sin distinguir mayusculas; null o vacio = todas
    List<CategoriaDTO> buscar(String descripcionParcial);

    List<CategoriaDTO> listarTodos();
}
