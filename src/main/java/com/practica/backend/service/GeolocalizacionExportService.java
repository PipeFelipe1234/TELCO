package com.practica.backend.service;

import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.practica.backend.dto.GeolocalizacionExportRequest;
import com.practica.backend.dto.GeolocalizacionHistorialResponse;
import com.practica.backend.entity.SolicitudUbicacion;
import com.practica.backend.entity.Usuario;
import com.practica.backend.repository.SolicitudUbicacionRepository;
import com.practica.backend.repository.UsuarioRepository;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Servicio para exportar historial de geolocalizaciones a PDF y Excel
 */
@Service
public class GeolocalizacionExportService {

    private final SolicitudUbicacionRepository solicitudRepository;
    private final UsuarioRepository usuarioRepository;

    // Zona horaria de Colombia
    private static final ZoneId ZONA_COLOMBIA = ZoneId.of("America/Bogota");

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final Locale LOCALE_ES = new Locale("es", "ES");

    // Encabezados de las columnas para historial de geolocalización
    private static final String[] HEADERS = {
            "Fecha Solicitud", "Hora Solicitud", "Empleado", "Identificación",
            "Estado", "Ubicación", "Latitud", "Longitud",
            "Precisión (m)", "Fecha Respuesta", "Hora Respuesta", "Solicitado Por"
    };

    public GeolocalizacionExportService(
            SolicitudUbicacionRepository solicitudRepository,
            UsuarioRepository usuarioRepository) {
        this.solicitudRepository = solicitudRepository;
        this.usuarioRepository = usuarioRepository;
    }

    /**
     * Obtiene el nombre del mes en español
     */
    public String getNombreMes(int mes) {
        return LocalDate.of(2024, mes, 1)
                .getMonth()
                .getDisplayName(TextStyle.FULL, LOCALE_ES)
                .toUpperCase();
    }

    // ============================
    // 📊 HISTORIAL CON FILTROS
    // ============================

    /**
     * Obtiene el historial de geolocalizaciones con filtros
     */
    public List<GeolocalizacionHistorialResponse> obtenerHistorial(GeolocalizacionExportRequest filtros) {
        List<SolicitudUbicacion> solicitudes = obtenerSolicitudesFiltradas(filtros);
        return solicitudes.stream()
                .map(GeolocalizacionHistorialResponse::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Obtiene las solicitudes filtradas según los criterios.
     * IMPORTANTE: Solo retorna solicitudes MANUALES (excluye automáticas).
     * Si se especifica 'mes', filtra por ese mes (y año si se especifica, sino año
     * actual).
     * Si no se especifica 'mes', usa los filtros de fechaInicio/fechaFin.
     */
    private List<SolicitudUbicacion> obtenerSolicitudesFiltradas(GeolocalizacionExportRequest filtros) {
        // Si se especifica mes, usar el repositorio directo para filtrar por mes (SOLO
        // MANUALES)
        if (filtros != null && filtros.mes() != null) {
            int mes = filtros.mes();
            int anio = filtros.anio() != null ? filtros.anio() : LocalDate.now().getYear();
            return solicitudRepository.findManualesByMesYAnio(mes, anio);
        }

        // Si no hay filtro de mes, obtener SOLO MANUALES y filtrar en memoria
        List<SolicitudUbicacion> todas = solicitudRepository.findAllManualesOrderByFechaSolicitudDesc();

        return todas.stream()
                // Filtrar por rango de fechas
                .filter(s -> {
                    if (filtros == null)
                        return true;
                    LocalDate fechaSolicitud = s.getFechaSolicitud().toLocalDate();
                    if (filtros.fechaInicio() != null && fechaSolicitud.isBefore(filtros.fechaInicio())) {
                        return false;
                    }
                    if (filtros.fechaFin() != null && fechaSolicitud.isAfter(filtros.fechaFin())) {
                        return false;
                    }
                    return true;
                })
                // Filtrar por empleadoId si se especifica
                .filter(s -> {
                    if (filtros == null || filtros.empleadoId() == null)
                        return true;
                    return s.getEmpleado().getId().equals(filtros.empleadoId());
                })
                // Filtrar por estado
                .filter(s -> {
                    if (filtros == null || filtros.estado() == null || filtros.estado().trim().isEmpty())
                        return true;
                    return s.getEstado().equalsIgnoreCase(filtros.estado());
                })
                // Filtrar por búsqueda (nombre o identificación del empleado)
                .filter(s -> {
                    if (filtros == null || filtros.busqueda() == null || filtros.busqueda().trim().isEmpty())
                        return true;
                    String busquedaLower = filtros.busqueda().toLowerCase().trim();
                    String nombre = s.getEmpleado().getNombre() != null ? s.getEmpleado().getNombre().toLowerCase()
                            : "";
                    String identificacion = s.getEmpleado().getIdentificacion() != null
                            ? s.getEmpleado().getIdentificacion().toLowerCase()
                            : "";
                    return nombre.contains(busquedaLower) || identificacion.contains(busquedaLower);
                })
                .collect(Collectors.toList());
    }

    // ============================
    // 📤 EXPORTACIÓN PDF
    // ============================

    /**
     * Exporta el historial de geolocalizaciones a PDF
     * Si filtros contiene 'mes', exporta solo ese mes
     */
    public byte[] exportarPdf(GeolocalizacionExportRequest filtros) throws Exception {
        List<SolicitudUbicacion> solicitudes = obtenerSolicitudesFiltradas(filtros);

        // Generar título según el tipo de filtro
        String titulo;
        if (filtros != null && filtros.mes() != null) {
            int mes = filtros.mes();
            int anio = filtros.anio() != null ? filtros.anio() : LocalDate.now().getYear();
            titulo = "Geolocalizaciones - " + getNombreMes(mes) + " " + anio;
        } else {
            titulo = "Historial de Geolocalizaciones";
        }

        return generarPdf(solicitudes, titulo);
    }

    /**
     * Exporta por mes y año a PDF (SOLO MANUALES)
     */
    public byte[] exportarPdfPorMes(int mes, int anio) throws Exception {
        List<SolicitudUbicacion> solicitudes = solicitudRepository.findManualesByMesYAnio(mes, anio);
        return generarPdf(solicitudes, "Geolocalizaciones - " + getNombreMes(mes) + " " + anio);
    }

    private byte[] generarPdf(List<SolicitudUbicacion> solicitudes, String titulo) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        com.lowagie.text.Document document = new com.lowagie.text.Document(PageSize.A4.rotate(), 15, 15, 15, 15);
        PdfWriter.getInstance(document, out);
        document.open();

        // Fuentes
        Font titleFont = new Font(Font.HELVETICA, 14, Font.BOLD, Color.DARK_GRAY);
        Font subtitleFont = new Font(Font.HELVETICA, 9, Font.NORMAL, Color.GRAY);
        Font headerFont = new Font(Font.HELVETICA, 7, Font.BOLD, Color.WHITE);
        Font dataFont = new Font(Font.HELVETICA, 6, Font.NORMAL, Color.BLACK);

        // Título
        Paragraph titleParagraph = new Paragraph(titulo, titleFont);
        titleParagraph.setAlignment(Element.ALIGN_CENTER);
        document.add(titleParagraph);

        // Fecha de generación
        Paragraph fecha = new Paragraph("Generado el: " + LocalDate.now().format(DATE_FORMATTER), subtitleFont);
        fecha.setAlignment(Element.ALIGN_CENTER);
        fecha.setSpacingAfter(10);
        document.add(fecha);

        // Tabla con 12 columnas
        PdfPTable table = new PdfPTable(12);
        table.setWidthPercentage(100);

        // Ajustar anchos de columnas
        float[] columnWidths = { 8f, 7f, 12f, 9f, 8f, 14f, 7f, 7f, 6f, 8f, 7f, 10f };
        table.setWidths(columnWidths);

        // Encabezados
        for (String header : HEADERS) {
            PdfPCell cell = new PdfPCell(new Phrase(header, headerFont));
            cell.setBackgroundColor(new Color(70, 130, 180)); // Steel Blue
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setPadding(4);
            table.addCell(cell);
        }

        // Datos
        for (SolicitudUbicacion s : solicitudes) {
            // 1. Fecha Solicitud
            addPdfCell(table, s.getFechaSolicitud().toLocalDate().format(DATE_FORMATTER), dataFont,
                    Element.ALIGN_CENTER);

            // 2. Hora Solicitud
            addPdfCell(table, s.getFechaSolicitud().toLocalTime().format(TIME_FORMATTER), dataFont,
                    Element.ALIGN_CENTER);

            // 3. Empleado
            addPdfCell(table, s.getEmpleado().getNombre(), dataFont, Element.ALIGN_LEFT);

            // 4. Identificación
            addPdfCell(table, s.getEmpleado().getIdentificacion(), dataFont, Element.ALIGN_CENTER);

            // 5. Estado
            addPdfCell(table, s.getEstado(), dataFont, Element.ALIGN_CENTER);

            // 6. Ubicación
            addPdfCell(table, valorOGuion(s.getUbicacion()), dataFont, Element.ALIGN_LEFT);

            // 7. Latitud
            addPdfCell(table, s.getLatitud() != null ? String.format("%.6f", s.getLatitud()) : "---", dataFont,
                    Element.ALIGN_CENTER);

            // 8. Longitud
            addPdfCell(table, s.getLongitud() != null ? String.format("%.6f", s.getLongitud()) : "---", dataFont,
                    Element.ALIGN_CENTER);

            // 9. Precisión
            addPdfCell(table, s.getPrecisionMetros() != null ? String.format("%.1f", s.getPrecisionMetros()) : "---",
                    dataFont, Element.ALIGN_CENTER);

            // 10. Fecha Respuesta
            addPdfCell(table,
                    s.getFechaRespuesta() != null ? s.getFechaRespuesta().toLocalDate().format(DATE_FORMATTER) : "---",
                    dataFont, Element.ALIGN_CENTER);

            // 11. Hora Respuesta
            addPdfCell(table,
                    s.getFechaRespuesta() != null ? s.getFechaRespuesta().toLocalTime().format(TIME_FORMATTER) : "---",
                    dataFont, Element.ALIGN_CENTER);

            // 12. Solicitado Por (Admin)
            addPdfCell(table, s.getAdmin().getNombre(), dataFont, Element.ALIGN_LEFT);
        }

        document.add(table);

        // Resumen
        Paragraph resumen = new Paragraph("\nTotal de geolocalizaciones: " + solicitudes.size(), subtitleFont);
        resumen.setSpacingBefore(10);
        document.add(resumen);

        document.close();
        return out.toByteArray();
    }

    private void addPdfCell(PdfPTable table, String text, Font font, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "---", font));
        cell.setHorizontalAlignment(alignment);
        cell.setPadding(3);
        table.addCell(cell);
    }

    // ============================
    // 📊 EXPORTACIÓN EXCEL
    // ============================

    /**
     * Exporta el historial de geolocalizaciones a Excel
     * Si filtros contiene 'mes', exporta solo ese mes
     */
    public byte[] exportarExcel(GeolocalizacionExportRequest filtros) throws Exception {
        List<SolicitudUbicacion> solicitudes = obtenerSolicitudesFiltradas(filtros);

        // Generar título según el tipo de filtro
        String titulo;
        if (filtros != null && filtros.mes() != null) {
            int mes = filtros.mes();
            int anio = filtros.anio() != null ? filtros.anio() : LocalDate.now().getYear();
            titulo = "Geolocalizaciones - " + getNombreMes(mes) + " " + anio;
        } else {
            titulo = "Historial de Geolocalizaciones";
        }

        return generarExcel(solicitudes, titulo);
    }

    /**
     * Exporta por mes y año a Excel (SOLO MANUALES)
     */
    public byte[] exportarExcelPorMes(int mes, int anio) throws Exception {
        List<SolicitudUbicacion> solicitudes = solicitudRepository.findManualesByMesYAnio(mes, anio);
        return generarExcel(solicitudes, "Geolocalizaciones - " + getNombreMes(mes) + " " + anio);
    }

    private byte[] generarExcel(List<SolicitudUbicacion> solicitudes, String titulo) throws Exception {
        try (Workbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Geolocalizaciones");

            // Estilos para encabezados
            CellStyle headerStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);

            // Estilo para título
            CellStyle titleStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            titleStyle.setFont(titleFont);
            titleStyle.setAlignment(HorizontalAlignment.CENTER);

            // Estilo para datos
            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setBorderBottom(BorderStyle.THIN);
            dataStyle.setBorderTop(BorderStyle.THIN);
            dataStyle.setBorderLeft(BorderStyle.THIN);
            dataStyle.setBorderRight(BorderStyle.THIN);
            dataStyle.setWrapText(true);

            CellStyle dataCenterStyle = workbook.createCellStyle();
            dataCenterStyle.cloneStyleFrom(dataStyle);
            dataCenterStyle.setAlignment(HorizontalAlignment.CENTER);

            // Título
            org.apache.poi.ss.usermodel.Row titleRow = sheet.createRow(0);
            org.apache.poi.ss.usermodel.Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue(titulo);
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 11));

            // Fecha de generación
            org.apache.poi.ss.usermodel.Row subtitleRow = sheet.createRow(1);
            org.apache.poi.ss.usermodel.Cell subtitleCell = subtitleRow.createCell(0);
            subtitleCell.setCellValue("Generado el: " + LocalDate.now().format(DATE_FORMATTER));
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 11));

            // Encabezados (fila 3, índice 2)
            org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(3);
            for (int i = 0; i < HEADERS.length; i++) {
                org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            // Datos
            int rowNum = 4;
            for (SolicitudUbicacion s : solicitudes) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowNum++);

                // 1. Fecha Solicitud
                createExcelCell(row, 0, s.getFechaSolicitud().toLocalDate().format(DATE_FORMATTER), dataCenterStyle);

                // 2. Hora Solicitud
                createExcelCell(row, 1, s.getFechaSolicitud().toLocalTime().format(TIME_FORMATTER), dataCenterStyle);

                // 3. Empleado
                createExcelCell(row, 2, s.getEmpleado().getNombre(), dataStyle);

                // 4. Identificación
                createExcelCell(row, 3, s.getEmpleado().getIdentificacion(), dataCenterStyle);

                // 5. Estado
                createExcelCell(row, 4, s.getEstado(), dataCenterStyle);

                // 6. Ubicación
                createExcelCell(row, 5, valorOGuion(s.getUbicacion()), dataStyle);

                // 7. Latitud
                createExcelCell(row, 6, s.getLatitud() != null ? String.format("%.6f", s.getLatitud()) : "---",
                        dataCenterStyle);

                // 8. Longitud
                createExcelCell(row, 7, s.getLongitud() != null ? String.format("%.6f", s.getLongitud()) : "---",
                        dataCenterStyle);

                // 9. Precisión
                createExcelCell(row, 8,
                        s.getPrecisionMetros() != null ? String.format("%.1f", s.getPrecisionMetros()) : "---",
                        dataCenterStyle);

                // 10. Fecha Respuesta
                createExcelCell(row, 9,
                        s.getFechaRespuesta() != null ? s.getFechaRespuesta().toLocalDate().format(DATE_FORMATTER)
                                : "---",
                        dataCenterStyle);

                // 11. Hora Respuesta
                createExcelCell(row, 10,
                        s.getFechaRespuesta() != null ? s.getFechaRespuesta().toLocalTime().format(TIME_FORMATTER)
                                : "---",
                        dataCenterStyle);

                // 12. Solicitado Por (Admin)
                createExcelCell(row, 11, s.getAdmin().getNombre(), dataStyle);
            }

            // Ajustar ancho de columnas
            int[] columnWidths = { 3500, 3000, 6000, 4000, 3500, 8000, 3500, 3500, 3000, 3500, 3000, 5000 };
            for (int i = 0; i < columnWidths.length; i++) {
                sheet.setColumnWidth(i, columnWidths[i]);
            }

            // Total de geolocalizaciones
            rowNum += 2;
            org.apache.poi.ss.usermodel.Row resumenRow = sheet.createRow(rowNum);
            org.apache.poi.ss.usermodel.Cell resumenCell = resumenRow.createCell(0);
            resumenCell.setCellValue("Total de geolocalizaciones: " + solicitudes.size());
            resumenCell.setCellStyle(titleStyle);

            workbook.write(out);
            return out.toByteArray();
        }
    }

    private void createExcelCell(org.apache.poi.ss.usermodel.Row row, int col, String value, CellStyle style) {
        org.apache.poi.ss.usermodel.Cell cell = row.createCell(col);
        cell.setCellValue(value != null ? value : "---");
        cell.setCellStyle(style);
    }

    // ============================
    // 📅 MESES DISPONIBLES (SOLO MANUALES)
    // ============================

    /**
     * Obtiene los meses que tienen geolocalizaciones MANUALES para exportar
     * (excluye automáticas)
     */
    public List<Map<String, Object>> obtenerMesesDisponibles() {
        List<Map<String, Object>> meses = new ArrayList<>();

        LocalDateTime fechaMasAntigua = solicitudRepository.findFechaMasAntiguaManuales();
        LocalDateTime fechaMasReciente = solicitudRepository.findFechaMasRecienteManuales();

        if (fechaMasAntigua == null || fechaMasReciente == null) {
            return meses;
        }

        LocalDate inicio = fechaMasAntigua.toLocalDate().withDayOfMonth(1);
        LocalDate fin = fechaMasReciente.toLocalDate().withDayOfMonth(1);

        while (!inicio.isAfter(fin)) {
            int mes = inicio.getMonthValue();
            int anio = inicio.getYear();
            long cantidad = solicitudRepository.countManualesByMesYAnio(mes, anio);

            if (cantidad > 0) {
                meses.add(Map.of(
                        "mes", mes,
                        "anio", anio,
                        "nombreMes", getNombreMes(mes),
                        "cantidad", cantidad,
                        "label", getNombreMes(mes) + " " + anio));
            }

            inicio = inicio.plusMonths(1);
        }

        return meses;
    }

    // ============================
    // 🧹 UTILIDADES
    // ============================

    private String valorOGuion(String valor) {
        return (valor != null && !valor.trim().isEmpty()) ? valor : "---";
    }
}
