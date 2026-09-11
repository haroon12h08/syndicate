package com.syndicate.ingestion;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TesseractOcrRunnerTest {

    private final TesseractOcrRunner runner = new TesseractOcrRunner();

    @Test
    void recognizesTextFromARealRenderedImage() throws Exception {
        byte[] png = renderTextImage("REVENUE 421800");

        List<DocumentToken> tokens = runner.run(png, 1, 800, 200);

        assertThat(tokens).isNotEmpty();
        String allText = tokens.stream().map(DocumentToken::text)
                .reduce("", (a, b) -> a + " " + b).toUpperCase();
        assertThat(allText).contains("REVENUE");
        tokens.forEach(t -> {
            assertThat(t.pageNumber()).isEqualTo(1);
            assertThat(t.pageImageWidth()).isEqualTo(800);
            assertThat(t.pageImageHeight()).isEqualTo(200);
        });
    }

    private byte[] renderTextImage(String text) throws Exception {
        BufferedImage image = new BufferedImage(800, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 800, 200);
        g.setColor(Color.BLACK);
        g.setFont(new Font("SansSerif", Font.BOLD, 40));
        g.drawString(text, 40, 110);
        g.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
