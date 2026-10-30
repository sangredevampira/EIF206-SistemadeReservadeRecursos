package Sistema.Service;

import Sistema.DTO.ReportHeaderDTO;
import Sistema.DTO.ReportTableDTO;
import Sistema.Logica.Exception.ReportExceptionLogica;
import com.lowagie.text.pdf.PdfDictionary;
import com.lowagie.text.pdf.PdfName;
import com.lowagie.text.pdf.PdfObject;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EstadisticasPdfTest {

    @TempDir
    Path carpetaTemporal;

    private PdfReportService servicio;

    @BeforeEach
    void prepararServicio() {
        servicio = new PdfReportService();
    }

    @Test
    void generaPdfDeEstadisticasDeRecursosConPeriodoYDatos()
            throws Exception {
        File archivo = carpetaTemporal.resolve(
                "estadisticas-recursos.pdf"
        ).toFile();
        ReportHeaderDTO encabezado = new ReportHeaderDTO(
                "Sistema de Gestión de Recursos",
                "Estadísticas de recursos",
                "Sofia"
        );
        encabezado.agregarFiltro("Desde", "01/10/2026");
        encabezado.agregarFiltro("Hasta", "31/10/2026");

        ReportTableDTO tabla = new ReportTableDTO(
                "Recursos reservados por categoría",
                List.of("Categoría", "Cantidad")
        );
        tabla.agregarFila("Computadoras", "8");
        tabla.agregarFila("Proyectores", "5");
        tabla.agregarFila("Cámaras", "3");

        servicio.generarReporte(archivo, encabezado, tabla);

        String texto = extraerTexto(archivo);
        assertAll(
                () -> assertTrue(archivo.isFile()),
                () -> assertTrue(archivo.length() > 0),
                () -> assertTrue(texto.contains(
                        "Sistema de Gestión de Recursos"
                )),
                () -> assertTrue(texto.contains("Estadísticas de recursos")),
                () -> assertTrue(texto.contains("01/10/2026")),
                () -> assertTrue(texto.contains("31/10/2026")),
                () -> assertTrue(texto.contains("Categoría")),
                () -> assertTrue(texto.contains("Computadoras")),
                () -> assertTrue(texto.contains("8"))
        );
    }

    @Test
    void generaPdfDeEstadisticasDeActividadesConPeriodoYDatos()
            throws Exception {
        File archivo = carpetaTemporal.resolve(
                "estadisticas-actividades.pdf"
        ).toFile();
        ReportHeaderDTO encabezado = new ReportHeaderDTO(
                "Sistema de Gestión de Recursos",
                "Estadísticas de actividades",
                "Sofia"
        );
        encabezado.agregarFiltro(
                "Período",
                "05/10/2026 al 25/10/2026"
        );

        ReportTableDTO tabla = new ReportTableDTO(
                "Actividades registradas por semana",
                List.of("Semana", "Cantidad")
        );
        tabla.agregarFila("05/10/2026 al 11/10/2026", "4");
        tabla.agregarFila("12/10/2026 al 18/10/2026", "7");
        tabla.agregarFila("19/10/2026 al 25/10/2026", "2");

        servicio.generarReporte(archivo, encabezado, tabla);

        String texto = extraerTexto(archivo);
        assertAll(
                () -> assertTrue(archivo.isFile()),
                () -> assertTrue(archivo.length() > 0),
                () -> assertTrue(texto.contains(
                        "Sistema de Gestión de Recursos"
                )),
                () -> assertTrue(texto.contains("Estadísticas de actividades")),
                () -> assertTrue(texto.contains("05/10/2026")),
                () -> assertTrue(texto.contains("25/10/2026")),
                () -> assertTrue(texto.contains("Semana")),
                () -> assertTrue(texto.contains("Actividades registradas")),
                () -> assertTrue(texto.contains("7"))
        );
    }

    @Test
    void generaPdfConImagenDeGraficoValida() throws Exception {
        File archivo = carpetaTemporal.resolve(
                "estadisticas-con-grafico.pdf"
        ).toFile();
        ReportHeaderDTO encabezado = new ReportHeaderDTO(
                "Sistema de Gestión de Recursos",
                "Estadísticas de recursos",
                "Sofia"
        );
        ReportTableDTO tabla = new ReportTableDTO(
                "Recursos reservados por categoría",
                List.of("Categoría", "Cantidad")
        );
        tabla.agregarFila("Computadoras", "8");

        servicio.generarReporteConGrafico(
                archivo,
                encabezado,
                tabla,
                "Recursos reservados por categoría",
                crearPngValido()
        );

        PdfReader lector = new PdfReader(archivo.getAbsolutePath());
        try {
            String texto = new PdfTextExtractor(lector).getTextFromPage(1);
            assertAll(
                    () -> assertTrue(archivo.length() > 0),
                    () -> assertEquals(1, lector.getNumberOfPages()),
                    () -> assertTrue(texto.contains(
                            "Recursos reservados por categoría"
                    )),
                    () -> assertTrue(contieneImagen(lector, 1))
            );
        } finally {
            lector.close();
        }
    }

    @Test
    void rechazaContenidoQueNoEsUnaImagenValida() {
        File archivo = carpetaTemporal.resolve(
                "estadisticas-imagen-invalida.pdf"
        ).toFile();
        ReportHeaderDTO encabezado = new ReportHeaderDTO(
                "Sistema de Gestión de Recursos",
                "Estadísticas de recursos",
                "Sofia"
        );
        ReportTableDTO tabla = new ReportTableDTO(
                "Recursos reservados por categoría",
                List.of("Categoría", "Cantidad")
        );
        tabla.agregarFila("Computadoras", "8");

        ReportExceptionLogica error = assertThrows(
                ReportExceptionLogica.class,
                () -> servicio.generarReporteConGrafico(
                        archivo,
                        encabezado,
                        tabla,
                        "Gráfico",
                        new byte[]{1, 2, 3, 4}
                )
        );

        assertEquals(
                "No se pudo generar el documento PDF.",
                error.getMessage()
        );
    }

    private String extraerTexto(File archivo) throws Exception {
        PdfReader lector = new PdfReader(archivo.getAbsolutePath());
        try {
            PdfTextExtractor extractor = new PdfTextExtractor(lector);
            StringBuilder texto = new StringBuilder();

            for (int pagina = 1; pagina <= lector.getNumberOfPages(); pagina++) {
                texto.append(extractor.getTextFromPage(pagina));
            }

            return texto.toString();
        } finally {
            lector.close();
        }
    }

    private byte[] crearPngValido() throws Exception {
        BufferedImage imagen = new BufferedImage(
                320,
                180,
                BufferedImage.TYPE_INT_RGB
        );
        Graphics2D graficos = imagen.createGraphics();

        try {
            graficos.setColor(Color.WHITE);
            graficos.fillRect(0, 0, imagen.getWidth(), imagen.getHeight());
            graficos.setColor(new Color(175, 37, 0));
            graficos.fillRect(40, 70, 220, 45);
        } finally {
            graficos.dispose();
        }

        try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            assertTrue(ImageIO.write(imagen, "png", salida));
            return salida.toByteArray();
        }
    }

    private boolean contieneImagen(PdfReader lector, int pagina) {
        PdfDictionary recursos = lector.getPageN(pagina).getAsDict(
                PdfName.RESOURCES
        );

        if (recursos == null) {
            return false;
        }

        PdfDictionary objetos = recursos.getAsDict(PdfName.XOBJECT);

        if (objetos == null) {
            return false;
        }

        for (PdfName nombre : objetos.getKeys()) {
            PdfObject objeto = PdfReader.getPdfObject(objetos.get(nombre));

            if (objeto instanceof PdfDictionary diccionario
                    && PdfName.IMAGE.equals(diccionario.getAsName(
                    PdfName.SUBTYPE
            ))) {
                return true;
            }
        }

        return false;
    }
}
