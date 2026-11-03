package Sistema.Service.Red;

import Sistema.DTO.ProgramacionActividadesDTO;
import Sistema.Red.Operaciones;
import Sistema.Red.ProxyBase;
import Sistema.Service.IProgramacionActividadesService;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * Representa en el Frontend al ProgramacionActividadesService del Backend: envía ACT_CONSULTAR y devuelve
 * la programación ya armada por el servidor.
 */
public class ProxyProgramacionActividadesService extends ProxyBase implements IProgramacionActividadesService {

    @Override
    public ProgramacionActividadesDTO consultar(LocalDate fechaDesde, LocalDate fechaHasta) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("fechaDesde", fechaDesde == null ? null : fechaDesde.toString());
        datos.put("fechaHasta", fechaHasta == null ? null : fechaHasta.toString());
        ProgramacionActividadesDTO resultado =
                ejecutar(Operaciones.ACT_CONSULTAR, datos, ProgramacionActividadesDTO.class);
        if (resultado == null) {
            throw new IllegalStateException("El servidor no devolvió la programación de actividades.");
        }
        return resultado;
    }
}
