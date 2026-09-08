package Sistema.Service;

import Sistema.Logica.EstadisticasRecursosLogica;

import java.time.LocalDate;

public class EstadisticasRecursosService {

    private final EstadisticasRecursosLogica estadisticasRecursosLogica = new EstadisticasRecursosLogica();

    public EstadisticasRecursosLogica.ResultadoEstadisticas calcular(LocalDate fechaDesde, LocalDate fechaHasta) {
        return estadisticasRecursosLogica.calcular(fechaDesde, fechaHasta);
    }
}
