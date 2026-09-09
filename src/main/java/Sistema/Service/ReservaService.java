package Sistema.Service;

import Sistema.DTO.ReservaDTO;
import Sistema.DTO.Funcionario;
import Sistema.Logica.ReservaLogica;

import java.util.List;
import java.time.LocalDate;
import java.time.LocalTime;

public class ReservaService {

    private ReservaLogica reservaLogica = new ReservaLogica();

    public ReservaService(){}
    public ReservaService(ReservaLogica reservaLogica) { this.reservaLogica = reservaLogica; }

    public int registrarReserva(String idFuncionario, String nombreFuncionario, String nombreActividad,
                                   LocalDate fecha, LocalTime horaInicio, LocalTime horaFin, List<String> idsCategoriasSolicitadas){
        return reservaLogica.registrarReserva(idFuncionario, nombreFuncionario, nombreActividad,
                fecha, horaInicio, horaFin, idsCategoriasSolicitadas);
    }

    public boolean cancelarReserva(String idReserva) { return reservaLogica.cancelarReserva(idReserva); }

    public ReservaDTO buscarReservaID(String id) {  return reservaLogica.buscarReservaID(id); }

    public List<ReservaDTO> buscarPorFuncionario(String idFuncionario) { return reservaLogica.buscarPorFuncionario(idFuncionario); }

    public List<ReservaDTO> buscarPorPeriodo(LocalDate fechaDesde, LocalDate fechaHasta) { return reservaLogica.buscarPorPeriodo(fechaDesde, fechaHasta); }
}
