package com.syndicate.ingestion;

import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

class TokenCollectingStripper extends PDFTextStripper {

    record RawToken(String text, float x, float y, float width, float height) {
    }

    private final List<RawToken> tokens = new ArrayList<>();

    TokenCollectingStripper() throws IOException {
        setSortByPosition(true);
    }

    List<RawToken> getTokens() {
        return tokens;
    }

    @Override
    protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
        StringBuilder word = new StringBuilder();
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = Float.MIN_VALUE;
        float maxY = Float.MIN_VALUE;

        for (TextPosition tp : textPositions) {
            if (tp.getUnicode() == null || tp.getUnicode().isBlank()) {
                flushWord(word, minX, minY, maxX, maxY);
                word.setLength(0);
                minX = Float.MAX_VALUE;
                minY = Float.MAX_VALUE;
                maxX = Float.MIN_VALUE;
                maxY = Float.MIN_VALUE;
                continue;
            }
            word.append(tp.getUnicode());
            minX = Math.min(minX, tp.getX());
            minY = Math.min(minY, tp.getY());
            maxX = Math.max(maxX, tp.getX() + tp.getWidth());
            maxY = Math.max(maxY, tp.getY());
        }
        flushWord(word, minX, minY, maxX, maxY);
    }

    private void flushWord(StringBuilder word, float minX, float minY, float maxX, float maxY) {
        if (word.length() > 0) {
            tokens.add(new RawToken(word.toString(), minX, minY, Math.max(maxX - minX, 1f), Math.max(maxY - minY, 1f)));
        }
    }
}
