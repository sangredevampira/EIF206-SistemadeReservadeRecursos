package Sistema.Logica;

import Sistema.DTO.CategoriaDTO;
import Sistema.DTO.RecursoDTO;
import Sistema.Datos.CategoriaDatos;
import Sistema.Datos.RecursoDatos;

import java.util.List;

public class CategoriaLogica {

    private final CategoriaDatos categoriaDatos = new CategoriaDatos();

    // Dependencia directa a RecursoDatos (no a RecursoLogica ni
    // RecursoService): es el mismo patron que ya usa FuncionarioLogica
    // para revisar UsuarioDatos al eliminar. Solo se necesita LEER
    // (filtrarPorCategoria), no hace falta pasar por las otras capas de
    // Recursos para esa consulta puntual.
    private final RecursoDatos recursoDatos = new RecursoDatos("src/data/recursos.json");

    public ResultadoOperacion registrar(CategoriaDTO categoria) {
        String descripcion = categoria.getDescripcion();

        if (descripcion == null || descripcion.isBlank()) {
            return ResultadoOperacion.fallo("La descripcion no puede estar vacia.");
        }
        if (existeDescripcion(descripcion, null)) {
            return ResultadoOperacion.fallo("Ya existe una categoria con esa descripcion.");
        }

        categoria.setId(categoriaDatos.generarSiguienteId());
        categoriaDatos.guardar(categoria);
        return ResultadoOperacion.exito();
    }

    public ResultadoOperacion modificar(CategoriaDTO categoria) {
        if (categoriaDatos.buscarPorId(categoria.getId()) == null) {
            return ResultadoOperacion.fallo("La categoria no existe.");
        }

        String descripcion = categoria.getDescripcion();
        if (descripcion == null || descripcion.isBlank()) {
            return ResultadoOperacion.fallo("La descripcion no puede estar vacia.");
        }
        if (existeDescripcion(descripcion, categoria.getId())) {
            return ResultadoOperacion.fallo("Ya existe otra categoria con esa descripcion.");
        }

        categoriaDatos.modificar(categoria);
        return ResultadoOperacion.exito();
    }

    /**
     * Regla del enunciado (funcionalidad 4) y del plan detallado (2.6):
     * "Impedir la eliminacion de una categoria que contenga recursos."
     * Se apoya en el metodo que ya existia en RecursoDatos
     * (filtrarPorCategoria), sin tocar nada del modulo de Kris.
     */
    public ResultadoOperacion eliminar(String id) {
        if (categoriaDatos.buscarPorId(id) == null) {
            return ResultadoOperacion.fallo("La categoria no existe.");
        }

        List<RecursoDTO> recursosDeEstaCategoria = recursoDatos.filtrarRecursos(id);
        if (!recursosDeEstaCategoria.isEmpty()) {
            return ResultadoOperacion.fallo(
                    "No se puede eliminar la categoria porque tiene recursos asociados.");
        }

        categoriaDatos.eliminar(id);
        return ResultadoOperacion.exito();
    }

    public List<CategoriaDTO> buscar(String descripcionParcial) {
        return categoriaDatos.buscar(descripcionParcial);
    }

    public List<CategoriaDTO> listarTodos() {
        return categoriaDatos.listarTodos();
    }

    private boolean existeDescripcion(String descripcion, String idAExcluir) {
        for (CategoriaDTO c : categoriaDatos.listarTodos()) {
            boolean mismaDescripcion = c.getDescripcion().equalsIgnoreCase(descripcion.trim());
            boolean esOtraCategoria = (idAExcluir == null) || !c.getId().equals(idAExcluir);
            if (mismaDescripcion && esOtraCategoria) {
                return true;
            }
        }
        return false;
    }

    // Mismo patron Resultado* que UsuarioLogica.ResultadoLogin y
    // FuncionarioLogica.ResultadoOperacion: Logica nunca lanza excepciones
    // propias para reglas de negocio, siempre devuelve este objeto.
    public static class ResultadoOperacion {
        private final boolean exitoso;
        private final String mensajeError;

        private ResultadoOperacion(boolean exitoso, String mensajeError) {
            this.exitoso = exitoso;
            this.mensajeError = mensajeError;
        }

        public static ResultadoOperacion exito() {
            return new ResultadoOperacion(true, null);
        }

        public static ResultadoOperacion fallo(String mensajeError) {
            return new ResultadoOperacion(false, mensajeError);
        }

        public boolean isExitoso() { return exitoso; }
        public String getMensajeError() { return mensajeError; }
    }
}
