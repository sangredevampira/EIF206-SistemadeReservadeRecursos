package Sistema.Service;

import Sistema.DTO.ProgramacionActividadesDTO;

import java.time.LocalDate;

// contrato de la consulta de programacion de actividades. Lo implementan el ProgramacionActividadesService
// del Backend y, en el Frontend, el proxy que envia ACT_CONSULTAR.
public interface IProgramacionActividadesService {

    ProgramacionActividadesDTO consultar(LocalDate fechaDesde, LocalDate fechaHasta);
}
