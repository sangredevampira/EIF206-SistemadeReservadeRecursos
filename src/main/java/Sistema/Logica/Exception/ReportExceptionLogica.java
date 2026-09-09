package Sistema.Logica.Exception;

public class ReportExceptionLogica extends Exception {

    public ReportExceptionLogica(
            String mensaje
    ) {
        super(mensaje);
    }

    public ReportExceptionLogica(
            String mensaje,
            Throwable causa
    ) {
        super(mensaje, causa);
    }
}
