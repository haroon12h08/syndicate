package com.syndicate.ingestion;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PdfCoordinateConverterTest {

    @Test
    void scalesPointsToTwoHundredDpiPixels() {
        // 1 inch = 72 points = 200 pixels at 200 DPI
        assertThat(PdfCoordinateConverter.toPixelX(72.0)).isCloseTo(200.0, within(0.01));
        assertThat(PdfCoordinateConverter.toPixelY(72.0)).isCloseTo(200.0, within(0.01));
    }

    @Test
    void zeroStaysZero() {
        assertThat(PdfCoordinateConverter.toPixelX(0.0)).isZero();
        assertThat(PdfCoordinateConverter.toPixelY(0.0)).isZero();
    }

    @Test
    void renderDpiIsTwoHundred() {
        assertThat(PdfCoordinateConverter.RENDER_DPI).isEqualTo(200);
    }
}
