package Sistema.Service;

import Sistema.DTO.ReportHeaderDTO;
import Sistema.DTO.ReportTableDTO;
import Sistema.Logica.ReportExceptionLogica;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.*;

import java.awt.*;
import java.io.File;
import java.io.FileOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public class PdfReportService {

    private static final DateTimeFormatter FORMATO_FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Font FUENTE_TITULO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
    private static final Font FUENTE_SUBTITULO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
    private static final Font FUENTE_CONTENIDO = FontFactory.getFont(FontFactory.HELVETICA, 9);
    private static final Font FUENTE_ENCABEZADO_TABLA = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
    private static final Font FUENTE_PIE = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY);
    private static final Color COLOR_ENCABEZADO = new Color(55, 95, 145);
    private static final Color COLOR_FILA_ALTERNA = new Color(240, 244, 248);

    public void generarReporte(File archivo, ReportHeaderDTO encabezado, ReportTableDTO tabla
    ) throws ReportExceptionLogica {
        generarReporteInterno(archivo, encabezado, tabla, PageSize.A4, null);
    }

    public void generarReporteHorizontal(File archivo, ReportHeaderDTO encabezado, ReportTableDTO tabla
    ) throws ReportExceptionLogica {
        generarReporteInterno(archivo, encabezado, tabla, PageSize.A4.rotate(), null);
    }

    public void generarReporteConGrafico(File archivo, ReportHeaderDTO encabezado, ReportTableDTO tabla, byte[] imagenGrafico
    ) throws ReportExceptionLogica {
        if (imagenGrafico == null || imagenGrafico.length == 0) {
            throw new ReportExceptionLogica("No se proporcionó la imagen del gráfico.");
        }
        generarReporteInterno(archivo, encabezado, tabla, PageSize.A4, imagenGrafico);
    }

    private void generarReporteInterno(File archivo, ReportHeaderDTO encabezado, ReportTableDTO tabla, Rectangle tamanioPagina, byte[] imagenGrafico
    ) throws ReportExceptionLogica {
        validarDatos(archivo, encabezado, tabla);
        Document documento = new Document(tamanioPagina, 40, 40, 75, 55);
        try {
            PdfWriter escritor = PdfWriter.getInstance(documento, new FileOutputStream(archivo));
            escritor.setPageEvent(new EventoPagina(encabezado.getNombreSistema()));
            documento.open();
            agregarInformacionGeneral(documento, encabezado);
            agregarTabla(documento, tabla);
            if (imagenGrafico != null && imagenGrafico.length > 0) {
                agregarGrafico(documento, imagenGrafico);
            }
        } catch (Exception excepcion) {
            throw new ReportExceptionLogica("No se pudo generar el documento PDF.", excepcion);
        } finally {
            if (documento.isOpen()) {
                documento.close();
            }
        }
    }

    private void validarDatos(File archivo, ReportHeaderDTO encabezado, ReportTableDTO tabla) throws ReportExceptionLogica {
        if (archivo == null) throw new ReportExceptionLogica("No se seleccionó el archivo de destino.");
        if (encabezado == null) throw new ReportExceptionLogica("No se proporcionó el encabezado del reporte.");
        if (tabla == null) throw new ReportExceptionLogica("No se proporcionaron los datos del reporte.");
        if (tabla.getColumnas().isEmpty()) throw new ReportExceptionLogica("El reporte debe contener al menos una columna.");
    }

    private void agregarInformacionGeneral(Document documento, ReportHeaderDTO encabezado) throws DocumentException {
        Paragraph titulo = new Paragraph(textoSeguro(encabezado.getTitulo()), FUENTE_TITULO);
        titulo.setAlignment(Element.ALIGN_CENTER);
        titulo.setSpacingAfter(15);
        documento.add(titulo);
        documento.add(new Paragraph("Fecha y hora de generación: " + encabezado.getFechaGeneracion().format(FORMATO_FECHA_HORA), FUENTE_CONTENIDO));
        documento.add(new Paragraph("Usuario: " + textoSeguro(encabezado.getUsuario()), FUENTE_CONTENIDO));
        agregarFiltros(documento, encabezado.getFiltros());
        documento.add(new Paragraph(" "));
    }

    private void agregarFiltros(Document documento, Map<String, String> filtros) throws DocumentException {
        Paragraph tituloFiltros = new Paragraph("Filtros aplicados:", FUENTE_SUBTITULO);
        tituloFiltros.setSpacingBefore(8);
        tituloFiltros.setSpacingAfter(4);
        documento.add(tituloFiltros);
        if (filtros == null || filtros.isEmpty()) {
            documento.add(new Paragraph("Ninguno", FUENTE_CONTENIDO));
            return;
        }
        PdfPTable tablaFiltros = new PdfPTable(2);
        tablaFiltros.setWidthPercentage(100);
        tablaFiltros.setWidths(new float[]{1, 3});
        for (Map.Entry<String, String> filtro : filtros.entrySet()) {
            PdfPCell celdaNombre = new PdfPCell(new Phrase(textoSeguro(filtro.getKey()), FUENTE_SUBTITULO));
            PdfPCell celdaValor = new PdfPCell(new Phrase(textoSeguro(filtro.getValue()), FUENTE_CONTENIDO));
            celdaNombre.setPadding(4);
            celdaValor.setPadding(4);
            tablaFiltros.addCell(celdaNombre);
            tablaFiltros.addCell(celdaValor);
        }
        documento.add(tablaFiltros);
    }

    private void agregarTabla(Document documento, ReportTableDTO reporte) throws DocumentException {
        if (reporte.getTitulo() != null && !reporte.getTitulo().isBlank()) {
            Paragraph tituloTabla = new Paragraph(reporte.getTitulo(), FUENTE_SUBTITULO);
            tituloTabla.setSpacingAfter(6);
            documento.add(tituloTabla);
        }
        PdfPTable tablaPdf = new PdfPTable(reporte.getColumnas().size());
        tablaPdf.setWidthPercentage(100);
        tablaPdf.setHeaderRows(1);
        tablaPdf.setSplitLate(false);
        for (String columna : reporte.getColumnas()) {
            PdfPCell celdaEncabezado = new PdfPCell(new Phrase(textoSeguro(columna), FUENTE_ENCABEZADO_TABLA));
            celdaEncabezado.setBackgroundColor(COLOR_ENCABEZADO);
            celdaEncabezado.setHorizontalAlignment(Element.ALIGN_CENTER);
            celdaEncabezado.setPadding(6);
            tablaPdf.addCell(celdaEncabezado);
        }
        int numeroFila = 0;
        for (List<String> fila : reporte.getFilas()) {
            for (String valor : fila) {
                PdfPCell celda = new PdfPCell(new Phrase(textoSeguro(valor), FUENTE_CONTENIDO));
                celda.setPadding(5);
                if (numeroFila % 2 != 0) celda.setBackgroundColor(COLOR_FILA_ALTERNA);
                tablaPdf.addCell(celda);
            }
            numeroFila++;
        }
        documento.add(tablaPdf);
    }

    private void agregarGrafico(Document documento, byte[] imagenGrafico
    ) throws Exception {
        Paragraph tituloGrafico = new Paragraph("Gráfico", FUENTE_SUBTITULO);
        tituloGrafico.setSpacingBefore(15);
        tituloGrafico.setSpacingAfter(8);
        documento.add(tituloGrafico);
        Image imagenPdf = Image.getInstance(imagenGrafico);
        float anchoDisponible = documento.right() - documento.left();
        imagenPdf.scaleToFit(anchoDisponible, 300);
        imagenPdf.setAlignment(Image.ALIGN_CENTER);
        documento.add(imagenPdf);
    }

    private static String textoSeguro(String texto) {
        return texto == null ? "" : texto;
    }

    private static class EventoPagina extends PdfPageEventHelper {

        private final String nombreSistema;

        public EventoPagina(String nombreSistema) {
            this.nombreSistema = nombreSistema;
        }

        @Override
        public void onEndPage(PdfWriter escritor, Document documento) {
            PdfContentByte contenido = escritor.getDirectContent();
            ColumnText.showTextAligned(contenido, Element.ALIGN_LEFT,
                    new Phrase(textoSeguro(nombreSistema), FUENTE_PIE),
                    documento.left(), documento.top() + 30, 0);
            ColumnText.showTextAligned(contenido, Element.ALIGN_LEFT,
                    new Phrase("Reporte generado por el sistema", FUENTE_PIE),
                    documento.left(), documento.bottom() - 25, 0);
            ColumnText.showTextAligned(contenido, Element.ALIGN_RIGHT,
                    new Phrase("Página " + escritor.getPageNumber(), FUENTE_PIE),
                    documento.right(), documento.bottom() - 25, 0);
        }
    }
}
