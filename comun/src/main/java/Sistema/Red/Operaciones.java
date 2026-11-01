package Sistema.Red;

// Nombres de operación compartidos por proxies y servidor (docs/00_contratos.md, sección 3).
public final class Operaciones {

    private Operaciones() {}

    // Sesión
    public static final String AUTH_LOGIN = "AUTH_LOGIN";
    public static final String AUTH_CAMBIAR_CLAVE = "AUTH_CAMBIAR_CLAVE";
    public static final String AUTH_LOGOUT = "AUTH_LOGOUT";

    // Funcionarios
    public static final String FUNC_CREAR = "FUNC_CREAR";
    public static final String FUNC_EDITAR = "FUNC_EDITAR";
    public static final String FUNC_BAJA = "FUNC_BAJA";
    public static final String FUNC_BUSCAR = "FUNC_BUSCAR";
    public static final String FUNC_LISTAR = "FUNC_LISTAR";

    // Categorías
    public static final String CAT_CREAR = "CAT_CREAR";
    public static final String CAT_EDITAR = "CAT_EDITAR";
    public static final String CAT_ELIMINAR = "CAT_ELIMINAR";
    public static final String CAT_BUSCAR = "CAT_BUSCAR";
    public static final String CAT_LISTAR = "CAT_LISTAR";

    // Recursos
    public static final String REC_CREAR = "REC_CREAR";
    public static final String REC_EDITAR = "REC_EDITAR";
    public static final String REC_ELIMINAR = "REC_ELIMINAR";
    public static final String REC_BUSCAR = "REC_BUSCAR";
    public static final String REC_LISTAR = "REC_LISTAR";
    public static final String REC_NUEVO_ID = "REC_NUEVO_ID";

    // Reservas
    public static final String RES_CREAR = "RES_CREAR";
    public static final String RES_CANCELAR = "RES_CANCELAR";
    public static final String RES_BUSCAR_ID = "RES_BUSCAR_ID";
    public static final String RES_BUSCAR_FUNCIONARIO = "RES_BUSCAR_FUNCIONARIO";
    public static final String RES_BUSCAR_PERIODO = "RES_BUSCAR_PERIODO";

    // Consultas
    public static final String CAL_CONSULTAR = "CAL_CONSULTAR";
    public static final String ACT_CONSULTAR = "ACT_CONSULTAR";
    public static final String EST_RECURSOS = "EST_RECURSOS";
    public static final String EST_ACTIVIDADES = "EST_ACTIVIDADES";

    // Evento (solo sale del Backend)
    public static final String RESERVA_CREADA = "RESERVA_CREADA";
}
