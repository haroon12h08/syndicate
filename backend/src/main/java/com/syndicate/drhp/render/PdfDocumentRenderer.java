package com.syndicate.drhp.render;

import com.syndicate.drhp.CompileMode;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.util.Matrix;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure deterministic rendering of a DRHP document into PDF using Apache PDFBox 3.x.
 *
 * <p>Spec §35 & Build Plan Ticket E6:
 * - Numbering, tables, footnotes, cross-references.
 * - Watermarked "DRAFT — NOT FOR FILING" on DRAFT_PREVIEW mode; clean for FINAL_FILING.
 * - Header/footer with page numbers and Merkle integrity hash.
 * - Pure downstream function of DocumentRenderModel.
 */
@Component
public class PdfDocumentRenderer {

    private static final PDRectangle PAGE_SIZE = PDRectangle.A4;
    private static final float PAGE_WIDTH = PAGE_SIZE.getWidth();
    private static final float PAGE_HEIGHT = PAGE_SIZE.getHeight();
    private static final float MARGIN_LEFT = 50f;
    private static final float MARGIN_RIGHT = 50f;
    private static final float MARGIN_TOP = 55f;
    private static final float MARGIN_BOTTOM = 55f;
    private static final float CONTENT_WIDTH = PAGE_WIDTH - MARGIN_LEFT - MARGIN_RIGHT;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm 'UTC'")
            .withZone(ZoneOffset.UTC);

    private final PDType1Font regularFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private final PDType1Font boldFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private final PDType1Font italicFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);
    private final PDType1Font monoFont = new PDType1Font(Standard14Fonts.FontName.COURIER);

    public byte[] render(DocumentRenderModel model) {
        try (PDDocument document = new PDDocument()) {
            RenderContext ctx = new RenderContext(document, model);

            // 1. Cover Page & Header
            renderCoverPage(ctx);

            // 2. Table of Contents
            renderTableOfContents(ctx);

            // 3. Sections & Paragraphs
            renderSections(ctx);

            // 4. Evidence & Fact Provenance Schedule
            renderProvenanceSchedule(ctx);

            // 5. Sign-off & Approvals Block
            renderApprovalsBlock(ctx);

            // Close last page stream
            ctx.closeCurrentStream();

            // 6. Second pass: draw headers, footers, page numbers and watermarks on every page
            applyPageChromeAndWatermarks(ctx);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to render DRHP PDF: " + e.getMessage(), e);
        }
    }

    private void renderCoverPage(RenderContext ctx) throws IOException {
        ctx.ensurePage();
        ctx.cursorY -= 20;

        // Document Type / Classification Banner
        boolean isDraft = ctx.model.compileMode() == CompileMode.DRAFT_PREVIEW;
        String banner = isDraft
                ? "DRAFT OFFER DOCUMENT — FOR TRANSACTION TEAM & DILIGENCE REVIEW ONLY"
                : "DRAFT RED HERRING PROSPECTUS — CONFIDENTIAL REGULATORY FILING COPY";
        drawText(ctx, banner, MARGIN_LEFT, ctx.cursorY, boldFont, 10, isDraft ? new Color(180, 50, 50) : new Color(30, 90, 160));
        ctx.cursorY -= 25;

        // Title
        drawText(ctx, ctx.model.companyName(), MARGIN_LEFT, ctx.cursorY, boldFont, 22, Color.BLACK);
        ctx.cursorY -= 22;

        drawText(ctx, ctx.model.transactionName(), MARGIN_LEFT, ctx.cursorY, regularFont, 14, Color.DARK_GRAY);
        ctx.cursorY -= 25;

        // Horizontal Rule
        drawLine(ctx, MARGIN_LEFT, ctx.cursorY, MARGIN_LEFT + CONTENT_WIDTH, ctx.cursorY, new Color(200, 200, 200), 1f);
        ctx.cursorY -= 20;

        // Metadata grid
        float col1 = MARGIN_LEFT;
        float col2 = MARGIN_LEFT + 140;

        drawMetadataRow(ctx, "Document Version:", "Version " + ctx.model.version(), col1, col2);
        drawMetadataRow(ctx, "Compilation Mode:", ctx.model.compileMode().name(), col1, col2);
        drawMetadataRow(ctx, "Document Status:", ctx.model.status().name(), col1, col2);
        drawMetadataRow(ctx, "Compiled At:", ctx.model.compiledAt() != null ? DATE_FMT.format(ctx.model.compiledAt()) : "N/A", col1, col2);
        drawMetadataRow(ctx, "Compiled By:", ctx.model.compiledByName() != null ? ctx.model.compiledByName() : "System", col1, col2);
        if (ctx.model.merkleRoot() != null) {
            drawMetadataRow(ctx, "Merkle Integrity Root:", ctx.model.merkleRoot(), col1, col2, monoFont, 8);
        }
        if (ctx.model.lintSummary() != null) {
            drawMetadataRow(ctx, "Compilation Status:", ctx.model.lintSummary(), col1, col2);
        }

        ctx.cursorY -= 15;
        drawLine(ctx, MARGIN_LEFT, ctx.cursorY, MARGIN_LEFT + CONTENT_WIDTH, ctx.cursorY, new Color(200, 200, 200), 1f);
        ctx.cursorY -= 30;

        // Explanatory note
        String note = "This document was compiled directly from verified transaction state in Syndicate. "
                + "Every material figure printed within this document is grounded in verified evidence, "
                + "hashed, and cryptographically anchored in the provenance manifest. Unverified values are refused.";
        renderParagraph(ctx, note, italicFont, 9.5f, 13f, Color.DARK_GRAY);
        ctx.cursorY -= 30;
    }

    private void renderTableOfContents(RenderContext ctx) throws IOException {
        ctx.ensurePage();
        drawText(ctx, "TABLE OF CONTENTS", MARGIN_LEFT, ctx.cursorY, boldFont, 13, Color.BLACK);
        ctx.cursorY -= 15;
        drawLine(ctx, MARGIN_LEFT, ctx.cursorY, MARGIN_LEFT + CONTENT_WIDTH, ctx.cursorY, new Color(150, 150, 150), 1f);
        ctx.cursorY -= 18;

        int num = 1;
        for (DocumentRenderModel.RenderSection sec : ctx.model.sections()) {
            if (ctx.cursorY < MARGIN_BOTTOM + 40) {
                ctx.newPage();
            }
            String title = num + ". " + sec.title() + " (" + sec.sectionCode() + ")";
            drawText(ctx, title, MARGIN_LEFT, ctx.cursorY, regularFont, 10, Color.BLACK);
            ctx.cursorY -= 16;
            num++;
        }

        if (ctx.cursorY < MARGIN_BOTTOM + 40) {
            ctx.newPage();
        }
        drawText(ctx, (num++) + ". Schedule of Cited Facts & Provenance", MARGIN_LEFT, ctx.cursorY, regularFont, 10, Color.BLACK);
        ctx.cursorY -= 16;
        drawText(ctx, (num) + ". Transaction Sign-Off & Approvals", MARGIN_LEFT, ctx.cursorY, regularFont, 10, Color.BLACK);
        ctx.cursorY -= 30;
    }

    private void renderSections(RenderContext ctx) throws IOException {
        int sectionIndex = 1;
        for (DocumentRenderModel.RenderSection section : ctx.model.sections()) {
            ctx.ensureSpace(60);

            // Section Header
            String headerText = "SECTION " + sectionIndex + " — " + section.title().toUpperCase();
            drawText(ctx, headerText, MARGIN_LEFT, ctx.cursorY, boldFont, 12, new Color(30, 40, 60));
            ctx.cursorY -= 12;
            drawText(ctx, "Code: " + section.sectionCode(), MARGIN_LEFT, ctx.cursorY, monoFont, 8, Color.GRAY);
            ctx.cursorY -= 12;
            drawLine(ctx, MARGIN_LEFT, ctx.cursorY, MARGIN_LEFT + CONTENT_WIDTH, ctx.cursorY, new Color(180, 180, 180), 0.75f);
            ctx.cursorY -= 16;

            // Section Prose with Citations
            renderSectionProse(ctx, section);
            ctx.cursorY -= 20;
            sectionIndex++;
        }
    }

    private void renderSectionProse(RenderContext ctx, DocumentRenderModel.RenderSection section) throws IOException {
        if (section.segments() == null || section.segments().isEmpty()) {
            if (section.renderedProse() != null && !section.renderedProse().isBlank()) {
                renderParagraph(ctx, section.renderedProse(), regularFont, 10, 14f, Color.BLACK);
            }
            return;
        }

        // Build continuous text with citations
        StringBuilder sb = new StringBuilder();
        for (DocumentRenderModel.SectionSegment seg : section.segments()) {
            if ("FACT".equalsIgnoreCase(seg.type())) {
                sb.append(seg.value() != null ? seg.value() : seg.label());
                if (seg.footnoteIndex() != null) {
                    sb.append(" [").append(seg.footnoteIndex()).append("]");
                }
            } else if ("UNRESOLVED".equalsIgnoreCase(seg.type())) {
                sb.append("[UNRESOLVED: ").append(seg.label()).append("]");
            } else {
                sb.append(seg.text() != null ? seg.text() : "");
            }
        }

        renderParagraph(ctx, sb.toString(), regularFont, 10, 14f, Color.BLACK);
    }

    private void renderProvenanceSchedule(RenderContext ctx) throws IOException {
        ctx.ensureSpace(80);
        drawText(ctx, "SCHEDULE OF CITED FACTS & PROVENANCE", MARGIN_LEFT, ctx.cursorY, boldFont, 12, new Color(30, 40, 60));
        ctx.cursorY -= 12;
        drawText(ctx, "Cryptographic audit trail of all verified assertions referenced in this DRHP", MARGIN_LEFT, ctx.cursorY, italicFont, 8.5f, Color.GRAY);
        ctx.cursorY -= 10;
        drawLine(ctx, MARGIN_LEFT, ctx.cursorY, MARGIN_LEFT + CONTENT_WIDTH, ctx.cursorY, new Color(180, 180, 180), 0.75f);
        ctx.cursorY -= 16;

        if (ctx.model.footnotes() == null || ctx.model.footnotes().isEmpty()) {
            renderParagraph(ctx, "No structured facts were cited in this compiled version.", italicFont, 9.5f, 13f, Color.GRAY);
            ctx.cursorY -= 15;
            return;
        }

        for (DocumentRenderModel.RenderFactFootnote fn : ctx.model.footnotes()) {
            ctx.ensureSpace(50);

            // Ref [N] + Label + Value
            String header = "[" + fn.index() + "] " + fn.label();
            if (fn.period() != null && !fn.period().isBlank()) {
                header += " (" + fn.period() + ")";
            }
            drawText(ctx, header, MARGIN_LEFT, ctx.cursorY, boldFont, 9.5f, new Color(20, 30, 50));
            ctx.cursorY -= 13;

            String details = "    Key: " + fn.factKey() + "  |  Value: " + fn.value() + (fn.unit() != null ? " " + fn.unit() : "");
            drawText(ctx, details, MARGIN_LEFT, ctx.cursorY, regularFont, 8.5f, Color.DARK_GRAY);
            ctx.cursorY -= 12;

            if (fn.verifiedBy() != null) {
                String verifier = "    Verified by: " + fn.verifiedBy()
                        + (fn.verifiedAt() != null ? " on " + DATE_FMT.format(fn.verifiedAt()) : "");
                drawText(ctx, verifier, MARGIN_LEFT, ctx.cursorY, regularFont, 8f, new Color(40, 120, 60));
                ctx.cursorY -= 11;
            }

            if (fn.evidence() != null && !fn.evidence().isEmpty()) {
                for (DocumentRenderModel.EvidenceSummary ev : fn.evidence()) {
                    String evText = "    Evidence: " + ev.fileName() + " [" + ev.documentType() + "] — SHA-256: "
                            + (ev.fileSha256() != null ? ev.fileSha256().substring(0, Math.min(16, ev.fileSha256().length())) + "..." : "none");
                    drawText(ctx, evText, MARGIN_LEFT, ctx.cursorY, monoFont, 7.5f, Color.GRAY);
                    ctx.cursorY -= 10;
                }
            }

            ctx.cursorY -= 6;
        }
        ctx.cursorY -= 15;
    }

    private void renderApprovalsBlock(RenderContext ctx) throws IOException {
        ctx.ensureSpace(70);
        drawText(ctx, "TRANSACTION SIGN-OFF & APPROVAL CERTIFICATE", MARGIN_LEFT, ctx.cursorY, boldFont, 12, new Color(30, 40, 60));
        ctx.cursorY -= 12;
        drawLine(ctx, MARGIN_LEFT, ctx.cursorY, MARGIN_LEFT + CONTENT_WIDTH, ctx.cursorY, new Color(180, 180, 180), 0.75f);
        ctx.cursorY -= 16;

        if (ctx.model.approvals() == null || ctx.model.approvals().isEmpty()) {
            renderParagraph(ctx, "Status: PENDING APPROVALS. This version has not yet received final executive sign-offs.", italicFont, 9.5f, 13f, new Color(170, 70, 30));
            ctx.cursorY -= 15;
            return;
        }

        for (DocumentRenderModel.RenderApproval ap : ctx.model.approvals()) {
            ctx.ensureSpace(30);
            String row = (ap.current() ? "✓ APPROVED" : "✗ INVALIDATED") + " — " + ap.role() + ": " + ap.approverName()
                    + " (" + (ap.grantedAt() != null ? DATE_FMT.format(ap.grantedAt()) : "N/A") + ")";
            Color color = ap.current() ? new Color(30, 120, 40) : new Color(180, 40, 40);
            drawText(ctx, row, MARGIN_LEFT, ctx.cursorY, boldFont, 9, color);
            ctx.cursorY -= 13;

            if (!ap.current() && ap.invalidationReason() != null) {
                drawText(ctx, "    Reason: " + ap.invalidationReason(), MARGIN_LEFT, ctx.cursorY, italicFont, 8, Color.GRAY);
                ctx.cursorY -= 11;
            }
        }
        ctx.cursorY -= 15;
    }

    private void renderParagraph(RenderContext ctx, String text, PDFont font, float fontSize, float lineHeight, Color color) throws IOException {
        List<String> lines = wrapText(text, font, fontSize, CONTENT_WIDTH);
        for (String line : lines) {
            ctx.ensureSpace(lineHeight);
            drawText(ctx, line, MARGIN_LEFT, ctx.cursorY, font, fontSize, color);
            ctx.cursorY -= lineHeight;
        }
    }

    private List<String> wrapText(String text, PDFont font, float fontSize, float maxWidth) throws IOException {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return lines;
        }

        String[] paragraphs = text.split("\n");
        for (String paragraph : paragraphs) {
            String[] words = paragraph.split(" ");
            StringBuilder line = new StringBuilder();
            for (String word : words) {
                if (word.isEmpty()) continue;
                String test = line.length() == 0 ? word : line + " " + word;
                float width = font.getStringWidth(test) / 1000f * fontSize;
                if (width <= maxWidth) {
                    line = new StringBuilder(test);
                } else {
                    if (line.length() > 0) {
                        lines.add(line.toString());
                    }
                    line = new StringBuilder(word);
                }
            }
            if (line.length() > 0) {
                lines.add(line.toString());
            }
        }
        return lines;
    }

    private void drawMetadataRow(RenderContext ctx, String label, String val, float c1, float c2) throws IOException {
        drawMetadataRow(ctx, label, val, c1, c2, regularFont, 9);
    }

    private void drawMetadataRow(RenderContext ctx, String label, String val, float c1, float c2, PDFont valFont, float valSize) throws IOException {
        drawText(ctx, label, c1, ctx.cursorY, boldFont, 9, new Color(60, 60, 60));
        drawText(ctx, val, c2, ctx.cursorY, valFont, valSize, Color.BLACK);
        ctx.cursorY -= 14;
    }

    private void drawText(RenderContext ctx, String text, float x, float y, PDFont font, float fontSize, Color color) throws IOException {
        if (text == null) return;
        ctx.stream.saveGraphicsState();
        ctx.stream.setNonStrokingColor(color);
        ctx.stream.beginText();
        ctx.stream.setFont(font, fontSize);
        ctx.stream.newLineAtOffset(x, y);
        // Sanitize string to standard Latin-1 / ASCII printable to avoid PDFBox character encoding exceptions
        String safeText = text.replaceAll("[^\\x20-\\x7E]", " ");
        ctx.stream.showText(safeText);
        ctx.stream.endText();
        ctx.stream.restoreGraphicsState();
    }

    private void drawLine(RenderContext ctx, float x1, float y1, float x2, float y2, Color color, float width) throws IOException {
        ctx.stream.saveGraphicsState();
        ctx.stream.setStrokingColor(color);
        ctx.stream.setLineWidth(width);
        ctx.stream.moveTo(x1, y1);
        ctx.stream.lineTo(x2, y2);
        ctx.stream.stroke();
        ctx.stream.restoreGraphicsState();
    }

    private void applyPageChromeAndWatermarks(RenderContext ctx) throws IOException {
        int totalPages = ctx.pages.size();
        boolean isDraft = ctx.model.compileMode() == CompileMode.DRAFT_PREVIEW;

        for (int i = 0; i < totalPages; i++) {
            PDPage page = ctx.pages.get(i);
            try (PDPageContentStream stream = new PDPageContentStream(ctx.document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                // Header (on pages after cover)
                if (i > 0) {
                    stream.saveGraphicsState();
                    stream.setNonStrokingColor(Color.GRAY);
                    stream.beginText();
                    stream.setFont(regularFont, 8);
                    stream.newLineAtOffset(MARGIN_LEFT, PAGE_HEIGHT - MARGIN_TOP + 15);
                    String headerLeft = ctx.model.companyName() + " — DRHP v" + ctx.model.version();
                    stream.showText(headerLeft.replaceAll("[^\\x20-\\x7E]", " "));
                    stream.endText();

                    stream.setStrokingColor(new Color(220, 220, 220));
                    stream.setLineWidth(0.5f);
                    stream.moveTo(MARGIN_LEFT, PAGE_HEIGHT - MARGIN_TOP + 8);
                    stream.lineTo(MARGIN_LEFT + CONTENT_WIDTH, PAGE_HEIGHT - MARGIN_TOP + 8);
                    stream.stroke();
                    stream.restoreGraphicsState();
                }

                // Footer (on all pages)
                stream.saveGraphicsState();
                stream.setStrokingColor(new Color(220, 220, 220));
                stream.setLineWidth(0.5f);
                stream.moveTo(MARGIN_LEFT, MARGIN_BOTTOM - 8);
                stream.lineTo(MARGIN_LEFT + CONTENT_WIDTH, MARGIN_BOTTOM - 8);
                stream.stroke();

                stream.setNonStrokingColor(Color.GRAY);
                stream.beginText();
                stream.setFont(regularFont, 8);
                stream.newLineAtOffset(MARGIN_LEFT, MARGIN_BOTTOM - 20);
                String footerLeft = "Syndicate Provenance: " + (ctx.model.merkleRoot() != null
                        ? ctx.model.merkleRoot().substring(0, Math.min(24, ctx.model.merkleRoot().length())) + "..."
                        : "UNANCHORED DRAFT");
                stream.showText(footerLeft);
                stream.endText();

                // Page Number
                String pageNum = "Page " + (i + 1) + " of " + totalPages;
                float numWidth = regularFont.getStringWidth(pageNum) / 1000f * 8;
                stream.beginText();
                stream.setFont(regularFont, 8);
                stream.newLineAtOffset(PAGE_WIDTH - MARGIN_RIGHT - numWidth, MARGIN_BOTTOM - 20);
                stream.showText(pageNum);
                stream.endText();
                stream.restoreGraphicsState();

                // Draft Watermark
                if (isDraft) {
                    stream.saveGraphicsState();
                    PDExtendedGraphicsState gs = new PDExtendedGraphicsState();
                    gs.setNonStrokingAlphaConstant(0.12f);
                    stream.setGraphicsStateParameters(gs);
                    stream.setNonStrokingColor(new Color(180, 30, 30));

                    stream.beginText();
                    stream.setFont(boldFont, 42);
                    // Center and rotate 45 degrees
                    Matrix matrix = Matrix.getRotateInstance(Math.toRadians(45), PAGE_WIDTH / 2f - 180, PAGE_HEIGHT / 2f - 80);
                    stream.setTextMatrix(matrix);
                    stream.showText("DRAFT — NOT FOR FILING");
                    stream.endText();
                    stream.restoreGraphicsState();
                }
            }
        }
    }

    private static class RenderContext {
        final PDDocument document;
        final DocumentRenderModel model;
        final List<PDPage> pages = new ArrayList<>();
        PDPage currentPage;
        PDPageContentStream stream;
        float cursorY;

        RenderContext(PDDocument document, DocumentRenderModel model) {
            this.document = document;
            this.model = model;
        }

        void newPage() throws IOException {
            closeCurrentStream();
            currentPage = new PDPage(PAGE_SIZE);
            document.addPage(currentPage);
            pages.add(currentPage);
            stream = new PDPageContentStream(document, currentPage);
            cursorY = PAGE_HEIGHT - MARGIN_TOP;
        }

        void ensurePage() throws IOException {
            if (currentPage == null) {
                newPage();
            }
        }

        void ensureSpace(float space) throws IOException {
            ensurePage();
            if (cursorY - space < MARGIN_BOTTOM) {
                newPage();
            }
        }

        void closeCurrentStream() throws IOException {
            if (stream != null) {
                stream.close();
                stream = null;
            }
        }
    }
}
