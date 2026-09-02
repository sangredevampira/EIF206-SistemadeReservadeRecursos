package Sistema.DTO;

import Sistema.DTO.CategoriaDTO;

public class RecursoDTO {
    private String id;
    private String descripcion;
    private CategoriaDTO categoria;

    public RecursoDTO(){}

    public RecursoDTO(String id, String descripcion, CategoriaDTO categoria){
        this.id = id;
        this.descripcion = descripcion;
        this.categoria = categoria;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public CategoriaDTO getCategoria() { return categoria; }
    public void setCategoria(CategoriaDTO categoria) { this.categoria = categoria; }
}
