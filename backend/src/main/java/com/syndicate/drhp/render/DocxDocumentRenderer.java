package com.syndicate.drhp.render;

import com.syndicate.drhp.CompileMode;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.TableWidthType;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Pure deterministic rendering of a DRHP document into DOCX using Apache POI.
 *
 * <p>Spec §35 & Build Plan Ticket E6:
 * - Numbered sections, inline fact citations [1], tables for facts/evidence.
 * - Cover block, approvals table, headers and footers with Merkle integrity hash.
 * - Prominent draft banner for DRAFT_PREVIEW.
 */
@Component
public class DocxDocumentRenderer {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm 'UTC'")
            .withZone(ZoneOffset.UTC);

    public byte[] render(DocumentRenderModel model) {
        try (XWPFDocument doc = new XWPFDocument()) {
            boolean isDraft = model.compileMode() == CompileMode.DRAFT_PREVIEW;

            // 1. Header & Footer
            setupHeaderFooter(doc, model);

            // 2. Draft Warning Callout (if draft)
            if (isDraft) {
                renderDraftBanner(doc);
            }

            // 3. Title & Cover Metadata
            renderCover(doc, model);

            // 4. Table of Contents
            renderTableOfContents(doc, model);

            // 5. Numbered Sections & Paragraphs
            renderSections(doc, model);

            // 6. Schedule of Cited Facts & Provenance Table
            renderProvenanceScheduleTable(doc, model);

            // 7. Sign-off & Approvals Table
            renderApprovalsTable(doc, model);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to render DRHP DOCX: " + e.getMessage(), e);
        }
    }

    private void setupHeaderFooter(XWPFDocument doc, DocumentRenderModel model) {
        // Header
        XWPFHeader header = doc.createHeader(org.apache.poi.wp.usermodel.HeaderFooterType.DEFAULT);
        XWPFParagraph headerPara = header.createParagraph();
        headerPara.setAlignment(ParagraphAlignment.RIGHT);
        XWPFRun headerRun = headerPara.createRun();
        headerRun.setFontFamily("Calibri");
        headerRun.setFontSize(8);
        headerRun.setColor("888888");
        headerRun.setText(model.companyName() + " — DRHP v" + model.version() + " (" + model.compileMode().name() + ")");

        // Footer
        XWPFFooter footer = doc.createFooter(org.apache.poi.wp.usermodel.HeaderFooterType.DEFAULT);
        XWPFParagraph footerPara = footer.createParagraph();
        footerPara.setAlignment(ParagraphAlignment.LEFT);
        XWPFRun footerRun = footerPara.createRun();
        footerRun.setFontFamily("Calibri");
        footerRun.setFontSize(8);
        footerRun.setColor("888888");
        String merkle = model.merkleRoot() != null
                ? "Merkle Root: " + model.merkleRoot().substring(0, Math.min(24, model.merkleRoot().length())) + "..."
                : "UNANCHORED DRAFT";
        footerRun.setText("Syndicate Diligence & Disclosure Control  |  " + merkle);
    }

    private void renderDraftBanner(XWPFDocument doc) {
        XWPFTable bannerTable = doc.createTable(1, 1);
        bannerTable.setWidthType(TableWidthType.PCT);
        bannerTable.setWidth("100%");
        XWPFTableCell cell = bannerTable.getRow(0).getCell(0);
        cell.setColor("FDE8E8"); // soft red background

        XWPFParagraph p = cell.getParagraphs().get(0);
        p.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun r = p.createRun();
        r.setFontFamily("Calibri");
        r.setBold(true);
        r.setFontSize(11);
        r.setColor("9B1C1C"); // dark red text
        r.setText("DRAFT OFFER DOCUMENT — FOR TRANSACTION TEAM & DILIGENCE REVIEW ONLY");

        addEmptyParagraph(doc);
    }

    private void renderCover(XWPFDocument doc, DocumentRenderModel model) {
        // Document Title
        XWPFParagraph titlePara = doc.createParagraph();
        XWPFRun titleRun = titlePara.createRun();
        titleRun.setFontFamily("Calibri");
        titleRun.setBold(true);
        titleRun.setFontSize(22);
        titleRun.setColor("1E293B");
        titleRun.setText(model.companyName());

        XWPFParagraph subPara = doc.createParagraph();
        XWPFRun subRun = subPara.createRun();
        subRun.setFontFamily("Calibri");
        subRun.setFontSize(14);
        subRun.setColor("475569");
        subRun.setText(model.transactionName());

        addEmptyParagraph(doc);

        // Metadata Table
        XWPFTable metaTable = doc.createTable();
        metaTable.setWidthType(TableWidthType.PCT);
        metaTable.setWidth("100%");

        addMetaRow(metaTable, "Document Version", "Version " + model.version());
        addMetaRow(metaTable, "Compilation Mode", model.compileMode().name());
        addMetaRow(metaTable, "Document Status", model.status().name());
        addMetaRow(metaTable, "Compiled At", model.compiledAt() != null ? DATE_FMT.format(model.compiledAt()) : "N/A");
        addMetaRow(metaTable, "Compiled By", model.compiledByName() != null ? model.compiledByName() : "System");
        if (model.merkleRoot() != null) {
            addMetaRow(metaTable, "Merkle Root", model.merkleRoot());
        }
        if (model.lintSummary() != null) {
            addMetaRow(metaTable, "Verification Summary", model.lintSummary());
        }

        addEmptyParagraph(doc);

        XWPFParagraph notePara = doc.createParagraph();
        XWPFRun noteRun = notePara.createRun();
        noteRun.setFontFamily("Calibri");
        noteRun.setItalic(true);
        noteRun.setFontSize(9.5);
        noteRun.setColor("64748B");
        noteRun.setText("Every material figure printed within this document is grounded in verified evidence, "
                + "hashed, and cryptographically anchored in the provenance manifest. Unverified values are refused.");

        addEmptyParagraph(doc);
    }

    private void renderTableOfContents(XWPFDocument doc, DocumentRenderModel model) {
        XWPFParagraph tocHeading = doc.createParagraph();
        XWPFRun tocHeadRun = tocHeading.createRun();
        tocHeadRun.setFontFamily("Calibri");
        tocHeadRun.setBold(true);
        tocHeadRun.setFontSize(13);
        tocHeadRun.setColor("0F172A");
        tocHeadRun.setText("Table of Contents");

        int num = 1;
        for (DocumentRenderModel.RenderSection sec : model.sections()) {
            XWPFParagraph itemPara = doc.createParagraph();
            itemPara.setSpacingAfter(40);
            XWPFRun r = itemPara.createRun();
            r.setFontFamily("Calibri");
            r.setFontSize(10);
            r.setText(num + ". " + sec.title() + " (" + sec.sectionCode() + ")");
            num++;
        }

        XWPFParagraph pProv = doc.createParagraph();
        XWPFRun rProv = pProv.createRun();
        rProv.setFontFamily("Calibri");
        rProv.setFontSize(10);
        rProv.setText((num++) + ". Schedule of Cited Facts & Evidential Provenance");

        XWPFParagraph pApp = doc.createParagraph();
        XWPFRun rApp = pApp.createRun();
        rApp.setFontFamily("Calibri");
        rApp.setFontSize(10);
        rApp.setText(num + ". Transaction Sign-Off & Approvals");

        addEmptyParagraph(doc);
    }

    private void renderSections(XWPFDocument doc, DocumentRenderModel model) {
        int index = 1;
        for (DocumentRenderModel.RenderSection section : model.sections()) {
            XWPFParagraph h = doc.createParagraph();
            h.setSpacingBefore(180);
            XWPFRun hr = h.createRun();
            hr.setFontFamily("Calibri");
            hr.setBold(true);
            hr.setFontSize(12);
            hr.setColor("1E3A8A"); // navy
            hr.setText("Section " + index + " — " + section.title());

            XWPFParagraph sub = doc.createParagraph();
            sub.setSpacingAfter(60);
            XWPFRun subRun = sub.createRun();
            subRun.setFontFamily("Consolas");
            subRun.setFontSize(8.5);
            subRun.setColor("64748B");
            subRun.setText("Section Code: " + section.sectionCode());

            // Prose with citations
            XWPFParagraph body = doc.createParagraph();
            body.setSpacingAfter(120);

            if (section.segments() != null && !section.segments().isEmpty()) {
                for (DocumentRenderModel.SectionSegment seg : section.segments()) {
                    if ("FACT".equalsIgnoreCase(seg.type())) {
                        XWPFRun fVal = body.createRun();
                        fVal.setFontFamily("Calibri");
                        fVal.setBold(true);
                        fVal.setFontSize(10);
                        fVal.setText(seg.value() != null ? seg.value() : seg.label());

                        if (seg.footnoteIndex() != null) {
                            XWPFRun fRef = body.createRun();
                            fRef.setFontFamily("Calibri");
                            fRef.setBold(true);
                            fRef.setFontSize(8);
                            fRef.setColor("1D4ED8");
                            fRef.setText(" [" + seg.footnoteIndex() + "]");
                        }
                    } else if ("UNRESOLVED".equalsIgnoreCase(seg.type())) {
                        XWPFRun uRun = body.createRun();
                        uRun.setFontFamily("Calibri");
                        uRun.setBold(true);
                        uRun.setFontSize(10);
                        uRun.setColor("DC2626");
                        uRun.setText("[UNRESOLVED: " + seg.label() + "]");
                    } else {
                        XWPFRun tRun = body.createRun();
                        tRun.setFontFamily("Calibri");
                        tRun.setFontSize(10);
                        tRun.setText(seg.text() != null ? seg.text() : "");
                    }
                }
            } else if (section.renderedProse() != null) {
                XWPFRun tRun = body.createRun();
                tRun.setFontFamily("Calibri");
                tRun.setFontSize(10);
                tRun.setText(section.renderedProse());
            }

            index++;
        }
    }

    private void renderProvenanceScheduleTable(XWPFDocument doc, DocumentRenderModel model) {
        XWPFParagraph h = doc.createParagraph();
        h.setSpacingBefore(240);
        XWPFRun hr = h.createRun();
        hr.setFontFamily("Calibri");
        hr.setBold(true);
        hr.setFontSize(12);
        hr.setColor("1E3A8A");
        hr.setText("Schedule of Cited Facts & Provenance");

        XWPFParagraph sub = doc.createParagraph();
        sub.setSpacingAfter(60);
        XWPFRun subRun = sub.createRun();
        subRun.setFontFamily("Calibri");
        subRun.setFontSize(8.5);
        subRun.setColor("64748B");
        subRun.setText("Every assertion linked to source evidence files and cryptographic verification hashes.");

        if (model.footnotes() == null || model.footnotes().isEmpty()) {
            XWPFParagraph p = doc.createParagraph();
            XWPFRun r = p.createRun();
            r.setFontFamily("Calibri");
            r.setItalic(true);
            r.setText("No structured facts cited in this compiled version.");
            return;
        }

        XWPFTable table = doc.createTable();
        table.setWidthType(TableWidthType.PCT);
        table.setWidth("100%");

        // Table Header
        XWPFTableRow headerRow = table.getRow(0);
        setHeaderCell(headerRow.getCell(0), "Ref");
        setHeaderCell(headerRow.addNewTableCell(), "Fact Label & Key");
        setHeaderCell(headerRow.addNewTableCell(), "Value & Period");
        setHeaderCell(headerRow.addNewTableCell(), "Verified By");
        setHeaderCell(headerRow.addNewTableCell(), "Supporting Evidence");

        for (DocumentRenderModel.RenderFactFootnote fn : model.footnotes()) {
            XWPFTableRow row = table.createRow();
            setBodyCell(getOrCreateCell(row, 0), "[" + fn.index() + "]");
            setBodyCell(getOrCreateCell(row, 1), fn.label() + "\n(" + fn.factKey() + ")");
            setBodyCell(getOrCreateCell(row, 2), fn.value() + (fn.unit() != null ? " " + fn.unit() : "")
                    + (fn.period() != null ? "\n" + fn.period() : ""));
            setBodyCell(getOrCreateCell(row, 3), fn.verifiedBy() != null
                    ? fn.verifiedBy() + "\n" + (fn.verifiedAt() != null ? DATE_FMT.format(fn.verifiedAt()) : "")
                    : "Unverified");

            StringBuilder evSb = new StringBuilder();
            if (fn.evidence() != null && !fn.evidence().isEmpty()) {
                for (DocumentRenderModel.EvidenceSummary ev : fn.evidence()) {
                    if (evSb.length() > 0) evSb.append("\n");
                    evSb.append(ev.fileName()).append(" [").append(ev.documentType()).append("]");
                    if (ev.fileSha256() != null) {
                        evSb.append(" (").append(ev.fileSha256().substring(0, Math.min(10, ev.fileSha256().length()))).append("...)");
                    }
                }
            } else {
                evSb.append("None linked");
            }
            setBodyCell(getOrCreateCell(row, 4), evSb.toString());
        }

        addEmptyParagraph(doc);
    }

    private void renderApprovalsTable(XWPFDocument doc, DocumentRenderModel model) {
        XWPFParagraph h = doc.createParagraph();
        h.setSpacingBefore(180);
        XWPFRun hr = h.createRun();
        hr.setFontFamily("Calibri");
        hr.setBold(true);
        hr.setFontSize(12);
        hr.setColor("1E3A8A");
        hr.setText("Transaction Sign-Off & Approvals Certificate");

        if (model.approvals() == null || model.approvals().isEmpty()) {
            XWPFParagraph p = doc.createParagraph();
            XWPFRun r = p.createRun();
            r.setFontFamily("Calibri");
            r.setColor("C2410C"); // orange
            r.setText("Status: Awaiting required role sign-offs prior to filing.");
            return;
        }

        XWPFTable table = doc.createTable();
        table.setWidthType(TableWidthType.PCT);
        table.setWidth("100%");

        XWPFTableRow headerRow = table.getRow(0);
        setHeaderCell(headerRow.getCell(0), "Role");
        setHeaderCell(headerRow.addNewTableCell(), "Approver");
        setHeaderCell(headerRow.addNewTableCell(), "Granted At");
        setHeaderCell(headerRow.addNewTableCell(), "Status");

        for (DocumentRenderModel.RenderApproval ap : model.approvals()) {
            XWPFTableRow row = table.createRow();
            setBodyCell(getOrCreateCell(row, 0), ap.role());
            setBodyCell(getOrCreateCell(row, 1), ap.approverName());
            setBodyCell(getOrCreateCell(row, 2), ap.grantedAt() != null ? DATE_FMT.format(ap.grantedAt()) : "N/A");
            setBodyCell(getOrCreateCell(row, 3), ap.current() ? "APPROVED" : "INVALIDATED: " + ap.invalidationReason());
        }

        addEmptyParagraph(doc);
    }

    private void setHeaderCell(XWPFTableCell cell, String text) {
        cell.setColor("F1F5F9"); // light gray header
        XWPFParagraph p = cell.getParagraphs().isEmpty() ? cell.addParagraph() : cell.getParagraphs().get(0);
        p.setSpacingBefore(40);
        p.setSpacingAfter(40);
        XWPFRun r = p.createRun();
        r.setFontFamily("Calibri");
        r.setBold(true);
        r.setFontSize(9);
        r.setColor("0F172A");
        r.setText(text);
    }

    private void setBodyCell(XWPFTableCell cell, String text) {
        XWPFParagraph p = cell.getParagraphs().isEmpty() ? cell.addParagraph() : cell.getParagraphs().get(0);
        p.setSpacingBefore(30);
        p.setSpacingAfter(30);
        XWPFRun r = p.createRun();
        r.setFontFamily("Calibri");
        r.setFontSize(8.5);
        r.setColor("334155");
        String[] lines = text.split("\n");
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) r.addBreak();
            r.setText(lines[i]);
        }
    }

    private void addMetaRow(XWPFTable table, String label, String value) {
        XWPFTableRow row = (table.getRows().size() == 1 && table.getRow(0).getTableCells().size() <= 1 && table.getRow(0).getCell(0).getText().isEmpty())
                ? table.getRow(0)
                : table.createRow();
        XWPFTableCell c1 = getOrCreateCell(row, 0);
        c1.setColor("F8FAFC");
        XWPFParagraph p1 = c1.getParagraphs().isEmpty() ? c1.addParagraph() : c1.getParagraphs().get(0);
        XWPFRun r1 = p1.createRun();
        r1.setFontFamily("Calibri");
        r1.setBold(true);
        r1.setFontSize(9);
        r1.setText(label);

        XWPFTableCell c2 = getOrCreateCell(row, 1);
        XWPFParagraph p2 = c2.getParagraphs().isEmpty() ? c2.addParagraph() : c2.getParagraphs().get(0);
        XWPFRun r2 = p2.createRun();
        r2.setFontFamily("Calibri");
        r2.setFontSize(9);
        r2.setText(value);
    }

    private XWPFTableCell getOrCreateCell(XWPFTableRow row, int index) {
        while (row.getTableCells().size() <= index) {
            row.addNewTableCell();
        }
        return row.getCell(index);
    }

    private void addEmptyParagraph(XWPFDocument doc) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(60);
        p.setSpacingAfter(60);
    }
}
