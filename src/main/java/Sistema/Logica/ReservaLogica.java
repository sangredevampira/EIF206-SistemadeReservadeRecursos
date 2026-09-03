package Sistema.Logica;

import Sistema.DTO.ReservaDTO;
import Sistema.Datos.ReservaDatos;

import java.util.List;

public class ReservaLogica {

    private ReservaDatos reservaDatos;


    // metodos de comprobacion
    public boolean reservaExistente(String id){
        for (ReservaDTO r : reservaDatos.deserializar()){
            if (r.getIdReserva().equals(id)){
                return true;
            }
        }
        return false;
    }
}
