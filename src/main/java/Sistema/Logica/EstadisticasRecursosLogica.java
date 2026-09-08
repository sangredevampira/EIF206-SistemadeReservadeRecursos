package Sistema.Logica;

import Sistema.DTO.EstadisticaCategoriaDTO;
import Sistema.DTO.EstadoReserva;
import Sistema.DTO.RecursoDTO;
import Sistema.DTO.ReservaDTO;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Calcula, para un periodo de fechas dado, cuantas veces se reservo un
// recurso de cada categoria. Se apoya en ReservaLogica (modulo de Kris)
// para obtener las reservas del periodo -- no lee ningun archivo directo,
// solo agrupa y cuenta lo que ReservaLogica ya le entrega.
public class EstadisticasRecursosLogica {

    private final ReservaLogica reservaLogica;

    public EstadisticasRecursosLogica() {
        this.reservaLogica = new ReservaLogica();
    }

    public ResultadoEstadisticas calcular(LocalDate fechaDesde, LocalDate fechaHasta) {

        if (fechaDesde == null || fechaHasta == null) {
            return ResultadoEstadisticas.fallo("Debe indicar ambas fechas del período.");
        }
        if (fechaDesde.isAfter(fechaHasta)) {
            return ResultadoEstadisticas.fallo("La fecha 'desde' no puede ser posterior a la fecha 'hasta'.");
        }

        List<ReservaDTO> reservas;
        try {
            reservas = reservaLogica.buscarPorPeriodo(fechaDesde, fechaHasta);
        } catch (IllegalArgumentException e) {
            return ResultadoEstadisticas.fallo(e.getMessage());
        }

        // Conteo por categoria (se ignoran las reservas canceladas).
        Map<String, Integer> conteo = new LinkedHashMap<>();

        for (ReservaDTO reserva : reservas) {
            if (reserva.getEstado() != EstadoReserva.ACTIVA) {
                continue;
            }
            if (reserva.getRecursosReservados() == null) {
                continue;
            }
            for (RecursoDTO recurso : reserva.getRecursosReservados()) {
                if (recurso.getCategoria() == null || recurso.getCategoria().getDescripcion() == null) {
                    continue;
                }
                String categoria = recurso.getCategoria().getDescripcion();
                conteo.merge(categoria, 1, Integer::sum);
            }
        }

        List<EstadisticaCategoriaDTO> datos = new ArrayList<>();
        for (Map.Entry<String, Integer> entrada : conteo.entrySet()) {
            datos.add(new EstadisticaCategoriaDTO(entrada.getKey(), entrada.getValue()));
        }
        datos.sort((a, b) -> a.getCategoria().compareToIgnoreCase(b.getCategoria()));

        return ResultadoEstadisticas.exito(datos);
    }

    public static class ResultadoEstadisticas {
        private final boolean exitoso;
        private final String mensajeError;
        private final List<EstadisticaCategoriaDTO> datos;

        private ResultadoEstadisticas(boolean exitoso, String mensajeError, List<EstadisticaCategoriaDTO> datos) {
            this.exitoso = exitoso;
            this.mensajeError = mensajeError;
            this.datos = datos;
        }

        public static ResultadoEstadisticas exito(List<EstadisticaCategoriaDTO> datos) {
            return new ResultadoEstadisticas(true, null, datos);
        }

        public static ResultadoEstadisticas fallo(String mensajeError) {
            return new ResultadoEstadisticas(false, mensajeError, null);
        }

        public boolean isExitoso() { return exitoso; }
        public String getMensajeError() { return mensajeError; }
        public List<EstadisticaCategoriaDTO> getDatos() { return datos; }
    }
}
