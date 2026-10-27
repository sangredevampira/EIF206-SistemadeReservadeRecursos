package Sistema.Service;

import Sistema.DTO.ReportHeaderDTO;
import Sistema.DTO.ReportTableDTO;
import Sistema.Logica.Exception.ReportExceptionLogica;
import com.lowagie.text.pdf.PdfReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PdfReportServiceTest {

    @TempDir
    Path carpetaTemporal;

    private PdfReportService pdfReportService;
    private ReportHeaderDTO encabezado;
    private ReportTableDTO tabla;

    @BeforeEach
    void prepararDatosDePrueba() {
        pdfReportService = new PdfReportService();

        encabezado = new ReportHeaderDTO("Sistema de Gestión de Recursos", "Reporte de prueba", "Sofia");
        encabezado.agregarFiltro("Período", "01/09/2026 al 30/09/2026");
        tabla = new ReportTableDTO("Actividades programadas", List.of("Hora", "Actividad"));
        tabla.agregarFila("07:00", "Reunión");
        tabla.agregarFila("07:30", "Reunión");
    }

    @Test
    void generarReporteCreaArchivoPdfCorrectamente() throws Exception {
        File archivo = carpetaTemporal.resolve("reporte-prueba.pdf").toFile();

        pdfReportService.generarReporte(archivo, encabezado, tabla);

        assertTrue(archivo.exists());
        assertTrue(archivo.length() > 0);
    }

    @Test
    void generarReporteHorizontalCreaPaginaApaisada() throws Exception {
        File archivo = carpetaTemporal.resolve("reporte-horizontal.pdf").toFile();

        pdfReportService.generarReporteHorizontal(archivo, encabezado, tabla);
        PdfReader lector = new PdfReader(archivo.getAbsolutePath());

        try {
            float ancho = lector.getPageSizeWithRotation(1).getWidth();
            float alto = lector.getPageSizeWithRotation(1).getHeight();
            assertTrue(ancho > alto);
        } finally {
            lector.close();
        }
    }

    @Test
    void generarReporteConGraficoVacioLanzaExcepcion() {
        File archivo = carpetaTemporal.resolve("reporte-sin-grafico.pdf").toFile();

        ReportExceptionLogica excepcion = assertThrows(
                ReportExceptionLogica.class,
                () -> pdfReportService.generarReporteConGrafico(
                        archivo, encabezado, tabla, "Actividades programadas", new byte[0])
        );

        assertEquals("No se proporcionó la imagen del gráfico.", excepcion.getMessage());
    }

    @Test
    void generarReporteSinColumnasLanzaExcepcion() {
        File archivo = carpetaTemporal.resolve("reporte-sin-columnas.pdf").toFile();

        ReportTableDTO tablaSinColumnas = new ReportTableDTO("Tabla vacía", List.of());

        ReportExceptionLogica excepcion = assertThrows(
                ReportExceptionLogica.class,
                () -> pdfReportService.generarReporte(archivo, encabezado, tablaSinColumnas)
        );

        assertEquals("El reporte debe contener al menos una columna.", excepcion.getMessage());
    }
}