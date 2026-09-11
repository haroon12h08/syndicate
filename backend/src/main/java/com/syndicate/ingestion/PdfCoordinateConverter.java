package com.syndicate.ingestion;

public final class PdfCoordinateConverter {

    public static final int RENDER_DPI = 200;
    private static final double POINTS_PER_INCH = 72.0;
    private static final double SCALE = RENDER_DPI / POINTS_PER_INCH;

    private PdfCoordinateConverter() {
    }

    public static double toPixelX(double pointValue) {
        return pointValue * SCALE;
    }

    public static double toPixelY(double pointValue) {
        return pointValue * SCALE;
    }
}
