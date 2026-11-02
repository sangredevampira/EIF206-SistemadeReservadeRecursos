package Sistema.Service;

import Sistema.DTO.ReservaDTO;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Lo que la pantalla de reservas (y calendario, actividades...) necesita del servicio de reservas.
 * Lo cumplen dos clases: ReservaService (backend, con la logica real) y ProxyReservaService (frontend,
 * que manda el mismo pedido por la red). Los controladores usan solo esta interfaz.
 *
 * Un fallo de regla de negocio siempre llega como IllegalArgumentException con un mensaje para el usuario.
 */
public interface IReservaService {

    // idFuncionario y nombreFuncionario se conservan por compatibilidad con el Proyecto 1;
    // en el Backend el funcionario sale de la sesion, no de estos parametros.
    ReservaDTO registrarReserva(String idFuncionario, String nombreFuncionario, String nombreActividad,
                                LocalDate fecha, LocalTime horaInicio, LocalTime horaFin,
                                List<String> idsCategoriasSolicitadas);

    boolean cancelarReserva(String idReserva);

    ReservaDTO buscarReservaID(String id);

    List<ReservaDTO> buscarPorFuncionario(String idFuncionario);

    List<ReservaDTO> buscarPorPeriodo(LocalDate fechaDesde, LocalDate fechaHasta);
}
