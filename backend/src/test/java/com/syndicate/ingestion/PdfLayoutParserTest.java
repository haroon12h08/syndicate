package com.syndicate.ingestion;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PdfLayoutParserTest {

    private final PdfLayoutParser parser = new PdfLayoutParser();

    @Test
    void extractsTextLayerTokensWithCorrectVerticalOrdering() throws Exception {
        byte[] pdfBytes = buildTestPdf();

        List<PdfLayoutParser.PageResult> pages = parser.parse(pdfBytes);

        assertThat(pages).hasSize(1);
        PdfLayoutParser.PageResult page = pages.get(0);
        assertThat(page.needsOcr).isFalse();
        assertThat(page.renderedPng).isNotEmpty();
        assertThat(page.imageWidth).isGreaterThan(0);
        assertThat(page.imageHeight).isGreaterThan(0);

        Optional<DocumentToken> topToken = page.tokens.stream()
                .filter(t -> t.text().contains("TOPTEXT")).findFirst();
        Optional<DocumentToken> bottomToken = page.tokens.stream()
                .filter(t -> t.text().contains("BOTTOMTEXT")).findFirst();
        assertThat(topToken).isPresent();
        assertThat(bottomToken).isPresent();

        // TOPTEXT was drawn near the top of the page (visually) and BOTTOMTEXT
        // near the bottom; in image/pixel space, top-of-page must be the smaller Y.
        assertThat(topToken.get().y()).isLessThan(bottomToken.get().y());
        assertThat(topToken.get().y()).isLessThan(page.imageHeight / 2.0);
        assertThat(bottomToken.get().y()).isGreaterThan(page.imageHeight / 2.0);

        // both tokens are within the rendered image bounds
        assertThat(topToken.get().x()).isBetween(0.0, (double) page.imageWidth);
        assertThat(topToken.get().y()).isBetween(0.0, (double) page.imageHeight);

        // 18pt text rendered at 200 DPI should have a real, non-trivial glyph
        // height (~50px) -- not the near-zero (~2.78px) height produced by the
        // baseline-only bounding-box bug this test guards against.
        assertThat(topToken.get().height()).isGreaterThan(20.0);
    }

    @Test
    void flagsPageAsNeedingOcrWhenTextLayerIsSparse() throws Exception {
        byte[] pdfBytes;
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            // no text drawn at all -> simulates a scanned page with no text layer
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            pdfBytes = out.toByteArray();
        }

        List<PdfLayoutParser.PageResult> pages = parser.parse(pdfBytes);

        assertThat(pages).hasSize(1);
        assertThat(pages.get(0).needsOcr).isTrue();
        assertThat(pages.get(0).tokens).isEmpty();
        assertThat(pages.get(0).renderedPng).isNotEmpty();
    }

    private byte[] buildTestPdf() throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER); // 612 x 792 pt
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
                stream.beginText();
                stream.setFont(font, 18);
                stream.newLineAtOffset(50, 700); // near the top (PDF y grows upward)
                stream.showText("TOPTEXT 42,18,000 FY2026");
                stream.endText();

                stream.beginText();
                stream.setFont(font, 18);
                stream.newLineAtOffset(50, 80); // near the bottom
                stream.showText("BOTTOMTEXT");
                stream.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }
}
