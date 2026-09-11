package com.syndicate.ingestion;

import com.syndicate.candidatefact.CandidateFactSource;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class PdfLayoutParser {

    private static final int MIN_TEXT_LAYER_CHARS = 20;

    public static final class PageResult {
        public final int pageNumber;
        public final List<DocumentToken> tokens;
        public final byte[] renderedPng;
        public final int imageWidth;
        public final int imageHeight;
        public final boolean needsOcr;

        PageResult(int pageNumber, List<DocumentToken> tokens, byte[] renderedPng,
                   int imageWidth, int imageHeight, boolean needsOcr) {
            this.pageNumber = pageNumber;
            this.tokens = tokens;
            this.renderedPng = renderedPng;
            this.imageWidth = imageWidth;
            this.imageHeight = imageHeight;
            this.needsOcr = needsOcr;
        }
    }

    public List<PageResult> parse(byte[] pdfBytes) throws IOException {
        List<PageResult> results = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFRenderer renderer = new PDFRenderer(document);
            int pageCount = document.getNumberOfPages();

            for (int i = 0; i < pageCount; i++) {
                int pageNumber = i + 1;

                TokenCollectingStripper stripper = new TokenCollectingStripper();
                stripper.setStartPage(pageNumber);
                stripper.setEndPage(pageNumber);
                stripper.getText(document);

                BufferedImage image = renderer.renderImageWithDPI(i, PdfCoordinateConverter.RENDER_DPI);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ImageIO.write(image, "png", out);
                byte[] renderedPng = out.toByteArray();

                int totalChars = stripper.getTokens().stream().mapToInt(t -> t.text().length()).sum();
                boolean needsOcr = totalChars < MIN_TEXT_LAYER_CHARS;

                List<DocumentToken> pixelTokens = needsOcr ? List.of() : stripper.getTokens().stream()
                        .map(t -> new DocumentToken(
                                t.text(), pageNumber,
                                PdfCoordinateConverter.toPixelX(t.x()),
                                PdfCoordinateConverter.toPixelY(t.y()),
                                PdfCoordinateConverter.toPixelX(t.width()),
                                PdfCoordinateConverter.toPixelY(t.height()),
                                image.getWidth(), image.getHeight(), CandidateFactSource.TEXT_LAYER))
                        .toList();

                results.add(new PageResult(pageNumber, pixelTokens, renderedPng,
                        image.getWidth(), image.getHeight(), needsOcr));
            }
        }
        return results;
    }
}
