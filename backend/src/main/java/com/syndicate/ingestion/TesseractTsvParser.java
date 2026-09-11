package com.syndicate.ingestion;

import com.syndicate.candidatefact.CandidateFactSource;

import java.util.ArrayList;
import java.util.List;

public final class TesseractTsvParser {

    private static final int MIN_CONFIDENCE = 30;
    private static final int WORD_LEVEL = 5;

    private TesseractTsvParser() {
    }

    public static List<DocumentToken> parse(String tsv, int pageNumber, int imageWidth, int imageHeight) {
        List<DocumentToken> tokens = new ArrayList<>();
        String[] lines = tsv.split("\n", -1);

        for (int i = 1; i < lines.length; i++) { // skip header row
            String line = lines[i].strip();
            if (line.isEmpty()) {
                continue;
            }
            String[] cols = line.split("\t", -1);
            if (cols.length < 12) {
                continue;
            }
            int level = Integer.parseInt(cols[0].trim());
            if (level != WORD_LEVEL) {
                continue;
            }
            String text = cols[11];
            if (text.isBlank()) {
                continue;
            }
            double conf = Double.parseDouble(cols[10].trim());
            if (conf < MIN_CONFIDENCE) {
                continue;
            }
            double left = Double.parseDouble(cols[6].trim());
            double top = Double.parseDouble(cols[7].trim());
            double width = Double.parseDouble(cols[8].trim());
            double height = Double.parseDouble(cols[9].trim());
            tokens.add(new DocumentToken(text, pageNumber, left, top, width, height,
                    imageWidth, imageHeight, CandidateFactSource.OCR));
        }
        return tokens;
    }
}
