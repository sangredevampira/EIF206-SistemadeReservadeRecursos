package Sistema.Service;

import Sistema.DTO.ProgramacionActividadesDTO;
import Sistema.DTO.ReservaDTO;
import Sistema.Logica.ProgramacionActividadesLogica;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public class ProgramacionActividadesService implements IProgramacionActividadesService {

    private final ProgramacionActividadesLogica programacionLogica;

    public ProgramacionActividadesService() {
        this(new ProgramacionActividadesLogica());
    }

    // permite inyectar la lógica (lo usan las pruebas)
    public ProgramacionActividadesService(ProgramacionActividadesLogica logica) {
        this.programacionLogica = logica;
    }

    @Override
    public ProgramacionActividadesDTO consultar(LocalDate fechaDesde, LocalDate fechaHasta) {
        return programacionLogica.consultar(fechaDesde, fechaHasta);
    }

    public List<String> generarHorasDelDia() {
        return programacionLogica.generarHorasDelDia();
    }

    public Map<LocalDate, Map<String, String>> construirProgramacion(List<ReservaDTO> reservaciones) {
        return programacionLogica.construirProgramacion(reservaciones);
    }
}
