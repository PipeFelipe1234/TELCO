package com.practica.backend.service;

import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.practica.backend.dto.ExportRequest;
import com.practica.backend.entity.Registro;
import com.practica.backend.entity.RegistroReporte;
import com.practica.backend.entity.Usuario;
import com.practica.backend.repository.RegistroReporteRepository;
import com.practica.backend.repository.RegistroRepository;
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
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class ExportService {

    private final RegistroRepository registroRepository;
    private final RegistroReporteRepository registroReporteRepository;
    private final UsuarioRepository usuarioRepository;

    // Zona horaria de Colombia
    private static final ZoneId ZONA_COLOMBIA = ZoneId.of("America/Bogota");

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter TIME_AM_PM_FORMATTER = DateTimeFormatter.ofPattern("h:mma", Locale.ENGLISH);
    private static final Locale LOCALE_ES = new Locale("es", "ES");

    // Encabezados de las 10 columnas
    private static final String[] HEADERS = {
            "Fecha", "Identificación", "Empleado", "Hora Entrada",
            "Ubicación Entrada", "Hora Salida", "Ubicación Salida",
            "Reporte", "Foto", "Horas Trabajadas"
    };

    public ExportService(RegistroRepository registroRepository,
            RegistroReporteRepository registroReporteRepository,
            UsuarioRepository usuarioRepository) {
        this.registroRepository = registroRepository;
        this.registroReporteRepository = registroReporteRepository;
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
    // 🕐 CONVERSIÓN DE ZONA HORARIA
    // ============================

    /**
     * Construye fecha y hora en zona Colombia. Las horas ya se guardan en hora
     * Colombia en la BD, por lo que NO se aplica conversión de instante (solo se
     * etiqueta la zona).
     */
    private ZonedDateTime toColombia(LocalDate fecha, LocalTime hora) {
        if (fecha == null || hora == null) {
            return null;
        }

        return ZonedDateTime.of(fecha, hora, ZONA_COLOMBIA);
    }

    /**
     * Convierte una hora a zona Colombia para mostrar en exportación.
     */
    private String formatTimeWithTimezone(LocalDate fechaRegistro, LocalTime horaOriginal) {
        ZonedDateTime fechaHoraColombia = toColombia(fechaRegistro, horaOriginal);
        if (fechaHoraColombia == null) {
            return "---";
        }

        return fechaHoraColombia.toLocalTime().format(TIME_AM_PM_FORMATTER).toLowerCase();
    }

    /**
     * Formatea la hora de un reporte. La hora ya está en hora Colombia en la BD,
     * por lo que se formatea directamente sin conversión de instante.
     */
    private String formatReporteHora(LocalDateTime fechaHora) {
        if (fechaHora == null) {
            return "---";
        }

        return fechaHora.toLocalTime().format(TIME_AM_PM_FORMATTER).toLowerCase();
    }

    /**
     * Obtiene fecha local Colombia para mostrar por registro (base: hora entrada).
     */
    private String formatDateWithTimezone(Registro registro) {
        ZonedDateTime fechaHoraColombia = toColombia(registro.getFecha(), registro.getHoraEntrada());
        if (fechaHoraColombia == null) {
            return registro.getFecha() != null ? registro.getFecha().format(DATE_FORMATTER) : "---";
        }
        return fechaHoraColombia.toLocalDate().format(DATE_FORMATTER);
    }

    /**
     * Calcula las horas trabajadas considerando la zona horaria
     */
    private String calcularHorasTrabajadas(Registro registro) {
        if (registro.getHorasTrabajadas() != null && registro.getMinutosTrabajados() != null) {
            int minutos = registro.getMinutosTrabajados() % 60;
            return registro.getHorasTrabajadas() + "h " + minutos + "m";
        }

        if (registro.getHoraSalida() == null) {
            return "---";
        }

        ZonedDateTime fechaHoraEntrada = toColombia(registro.getFecha(), registro.getHoraEntrada());
        ZonedDateTime fechaHoraSalida = toColombia(registro.getFecha(), registro.getHoraSalida());
        if (fechaHoraEntrada == null || fechaHoraSalida == null) {
            return "---";
        }

        if (fechaHoraSalida.isBefore(fechaHoraEntrada)) {
            fechaHoraSalida = fechaHoraSalida.plusDays(1);
        }

        Duration duracion = Duration.between(fechaHoraEntrada, fechaHoraSalida);
        long horas = duracion.toHours();
        long minutos = duracion.toMinutes() % 60;

        return horas + "h " + minutos + "m";
    }

    private String construirDetalleReportes(Registro registro) {
        List<RegistroReporte> reportes = registroReporteRepository.findByRegistroOrderByFechaHoraAsc(registro);
        if (reportes.isEmpty()) {
            return valorOGuion(registro.getReporte());
        }

        List<String> lineas = new ArrayList<>();
        int orden = 1;
        for (RegistroReporte reporte : reportes) {
            String linea = "* " + orden + ") Hora: " + formatReporteHora(reporte.getFechaHora())
                    + " | Texto: " + valorOGuion(reporte.getReporte())
                    + " | Foto: " + (reporte.getPicture() != null && !reporte.getPicture().isBlank() ? "Sí" : "No")
                    + " | Ubicación: " + valorOGuion(reporte.getUbicacion());
            lineas.add(linea);
            orden++;
        }

        return String.join("\n", lineas);
    }

    private String tieneFotoEnTurno(Registro registro) {
        if (registro.getPicture() != null && !registro.getPicture().isBlank()) {
            return "Sí";
        }

        boolean existeFotoEnReportes = registroReporteRepository
                .findByRegistroOrderByFechaHoraAsc(registro)
                .stream()
                .anyMatch(r -> r.getPicture() != null && !r.getPicture().isBlank());

        return existeFotoEnReportes ? "Sí" : "No";
    }

    // ============================
    // 📤 EXPORTACIÓN ADMIN - PDF
    // ============================

    /**
     * Exporta registros a PDF para administradores con filtros
     */
    public byte[] exportarPdfAdmin(ExportRequest filtros) throws Exception {
        List<Registro> registros = obtenerRegistrosFiltrados(filtros);
        return generarPdfAdmin(registros, "Reporte de Asistencia - Administrador");
    }

    /**
     * Exporta registros a PDF para administradores (método legacy para
     * compatibilidad)
     */
    public byte[] exportarPdfAdmin(int mes, int anio) throws Exception {
        List<Registro> registros = registroRepository.findByMesYAnio(mes, anio);
        return generarPdfAdmin(registros, "Reporte de Asistencia - " + getNombreMes(mes) + " " + anio);
    }

    // ============================
    // 📤 EXPORTACIÓN ADMIN - EXCEL
    // ============================

    /**
     * Exporta registros a Excel para administradores con filtros
     */
    public byte[] exportarExcelAdmin(ExportRequest filtros) throws Exception {
        List<Registro> registros = obtenerRegistrosFiltrados(filtros);
        return generarExcelAdmin(registros, "Reporte de Asistencia - Administrador");
    }

    /**
     * Exporta registros a Excel para administradores (método legacy para
     * compatibilidad)
     */
    public byte[] exportarExcelAdmin(int mes, int anio) throws Exception {
        List<Registro> registros = registroRepository.findByMesYAnio(mes, anio);
        return generarExcelAdmin(registros, "Registros " + getNombreMes(mes) + " " + anio);
    }

    // ============================
    // 📤 EXPORTACIÓN USUARIO - PDF
    // ============================

    /**
     * Exporta registros a PDF para un usuario específico por rango de fechas
     */
    public byte[] exportarPdfUsuario(Long usuarioId, LocalDate fechaInicio, LocalDate fechaFin) throws Exception {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        List<Registro> registros = obtenerRegistrosUsuarioFiltrados(usuario, fechaInicio, fechaFin);
        String titulo = "Reporte de Asistencia - " + usuario.getNombre();
        return generarPdfAdmin(registros, titulo);
    }

    /**
     * Exporta registros a PDF para usuario (método legacy para compatibilidad)
     */
    public byte[] exportarPdfUsuario(Usuario usuario, int mes, int anio) throws Exception {
        List<Registro> registros = registroRepository.findByUsuarioAndMesYAnio(usuario, mes, anio);
        return generarPdfAdmin(registros, "Reporte de Asistencia - " + usuario.getNombre());
    }

    // ============================
    // 📤 EXPORTACIÓN USUARIO - EXCEL
    // ============================

    /**
     * Exporta registros a Excel para un usuario específico por rango de fechas
     */
    public byte[] exportarExcelUsuario(Long usuarioId, LocalDate fechaInicio, LocalDate fechaFin) throws Exception {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        List<Registro> registros = obtenerRegistrosUsuarioFiltrados(usuario, fechaInicio, fechaFin);
        String titulo = "Registros - " + usuario.getNombre();
        return generarExcelAdmin(registros, titulo);
    }

    /**
     * Exporta registros a Excel para usuario (método legacy para compatibilidad)
     */
    public byte[] exportarExcelUsuario(Usuario usuario, int mes, int anio) throws Exception {
        List<Registro> registros = registroRepository.findByUsuarioAndMesYAnio(usuario, mes, anio);
        return generarExcelAdmin(registros, "Registros - " + usuario.getNombre());
    }

    // ============================
    // 🔍 FILTRADO DE REGISTROS
    // ============================

    /**
     * Obtiene todos los registros y los filtra según los criterios del
     * ExportRequest
     * Usa findAll() + filtrado en Java para manejar correctamente las zonas
     * horarias
     */
    private List<Registro> obtenerRegistrosFiltrados(ExportRequest filtros) {
        // Obtener TODOS los registros
        List<Registro> todosLosRegistros = registroRepository.findAll();

        return todosLosRegistros.stream()
                // Filtrar por rango de fechas
                .filter(r -> {
                    if (filtros.fechaInicio() != null && r.getFecha().isBefore(filtros.fechaInicio())) {
                        return false;
                    }
                    if (filtros.fechaFin() != null && r.getFecha().isAfter(filtros.fechaFin())) {
                        return false;
                    }
                    return true;
                })
                // Filtrar por usuarioId si se especifica
                .filter(r -> {
                    if (filtros.usuarioId() != null) {
                        return r.getUsuario().getId().equals(filtros.usuarioId());
                    }
                    return true;
                })
                // Filtrar por búsqueda (nombre o identificación)
                .filter(r -> {
                    if (filtros.busqueda() != null && !filtros.busqueda().trim().isEmpty()) {
                        String busquedaLower = filtros.busqueda().toLowerCase().trim();
                        String nombre = r.getUsuario().getNombre() != null ? r.getUsuario().getNombre().toLowerCase()
                                : "";
                        String identificacion = r.getUsuario().getIdentificacion() != null
                                ? r.getUsuario().getIdentificacion().toLowerCase()
                                : "";
                        return nombre.contains(busquedaLower) || identificacion.contains(busquedaLower);
                    }
                    return true;
                })
                // Ordenar por fecha descendente, luego por hora entrada descendente
                .sorted(Comparator
                        .comparing(Registro::getFecha).reversed()
                        .thenComparing(Comparator.comparing(Registro::getHoraEntrada).reversed()))
                .collect(Collectors.toList());
    }

    /**
     * Obtiene los registros de un usuario específico filtrados por rango de fechas
     */
    private List<Registro> obtenerRegistrosUsuarioFiltrados(Usuario usuario, LocalDate fechaInicio,
            LocalDate fechaFin) {
        List<Registro> todosLosRegistros = registroRepository.findAllByUsuario(usuario);

        return todosLosRegistros.stream()
                .filter(r -> {
                    if (fechaInicio != null && r.getFecha().isBefore(fechaInicio)) {
                        return false;
                    }
                    if (fechaFin != null && r.getFecha().isAfter(fechaFin)) {
                        return false;
                    }
                    return true;
                })
                .sorted(Comparator
                        .comparing(Registro::getFecha).reversed()
                        .thenComparing(Comparator.comparing(Registro::getHoraEntrada).reversed()))
                .collect(Collectors.toList());
    }

    // ============================
    // 📄 GENERACIÓN DE PDF
    // ============================

    private byte[] generarPdfAdmin(List<Registro> registros, String titulo) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        com.lowagie.text.Document document = new com.lowagie.text.Document(PageSize.A4.rotate(), 20, 20, 20, 20);
        PdfWriter.getInstance(document, out);
        document.open();

        // Fuentes
        Font titleFont = new Font(Font.HELVETICA, 16, Font.BOLD, Color.DARK_GRAY);
        Font subtitleFont = new Font(Font.HELVETICA, 10, Font.NORMAL, Color.GRAY);
        Font headerFont = new Font(Font.HELVETICA, 8, Font.BOLD, Color.WHITE);
        Font dataFont = new Font(Font.HELVETICA, 7, Font.NORMAL, Color.BLACK);

        // Título
        Paragraph titleParagraph = new Paragraph(titulo, titleFont);
        titleParagraph.setAlignment(Element.ALIGN_CENTER);
        document.add(titleParagraph);

        // Fecha de generación
        Paragraph fecha = new Paragraph("Generado el: " + LocalDate.now(ZONA_COLOMBIA).format(DATE_FORMATTER),
                subtitleFont);
        fecha.setAlignment(Element.ALIGN_CENTER);
        fecha.setSpacingAfter(15);
        document.add(fecha);

        // Tabla con 10 columnas
        PdfPTable table = new PdfPTable(10);
        table.setWidthPercentage(100);

        // Ajustar anchos de columnas
        float[] columnWidths = { 8f, 10f, 14f, 8f, 14f, 8f, 14f, 12f, 5f, 9f };
        table.setWidths(columnWidths);

        // Encabezados
        for (String header : HEADERS) {
            PdfPCell cell = new PdfPCell(new Phrase(header, headerFont));
            cell.setBackgroundColor(new Color(128, 128, 128)); // Gris
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setPadding(5);
            table.addCell(cell);
        }

        // Datos
        for (Registro registro : registros) {
            // 1. Fecha
            addPdfCell(table, formatDateWithTimezone(registro), dataFont, Element.ALIGN_CENTER);

            // 2. Identificación
            addPdfCell(table, registro.getUsuario().getIdentificacion(), dataFont, Element.ALIGN_CENTER);

            // 3. Empleado (nombre completo)
            addPdfCell(table, registro.getUsuario().getNombre(), dataFont, Element.ALIGN_LEFT);

            // 4. Hora Entrada
            addPdfCell(table, formatTimeWithTimezone(registro.getFecha(), registro.getHoraEntrada()),
                    dataFont, Element.ALIGN_CENTER);

            // 5. Ubicación Entrada
            addPdfCell(table, valorOGuion(registro.getUbicacionEntrada()), dataFont, Element.ALIGN_LEFT);

            // 6. Hora Salida
            addPdfCell(table, formatTimeWithTimezone(registro.getFecha(), registro.getHoraSalida()),
                    dataFont, Element.ALIGN_CENTER);

            // 7. Ubicación Salida
            addPdfCell(table, valorOGuion(registro.getUbicacionSalida()), dataFont, Element.ALIGN_LEFT);

            // 8. Reporte
            addPdfCell(table, construirDetalleReportes(registro), dataFont, Element.ALIGN_LEFT);

            // 9. Foto
            addPdfCell(table, tieneFotoEnTurno(registro),
                    dataFont, Element.ALIGN_CENTER);

            // 10. Horas Trabajadas
            addPdfCell(table, calcularHorasTrabajadas(registro), dataFont, Element.ALIGN_CENTER);
        }

        document.add(table);

        // Resumen
        Paragraph resumen = new Paragraph("\nTotal de registros: " + registros.size(), subtitleFont);
        resumen.setSpacingBefore(15);
        document.add(resumen);

        document.close();
        return out.toByteArray();
    }

    private void addPdfCell(PdfPTable table, String text, Font font, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "---", font));
        cell.setHorizontalAlignment(alignment);
        cell.setPadding(4);
        table.addCell(cell);
    }

    // ============================
    // 📊 GENERACIÓN DE EXCEL
    // ============================

    private byte[] generarExcelAdmin(List<Registro> registros, String titulo) throws Exception {
        try (Workbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Registros");

            // Estilos para encabezados
            CellStyle headerStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_50_PERCENT.getIndex());
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
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 9));

            // Fecha de generación
            org.apache.poi.ss.usermodel.Row subtitleRow = sheet.createRow(1);
            org.apache.poi.ss.usermodel.Cell subtitleCell = subtitleRow.createCell(0);
            subtitleCell.setCellValue("Generado el: " + LocalDate.now(ZONA_COLOMBIA).format(DATE_FORMATTER));
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 9));

            // Encabezados (fila 3, índice 2)
            org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(3);
            for (int i = 0; i < HEADERS.length; i++) {
                org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            // Datos
            int rowNum = 4;
            for (Registro registro : registros) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowNum++);

                // 1. Fecha
                createExcelCell(row, 0, formatDateWithTimezone(registro), dataCenterStyle);

                // 2. Identificación
                createExcelCell(row, 1, registro.getUsuario().getIdentificacion(), dataCenterStyle);

                // 3. Empleado
                createExcelCell(row, 2, registro.getUsuario().getNombre(), dataStyle);

                // 4. Hora Entrada
                createExcelCell(row, 3, formatTimeWithTimezone(registro.getFecha(), registro.getHoraEntrada()),
                        dataCenterStyle);

                // 5. Ubicación Entrada
                createExcelCell(row, 4, valorOGuion(registro.getUbicacionEntrada()), dataStyle);

                // 6. Hora Salida
                createExcelCell(row, 5, formatTimeWithTimezone(registro.getFecha(), registro.getHoraSalida()),
                        dataCenterStyle);

                // 7. Ubicación Salida
                createExcelCell(row, 6, valorOGuion(registro.getUbicacionSalida()), dataStyle);

                // 8. Reporte
                createExcelCell(row, 7, construirDetalleReportes(registro), dataStyle);

                // 9. Foto
                createExcelCell(row, 8, tieneFotoEnTurno(registro),
                        dataCenterStyle);

                // 10. Horas Trabajadas
                createExcelCell(row, 9, calcularHorasTrabajadas(registro), dataCenterStyle);
            }

            // Ajustar ancho de columnas
            int[] columnWidths = { 3500, 4000, 6000, 3500, 8000, 3500, 8000, 6000, 2000, 4000 };
            for (int i = 0; i < columnWidths.length; i++) {
                sheet.setColumnWidth(i, columnWidths[i]);
            }

            // Total de registros
            rowNum += 2;
            org.apache.poi.ss.usermodel.Row resumenRow = sheet.createRow(rowNum);
            org.apache.poi.ss.usermodel.Cell resumenCell = resumenRow.createCell(0);
            resumenCell.setCellValue("Total de registros: " + registros.size());
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
    // 📝 EXPORTAR A WORD (Legacy)
    // ============================

    /**
     * Exporta registros de un mes a Word (para ADMIN) - Método legacy
     */
    public byte[] exportarWordAdmin(int mes, int anio) throws Exception {
        List<Registro> registros = registroRepository.findByMesYAnio(mes, anio);
        return generarWord(registros, mes, anio, true);
    }

    /**
     * Exporta registros de un mes a Word (para USER) - Método legacy
     */
    public byte[] exportarWordUsuario(Usuario usuario, int mes, int anio) throws Exception {
        List<Registro> registros = registroRepository.findByUsuarioAndMesYAnio(usuario, mes, anio);
        return generarWord(registros, mes, anio, false);
    }

    private byte[] generarWord(List<Registro> registros, int mes, int anio, boolean esAdmin) throws Exception {
        try (XWPFDocument document = new XWPFDocument();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            // Título
            XWPFParagraph titleParagraph = document.createParagraph();
            titleParagraph.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun titleRun = titleParagraph.createRun();
            titleRun.setText("REGISTROS DE ASISTENCIA");
            titleRun.setBold(true);
            titleRun.setFontSize(20);
            titleRun.addBreak();

            XWPFRun subtitleRun = titleParagraph.createRun();
            subtitleRun.setText(getNombreMes(mes) + " " + anio);
            subtitleRun.setFontSize(14);
            subtitleRun.addBreak();

            XWPFRun dateRun = titleParagraph.createRun();
            dateRun.setText("Generado el: " + LocalDate.now().format(DATE_FORMATTER));
            dateRun.setFontSize(10);
            dateRun.setItalic(true);
            dateRun.addBreak();
            dateRun.addBreak();

            // Tabla con 10 columnas
            int numCols = HEADERS.length;
            XWPFTable table = document.createTable(registros.size() + 1, numCols);
            table.setWidth("100%");

            // Encabezados
            XWPFTableRow headerRow = table.getRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                XWPFTableCell cell = headerRow.getCell(i);
                cell.setColor("808080");
                XWPFParagraph p = cell.getParagraphs().get(0);
                p.setAlignment(ParagraphAlignment.CENTER);
                XWPFRun run = p.createRun();
                run.setText(HEADERS[i]);
                run.setBold(true);
                run.setColor("FFFFFF");
                run.setFontSize(8);
            }

            // Datos
            for (int i = 0; i < registros.size(); i++) {
                Registro registro = registros.get(i);
                XWPFTableRow row = table.getRow(i + 1);

                setWordCell(row, 0, registro.getFecha().format(DATE_FORMATTER));
                setWordCell(row, 1, registro.getUsuario().getIdentificacion());
                setWordCell(row, 2, registro.getUsuario().getNombre());
                setWordCell(row, 3, formatTimeWithTimezone(registro.getFecha(), registro.getHoraEntrada()));
                setWordCell(row, 4, valorOGuion(registro.getUbicacionEntrada()));
                setWordCell(row, 5, formatTimeWithTimezone(registro.getFecha(), registro.getHoraSalida()));
                setWordCell(row, 6, valorOGuion(registro.getUbicacionSalida()));
                setWordCell(row, 7, valorOGuion(registro.getReporte()));
                setWordCell(row, 8, registro.getPicture() != null && !registro.getPicture().isEmpty() ? "Sí" : "No");
                setWordCell(row, 9, calcularHorasTrabajadas(registro));
            }

            // Resumen
            XWPFParagraph resumenParagraph = document.createParagraph();
            resumenParagraph.setSpacingBefore(400);
            XWPFRun resumenRun = resumenParagraph.createRun();
            resumenRun.addBreak();
            resumenRun.setText("Total de registros: " + registros.size());
            resumenRun.setBold(true);

            document.write(out);
            return out.toByteArray();
        }
    }

    private void setWordCell(XWPFTableRow row, int col, String value) {
        XWPFTableCell cell = row.getCell(col);
        XWPFParagraph p = cell.getParagraphs().get(0);
        p.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun run = p.createRun();
        run.setText(value != null ? value : "---");
        run.setFontSize(7);
    }

    // ============================
    // 🔧 UTILIDADES
    // ============================

    /**
     * Retorna el valor o "---" si es nulo o vacío
     */
    private String valorOGuion(String valor) {
        if (valor == null || valor.trim().isEmpty()) {
            return "---";
        }
        return valor;
    }
}
