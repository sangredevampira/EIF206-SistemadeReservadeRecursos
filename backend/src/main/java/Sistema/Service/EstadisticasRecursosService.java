package Sistema.Service;

import Sistema.Logica.EstadisticasRecursosLogica;

import java.time.LocalDate;

public class EstadisticasRecursosService {

    private final EstadisticasRecursosLogica estadisticasRecursosLogica;

    public EstadisticasRecursosService() {
        this(new EstadisticasRecursosLogica());
    }

    // permite inyectar la lógica (lo usan las pruebas)
    public EstadisticasRecursosService(EstadisticasRecursosLogica logica) {
        this.estadisticasRecursosLogica = logica;
    }

    public EstadisticasRecursosLogica.ResultadoEstadisticas calcular(LocalDate fechaDesde, LocalDate fechaHasta) {
        return estadisticasRecursosLogica.calcular(fechaDesde, fechaHasta);
    }
}
