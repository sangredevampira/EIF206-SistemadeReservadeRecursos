package Sistema.Red;

import Sistema.DTO.Funcionario;
import Sistema.DTO.ReservaDTO;
import Sistema.DTO.Rol;
import Sistema.Service.ReservaService;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

// Operaciones de reservas: RES_CREAR, RES_CANCELAR, RES_BUSCAR_ID, RES_BUSCAR_FUNCIONARIO y RES_BUSCAR_PERIODO.
// Solo traduce mensaje <-> ReservaService y revisa quien pide cada cosa; las reglas de negocio son de la Logica.
public class ManejadorReservas {

    private static final String DATOS_INVALIDOS = "Los datos de la solicitud no son válidos.";

    private final ReservaService reservaService;
    private final GestorClientes gestor;

    public ManejadorReservas(ReservaService reservaService, GestorClientes gestor) {
        this.reservaService = reservaService;
        this.gestor = gestor;
    }

    // Registra sus cinco operaciones en el despachador
    public void registrarEn(DespachadorPeticiones despachador) {
        despachador.registrar(Operaciones.RES_CREAR, this::crear);
        despachador.registrar(Operaciones.RES_CANCELAR, this::cancelar);
        despachador.registrar(Operaciones.RES_BUSCAR_ID, this::buscarPorId);
        despachador.registrar(Operaciones.RES_BUSCAR_FUNCIONARIO, this::buscarPorFuncionario);
        despachador.registrar(Operaciones.RES_BUSCAR_PERIODO, this::buscarPorPeriodo);
    }

    private Object crear(JsonNode datos, ContextoSesion contexto) {
        // el funcionario sale de la SESION, no de lo que mande el cliente: nadie reserva a nombre de otro
        String idFuncionario = contexto.getIdUsuario();
        Funcionario funcionario = contexto.getFuncionario();
        String nombreFuncionario = (funcionario != null) ? funcionario.getNombre() : idFuncionario;

        // si algo falla (datos o disponibilidad) lanza IllegalArgumentException y no se difunde nada
        ReservaDTO creada = reservaService.registrarReserva(idFuncionario, nombreFuncionario,
                texto(datos, "nombreActividad"),
                fecha(datos, "fecha"),
                hora(datos, "horaInicio"),
                hora(datos, "horaFin"),
                lista(datos, "idsCategorias"));

        gestor.difundirReserva(creada, contexto);   // ya esta guardada: avisa a los demas clientes
        return creada;
    }

    private Object cancelar(JsonNode datos, ContextoSesion contexto) {
        String idReserva = texto(datos, "idReserva");
        ReservaDTO reserva = reservaService.buscarReservaID(idReserva);   // si no existe, lanza con su mensaje
        exigirDuenoOAdministrador(reserva.getIdFuncionario(), contexto);
        return reservaService.cancelarReserva(idReserva);
    }

    private Object buscarPorId(JsonNode datos, ContextoSesion contexto) {
        ReservaDTO reserva = reservaService.buscarReservaID(texto(datos, "idReserva"));
        exigirDuenoOAdministrador(reserva.getIdFuncionario(), contexto);
        return reserva;
    }

    private Object buscarPorFuncionario(JsonNode datos, ContextoSesion contexto) {
        String idFuncionario = texto(datos, "idFuncionario");
        exigirDuenoOAdministrador(idFuncionario, contexto);
        return reservaService.buscarPorFuncionario(idFuncionario);
    }

    // para calendario, actividades y estadisticas: cualquier sesion iniciada (lo exige el despachador)
    private Object buscarPorPeriodo(JsonNode datos, ContextoSesion contexto) {
        return reservaService.buscarPorPeriodo(fecha(datos, "fechaDesde"), fecha(datos, "fechaHasta"));
    }

    // un funcionario solo toca sus propias reservas; el administrador, cualquiera
    private void exigirDuenoOAdministrador(String idDueno, ContextoSesion contexto) {
        boolean esAdministrador = contexto.getRol() == Rol.ADMINISTRADOR;
        if (!esAdministrador && (idDueno == null || !idDueno.equals(contexto.getIdUsuario())))
            throw new IllegalArgumentException("Solo puede acceder a sus propias reservas.");
    }

    // --- lectura de "datos": si falta algo o no se puede leer, la solicitud no es valida ---

    private String texto(JsonNode datos, String campo) {
        JsonNode valor = (datos == null) ? null : datos.get(campo);
        if (valor == null || !valor.isTextual())
            throw new IllegalArgumentException(DATOS_INVALIDOS);
        return valor.asText();
    }

    private LocalDate fecha(JsonNode datos, String campo) {
        try {
            return LocalDate.parse(texto(datos, campo));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(DATOS_INVALIDOS);
        }
    }

    private LocalTime hora(JsonNode datos, String campo) {
        try {
            return LocalTime.parse(texto(datos, campo));   // acepta "08:00" y "08:00:00"
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(DATOS_INVALIDOS);
        }
    }

    private List<String> lista(JsonNode datos, String campo) {
        JsonNode valor = (datos == null) ? null : datos.get(campo);
        if (valor == null || !valor.isArray())
            throw new IllegalArgumentException(DATOS_INVALIDOS);
        List<String> elementos = new ArrayList<>();
        for (JsonNode elemento : valor) {
            if (!elemento.isTextual())
                throw new IllegalArgumentException(DATOS_INVALIDOS);
            elementos.add(elemento.asText());
        }
        return elementos;
    }
}