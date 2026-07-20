package com.loop.new_loop_api.stockcontrols.pdf;

import com.loop.new_loop_api.stockcontrols.entity.StockControl;
import com.loop.new_loop_api.stockcontrols.entity.StockControlItem;
import org.openpdf.text.*;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Renders the "remito" (delivery note) of a confirmed EXIT stock control as a PDF. */
@Component
public class RemitoPdfGenerator {

    private static final DateTimeFormatter DATE_FORMAT     = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final Color BRAND_COLOR  = new Color(30, 64, 110);
    private static final Color LABEL_BG     = new Color(245, 246, 248);
    private static final Color BORDER_COLOR = new Color(210, 214, 220);
    private static final Color STRIPE_COLOR = new Color(248, 249, 250);

    private static final Font TITLE_FONT        = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 19, BRAND_COLOR);
    private static final Font SUBTITLE_FONT     = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.GRAY);
    private static final Font BADGE_LABEL_FONT  = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE);
    private static final Font BADGE_VALUE_FONT  = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 17, Color.WHITE);
    private static final Font LABEL_FONT        = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.DARK_GRAY);
    private static final Font VALUE_FONT        = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);
    private static final Font SECTION_FONT      = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, BRAND_COLOR);
    private static final Font TABLE_HEADER_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
    private static final Font TABLE_CELL_FONT   = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.BLACK);
    private static final Font EMPTY_FONT        = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9, Color.GRAY);
    private static final Font FOOTER_FONT       = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 7, Color.GRAY);

    public byte[] generate(StockControl control) {
        var document = new Document(PageSize.A4, 40, 40, 50, 40);
        var output   = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(document, output);
            document.open();

            document.add(title());
            document.add(subtitle());
            document.add(spacer(16));
            document.add(remitoBadge(control));
            document.add(spacer(18));
            document.add(sectionTitle("Datos del control"));
            document.add(spacer(6));
            document.add(infoTable(control));
            document.add(spacer(20));
            document.add(sectionTitle("Productos cargados"));
            document.add(spacer(6));
            document.add(itemsTable(control));
            document.add(spacer(24));
            document.add(footer());

            document.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("Could not generate remito PDF", e);
        }
        return output.toByteArray();
    }

    private Paragraph title() {
        var paragraph = new Paragraph("REMITO DE SALIDA", TITLE_FONT);
        paragraph.setAlignment(Element.ALIGN_CENTER);
        return paragraph;
    }

    private Paragraph subtitle() {
        var paragraph = new Paragraph("LOOP · Control de reparto", SUBTITLE_FONT);
        paragraph.setAlignment(Element.ALIGN_CENTER);
        paragraph.setSpacingBefore(2);
        return paragraph;
    }

    /** Highlighted banner with the two numbers that matter most for the physical remito. */
    private PdfPTable remitoBadge(StockControl control) {
        var table = new PdfPTable(2);
        table.setWidthPercentage(100);
        setWidths(table, 1f, 1f);

        table.addCell(badgeCell("FORMULARIO Nº", control.getAguasFormulario()));
        table.addCell(badgeCell("REMITO Nº", String.valueOf(control.getAguasNroRemito())));
        return table;
    }

    private PdfPCell badgeCell(String label, String value) {
        var cell = new PdfPCell();
        cell.setBackgroundColor(BRAND_COLOR);
        cell.setPadding(12);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);

        var labelParagraph = new Paragraph(label, BADGE_LABEL_FONT);
        labelParagraph.setAlignment(Element.ALIGN_CENTER);
        var valueParagraph = new Paragraph(value != null ? value : "-", BADGE_VALUE_FONT);
        valueParagraph.setAlignment(Element.ALIGN_CENTER);
        valueParagraph.setSpacingBefore(2);

        cell.addElement(labelParagraph);
        cell.addElement(valueParagraph);
        return cell;
    }

    private PdfPTable infoTable(StockControl control) {
        var table = new PdfPTable(2);
        table.setWidthPercentage(100);
        setWidths(table, 1f, 1f);

        addLabelValue(table, "Reparto", control.getRoute().getCode());
        addLabelValue(table, "Sucursal", control.getBranch().getName());
        addLabelValue(table, "Fecha de control", control.getControlDate().format(DATE_FORMAT));
        if (control.getConfirmedAt() != null) {
            addLabelValue(table, "Confirmado", control.getConfirmedAt().format(DATETIME_FORMAT));
        }
        if (control.getObservations() != null && !control.getObservations().isBlank()) {
            addLabelValue(table, "Observaciones", control.getObservations());
        }
        return table;
    }

    private void addLabelValue(PdfPTable table, String label, String value) {
        var labelCell = new PdfPCell(new Phrase(label, LABEL_FONT));
        labelCell.setBackgroundColor(LABEL_BG);
        labelCell.setBorderColor(BORDER_COLOR);
        labelCell.setPadding(6);
        table.addCell(labelCell);

        var valueCell = new PdfPCell(new Phrase(value != null ? value : "-", VALUE_FONT));
        valueCell.setBorderColor(BORDER_COLOR);
        valueCell.setPadding(6);
        table.addCell(valueCell);
    }

    private Paragraph sectionTitle(String text) {
        return new Paragraph(text, SECTION_FONT);
    }

    /** Only shows products actually loaded (total > 0) and only the Total column. */
    private Element itemsTable(StockControl control) {
        List<StockControlItem> items = control.getItems().stream()
                .filter(item -> item.getTotalQuantity() != null && item.getTotalQuantity() > 0)
                .toList();

        if (items.isEmpty()) {
            return new Paragraph("No se registraron productos con cantidad.", EMPTY_FONT);
        }

        var table = new PdfPTable(3);
        table.setWidthPercentage(100);
        setWidths(table, 1.3f, 3.2f, 1f);

        addHeaderCell(table, "Código", Element.ALIGN_LEFT);
        addHeaderCell(table, "Producto", Element.ALIGN_LEFT);
        addHeaderCell(table, "Total", Element.ALIGN_RIGHT);

        var stripe = false;
        for (StockControlItem item : items) {
            var background = stripe ? STRIPE_COLOR : Color.WHITE;
            addCell(table, item.getProduct().getCode(), Element.ALIGN_LEFT, background);
            addCell(table, item.getProduct().getName(), Element.ALIGN_LEFT, background);
            addCell(table, String.valueOf(item.getTotalQuantity()), Element.ALIGN_RIGHT, background);
            stripe = !stripe;
        }
        return table;
    }

    private void addHeaderCell(PdfPTable table, String text, int alignment) {
        var cell = new PdfPCell(new Phrase(text, TABLE_HEADER_FONT));
        cell.setBackgroundColor(BRAND_COLOR);
        cell.setBorderColor(BRAND_COLOR);
        cell.setHorizontalAlignment(alignment);
        cell.setPadding(6);
        table.addCell(cell);
    }

    private void addCell(PdfPTable table, String text, int alignment, Color background) {
        var cell = new PdfPCell(new Phrase(text, TABLE_CELL_FONT));
        cell.setBackgroundColor(background);
        cell.setBorderColor(BORDER_COLOR);
        cell.setHorizontalAlignment(alignment);
        cell.setPadding(6);
        table.addCell(cell);
    }

    private Paragraph footer() {
        var text = "Documento generado automáticamente por LOOP el "
                + LocalDateTime.now().format(DATETIME_FORMAT);
        var paragraph = new Paragraph(text, FOOTER_FONT);
        paragraph.setAlignment(Element.ALIGN_CENTER);
        return paragraph;
    }

    private Paragraph spacer(float height) {
        var paragraph = new Paragraph(" ");
        paragraph.setSpacingAfter(0);
        paragraph.setLeading(height);
        return paragraph;
    }

    private void setWidths(PdfPTable table, float... widths) {
        try {
            table.setWidths(widths);
        } catch (DocumentException e) {
            throw new IllegalStateException("Invalid PDF table layout", e);
        }
    }
}
