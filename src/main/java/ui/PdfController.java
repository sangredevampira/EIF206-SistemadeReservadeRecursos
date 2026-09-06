package ui;

import Sistema.DTO.ReportConfigDTO;
import Sistema.DTO.ReportHeaderDTO;
import Sistema.DTO.ReportTableDTO;
import Sistema.Exception.ReportExceptionLogica;
import Sistema.Service.PdfReportService;
import Sistema.util.GraficoImagenUtil;
import Sistema.util.ReportTableViewUtil;
import Sistema.util.SelectorArchivoPdfUtil;
import javafx.scene.Node;
import javafx.scene.control.TableView;
import javafx.stage.Window;
import java.io.File;
import java.util.Map;

/**
 * Coordina el proceso completo de exportación de las tablas
 * y gráficos de la interfaz a documentos PDF.
 */
public class PdfController {

    private static final String NOMBRE_SISTEMA = "Sistema de Gestión de Recursos";
    private final PdfReportService pdfReportService = new PdfReportService();

    public <T> boolean generar(ReportConfigDTO configuracion, Window ventana, TableView<T> tableView
    ) throws ReportExceptionLogica {
        return generarInterno(configuracion, ventana, tableView, null);
    }

    public <T> boolean generarConGrafico(ReportConfigDTO configuracion, Window ventana, TableView<T> tableView, Node grafico
    ) throws ReportExceptionLogica {
        return generarInterno(configuracion, ventana, tableView, grafico);
    }

    private <T> boolean generarInterno(ReportConfigDTO configuracion, Window ventana, TableView<T> tableView, Node grafico
    ) throws ReportExceptionLogica {
        validarDatos(configuracion, tableView);
        File archivo = SelectorArchivoPdfUtil.seleccionar(ventana, configuracion.getTituloVentana(), configuracion.getNombreArchivo());
        if (archivo == null) return false;
        try {
            ReportHeaderDTO encabezado = crearEncabezado(configuracion);
            ReportTableDTO tabla = ReportTableViewUtil.convertir(configuracion.getTituloTabla(), tableView);
            if (grafico != null) {
                byte[] imagen = GraficoImagenUtil.convertirAPng(grafico);
                pdfReportService.generarReporteConGrafico(archivo, encabezado, tabla, configuracion.getTituloGrafico(), imagen);
            } else if (configuracion.isHorizontal()) {
                pdfReportService.generarReporteHorizontal(archivo, encabezado, tabla);
            } else {
                pdfReportService.generarReporte(archivo, encabezado, tabla);
            }
            return true;
        } catch (ReportExceptionLogica excepcion) {
            throw excepcion;
        } catch (Exception excepcion) {
            throw new ReportExceptionLogica("No se pudo preparar la información del reporte.", excepcion);
        }
    }

    private ReportHeaderDTO crearEncabezado(ReportConfigDTO configuracion) {
        ReportHeaderDTO encabezado = new ReportHeaderDTO(NOMBRE_SISTEMA, configuracion.getTituloReporte(), obtenerUsuarioActual());
        for (Map.Entry<String, String> filtro : configuracion.getFiltros().entrySet()) {
            encabezado.agregarFiltro(filtro.getKey(), filtro.getValue());
        }
        return encabezado;
    }

    private String obtenerUsuarioActual() {
        return System.getProperty("user.name", "Usuario no identificado");
    }

    private <T> void validarDatos(ReportConfigDTO configuracion, TableView<T> tableView
    ) throws ReportExceptionLogica {
        if (configuracion == null) {
            throw new ReportExceptionLogica("No se proporcionó la configuración del reporte.");
        }
        if (tableView == null) {
            throw new ReportExceptionLogica("No se proporcionó la tabla del reporte.");
        }
        if (tableView.getColumns().isEmpty()) {
            throw new ReportExceptionLogica("La tabla no contiene columnas.");
        }
    }
}