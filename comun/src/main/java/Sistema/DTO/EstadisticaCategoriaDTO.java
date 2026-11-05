package Sistema.DTO;

// Fila de la tabla de Estadisticas de recursos: una categoria y cuantas
// veces se reservo algun recurso de esa categoria en el periodo consultado.
// No se persiste en ningun archivo -- solo transporta datos ya calculados
// entre Logica/Service y la vista (tabla + grafico).

public class EstadisticaCategoriaDTO {

    private String categoria;
    private int cantidad;

    public EstadisticaCategoriaDTO() {
    }

    public EstadisticaCategoriaDTO(String categoria, int cantidad) {
        this.categoria = categoria;
        this.cantidad = cantidad;
    }

    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }

    public int getCantidad() { return cantidad; }
    public void setCantidad(int cantidad) { this.cantidad = cantidad; }
}
