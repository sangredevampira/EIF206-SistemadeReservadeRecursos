package Sistema.util;

import java.time.LocalTime;

public final class HorarioSistema {
    public static final LocalTime INICIO_SERVICIO = LocalTime.of(6, 0);
    public static final LocalTime FIN_SERVICIO = LocalTime.of(22, 0);
    public static final int MINUTOS_POR_BLOQUE = 30;

    private HorarioSistema() {
    }
}