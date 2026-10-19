package com.syndicate.drhp.render;

import com.syndicate.drhp.CompileMode;
import com.syndicate.drhp.DrhpStatus;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentRenderTest {

    private PdfDocumentRenderer pdfRenderer;
    private DocxDocumentRenderer docxRenderer;
    private DocumentRenderModel sampleModel;

    @BeforeEach
    void setUp() {
        pdfRenderer = new PdfDocumentRenderer();
        docxRenderer = new DocxDocumentRenderer();

        UUID docId = UUID.randomUUID();
        UUID txId = UUID.randomUUID();
        UUID factId1 = UUID.randomUUID();
        UUID factId2 = UUID.randomUUID();

        sampleModel = new DocumentRenderModel(
                docId,
                txId,
                "Acme Tech Solutions Private Limited",
                "Initial Public Offering (SME)",
                2,
                DrhpStatus.COMPILED,
                CompileMode.DRAFT_PREVIEW,
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                Instant.parse("2026-10-19T10:15:30Z"),
                "Amina Al-Mansoor",
                "Compiled cleanly: 2 section(s), 2 verified fact(s) cited.",
                List.of(
                        new DocumentRenderModel.RenderSection(
                                UUID.randomUUID(),
                                "CAPITAL_STRUCTURE",
                                "Capital Structure",
                                "The post-issue paid-up capital of the Company is INR 28,70,00,000 [1].",
                                List.of(
                                        new DocumentRenderModel.SectionSegment("TEXT", "The post-issue paid-up capital of the Company is ", null, null, null),
                                        new DocumentRenderModel.SectionSegment("FACT", null, 1, "INR 28,70,00,000", "Post-issue paid-up capital"),
                                        new DocumentRenderModel.SectionSegment("TEXT", ".", null, null, null)
                                )
                        ),
                        new DocumentRenderModel.RenderSection(
                                UUID.randomUUID(),
                                "FINANCIAL_INFO",
                                "Summary Financial Information",
                                "Total revenue for FY2026 was INR 82,45,00,000 [2].",
                                List.of(
                                        new DocumentRenderModel.SectionSegment("TEXT", "Total revenue for FY2026 was ", null, null, null),
                                        new DocumentRenderModel.SectionSegment("FACT", null, 2, "INR 82,45,00,000", "Revenue"),
                                        new DocumentRenderModel.SectionSegment("TEXT", ".", null, null, null)
                                )
                        )
                ),
                List.of(
                        new DocumentRenderModel.RenderFactFootnote(
                                1,
                                factId1,
                                "issue.post_issue_paid_up_capital",
                                "Post-issue paid-up capital",
                                "287000000",
                                "INR",
                                null,
                                "Tariq Auditor",
                                Instant.parse("2026-10-18T14:00:00Z"),
                                List.of(new DocumentRenderModel.EvidenceSummary(UUID.randomUUID(), "pas3_return.pdf", "REGULATORY_FILING", "a1b2c3d4e5f6"))
                        ),
                        new DocumentRenderModel.RenderFactFootnote(
                                2,
                                factId2,
                                "issuer.revenue",
                                "Revenue",
                                "824500000",
                                "INR",
                                "FY2026",
                                "Tariq Auditor",
                                Instant.parse("2026-10-18T14:30:00Z"),
                                List.of(new DocumentRenderModel.EvidenceSummary(UUID.randomUUID(), "audited_financials_fy26.pdf", "AUDITED_FINANCIALS", "f6e5d4c3b2a1"))
                        )
                ),
                List.of(
                        new DocumentRenderModel.RenderApproval("Amina Al-Mansoor", "LEAD_BANKER", Instant.parse("2026-10-19T11:00:00Z"), true, null),
                        new DocumentRenderModel.RenderApproval("Omar Issuer", "ISSUER_ADMIN", Instant.parse("2026-10-19T11:30:00Z"), true, null)
                )
        );
    }

    @Test
    void rendersPdfWithProperHeadersFootersAndContent() throws Exception {
        byte[] pdfBytes = pdfRenderer.render(sampleModel);
        assertThat(pdfBytes).isNotEmpty();
        assertThat(new String(pdfBytes, 0, 8, StandardCharsets.ISO_8859_1)).startsWith("%PDF-");

        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            assertThat(doc.getNumberOfPages()).isGreaterThanOrEqualTo(1);

            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(doc);

            assertThat(text).contains("Acme Tech Solutions Private Limited");
            assertThat(text).contains("Initial Public Offering (SME)");
            assertThat(text).contains("Version 2");
            assertThat(text).contains("Capital Structure");
            assertThat(text).contains("Summary Financial Information");
            assertThat(text).contains("INR 28,70,00,000 [1]");
            assertThat(text).contains("INR 82,45,00,000 [2]");
            assertThat(text).contains("SCHEDULE OF CITED FACTS & PROVENANCE");
            assertThat(text).contains("TRANSACTION SIGN-OFF & APPROVAL CERTIFICATE");
            assertThat(text).contains("Amina Al-Mansoor");
            assertThat(text).contains("Omar Issuer");
            assertThat(text).contains("DRAFT");
        }
    }

    @Test
    void rendersFinalFilingPdfWithoutDraftWatermark() throws Exception {
        DocumentRenderModel finalModel = new DocumentRenderModel(
                sampleModel.documentId(),
                sampleModel.transactionId(),
                sampleModel.companyName(),
                sampleModel.transactionName(),
                sampleModel.version(),
                sampleModel.status(),
                CompileMode.FINAL_FILING,
                sampleModel.merkleRoot(),
                sampleModel.compiledAt(),
                sampleModel.compiledByName(),
                sampleModel.lintSummary(),
                sampleModel.sections(),
                sampleModel.footnotes(),
                sampleModel.approvals()
        );

        byte[] pdfBytes = pdfRenderer.render(finalModel);
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(doc);
            assertThat(text).contains("CONFIDENTIAL REGULATORY FILING COPY");
            assertThat(text).doesNotContain("DRAFT - NOT FOR FILING");
        }
    }

    @Test
    void rendersDocxWithSectionsAndTables() throws Exception {
        byte[] docxBytes = docxRenderer.render(sampleModel);
        assertThat(docxBytes).isNotEmpty();
        // Zip file signature PK\x03\x04
        assertThat(docxBytes[0]).isEqualTo((byte) 0x50);
        assertThat(docxBytes[1]).isEqualTo((byte) 0x4B);

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docxBytes))) {
            List<XWPFTable> tables = doc.getTables();
            assertThat(tables).isNotEmpty();

            StringBuilder fullText = new StringBuilder();
            doc.getParagraphs().forEach(p -> fullText.append(p.getText()).append("\n"));
            tables.forEach(t -> t.getRows().forEach(r -> r.getTableCells().forEach(c -> fullText.append(c.getText()).append(" "))));

            String text = fullText.toString();
            assertThat(text).contains("Acme Tech Solutions Private Limited");
            assertThat(text).contains("Initial Public Offering (SME)");
            assertThat(text).contains("Capital Structure");
            assertThat(text).contains("Summary Financial Information");
            assertThat(text).contains("INR 28,70,00,000");
            assertThat(text).contains("Schedule of Cited Facts & Provenance");
            assertThat(text).contains("Transaction Sign-Off & Approvals Certificate");
            assertThat(text).contains("Amina Al-Mansoor");
        }
    }
}
