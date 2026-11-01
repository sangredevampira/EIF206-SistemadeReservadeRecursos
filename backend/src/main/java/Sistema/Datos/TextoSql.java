package Sistema.Datos;

/**
 * Apoyo para armar valores de SQL. De paquete: solo la usan las clases de
 * Datos (RecursoDatos y CategoriaDatos; FuncionarioDatos en el paso 5c).
 */
final class TextoSql {

    private TextoSql() {}

    // Con ESCAPE '!' los comodines del usuario se buscan como texto: "50%"
    // no encuentra "500". Primero se escapa el propio '!'. Se usa '!' y no
    // '\' porque el significado de '\' depende del sql_mode (NO_BACKSLASH_ESCAPES).
    // Uso: "... LIKE ? ESCAPE '!'" con el valor "%" + escaparLike(texto) + "%".
    static String escaparLike(String texto) {
        return texto.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
