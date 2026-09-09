package Sistema;

import Sistema.DTO.ReportHeaderDTO;
import Sistema.DTO.ReportTableDTO;
import Sistema.Logica.Exception.ReportExceptionLogica;
import Sistema.Service.PdfReportService;

import java.io.File;
import java.util.List;

public class PruebaPdfReportService {

    public static void main(String[] args) {

        ReportHeaderDTO encabezado =
                new ReportHeaderDTO(
                        "Sistema de Gestión de Recursos",
                        "Reporte general de prueba",
                        "Usuario de prueba"
                );

        encabezado.agregarFiltro(
                "Fecha inicial",
                "01/08/2026"
        );

        encabezado.agregarFiltro(
                "Fecha final",
                "31/08/2026"
        );

        encabezado.agregarFiltro(
                "Categoría",
                "Todas"
        );

        ReportTableDTO tabla =
                new ReportTableDTO(
                        "Recursos registrados",
                        List.of(
                                "Código",
                                "Recurso",
                                "Categoría",
                                "Estado"
                        )
                );

        tabla.agregarFila(
                "R-001",
                "Proyector",
                "Equipo audiovisual",
                "Disponible"
        );

        tabla.agregarFila(
                "R-002",
                "Sala de reuniones",
                "Espacio",
                "Reservado"
        );

        tabla.agregarFila(
                "R-003",
                "Computadora portátil",
                "Equipo tecnológico",
                "Disponible"
        );

        File archivo = new File(
                "reporte_prueba.pdf"
        );

        PdfReportService servicio =
                new PdfReportService();

        try {

            servicio.generarReporteHorizontal(
                    archivo,
                    encabezado,
                    tabla
            );

            System.out.println(
                    "PDF generado correctamente en:"
            );

            System.out.println(
                    archivo.getAbsolutePath()
            );

        } catch (ReportExceptionLogica excepcion) {

            System.err.println(
                    excepcion.getMessage()
            );

            excepcion.printStackTrace();
        }
    }
}
