package Sistema.Logica;

import Sistema.DTO.Funcionario;
import Sistema.DTO.ReservaDTO;
import Sistema.Datos.ReservaDatos;

import java.util.List;

public class ReservaLogica {

    private final ReservaDatos reservaDatos;

    // El bug de "reservaDatos nunca se inicializa" se corrige aca: antes
    // no habia ningun constructor que le asignara un valor, asi que
    // cualquier metodo de esta clase habria tronado con
    // NullPointerException al primer uso.
    public ReservaLogica() {
        this.reservaDatos = new ReservaDatos("src/data/reservas.json");
    }

    // metodos de comprobacion
    public boolean reservaExistente(String id){
        for (ReservaDTO r : reservaDatos.deserializar()){
            if (r.getIdReserva().equals(id)){
                return true;
            }
        }
        return false;
    }

    /**
     * Busca en reservas.json todas las reservas del funcionario dado y se
     * las asigna a su atributo funcionario.reservas (ver Funcionario.java,
     * @JsonIgnore: ese atributo nunca se persiste, es solo un "casillero"
     * en memoria para que la UI lo consulte).
     *
     * Este es el metodo que reemplaza la idea original de
     * "filtrarReservasPorUsuario": en vez de que quien pida las reservas
     * reciba una lista suelta, se la dejamos ya cargada dentro del propio
     * Funcionario, que es el objeto que representa a quien reserva
     * (ver enunciado, funcionalidad 2).
     */
    public void cargarReservasDeFuncionario(Funcionario funcionario) {
        if (funcionario == null) {
            return;
        }
        List<ReservaDTO> reservas = reservaDatos.filtrarReservaFuncionario(funcionario.getId());
        funcionario.setReservas(reservas);
    }

    /**
     * Ejemplo pedido por Kris: recibe una lista de reservas ya armada
     * (por ejemplo, el resultado de una busqueda o filtro distinto a
     * "todas las reservas del funcionario") y la asigna directo al
     * atributo, sin volver a consultar el archivo.
     *
     * Nombre distinto a proposito de "agregarReserva" (singular, en
     * ReservaDatos) para no confundirlos: ese SI guarda en el JSON;
     * este SOLO pega una lista en memoria, en el atributo del Funcionario.
     */
    public void asignarReservasAFuncionario(Funcionario funcionario, List<ReservaDTO> reservas) {
        if (funcionario == null) {
            return;
        }
        funcionario.setReservas(reservas);
    }
}
