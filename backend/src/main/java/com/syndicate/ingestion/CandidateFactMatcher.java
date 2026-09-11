package com.syndicate.ingestion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class CandidateFactMatcher {

    private static final List<String> LABELS = List.of(
            "Total Revenue", "Revenue", "Turnover", "Net Profit", "PAT",
            "EBITDA", "Total Assets", "Total Liabilities", "Share Capital"
    );

    private static final Pattern NUMBER_PATTERN =
            Pattern.compile("(?:₹|Rs\\.?|INR)?\\s*\\d[\\d,]*\\.?\\d*\\s*(?:Cr|Crore|Lakh|Lakhs)?",
                    Pattern.CASE_INSENSITIVE);

    private static final Pattern PERIOD_PATTERN =
            Pattern.compile("FY\\s?\\d{4}|\\d{4}-\\d{2}", Pattern.CASE_INSENSITIVE);

    private static final double LINE_TOLERANCE = 5.0;

    public List<MatchedCandidate> match(List<DocumentToken> tokens) {
        List<MatchedCandidate> results = new ArrayList<>();
        if (tokens.isEmpty()) {
            return results;
        }

        var tokensByPage = tokens.stream().collect(Collectors.groupingBy(DocumentToken::pageNumber));

        for (var entry : tokensByPage.entrySet()) {
            String period = findPeriod(entry.getValue());
            List<List<DocumentToken>> lines = groupIntoLines(entry.getValue());

            for (int lineIdx = 0; lineIdx < lines.size(); lineIdx++) {
                List<DocumentToken> line = lines.get(lineIdx);
                String lineText = line.stream().map(DocumentToken::text).collect(Collectors.joining(" "));

                for (String label : LABELS) {
                    if (!containsLabel(lineText, label)) {
                        continue;
                    }
                    DocumentToken valueToken = findNumberToken(line);
                    if (valueToken == null && lineIdx - 1 >= 0) {
                        valueToken = findNumberToken(lines.get(lineIdx - 1));
                    }
                    if (valueToken == null && lineIdx + 1 < lines.size()) {
                        valueToken = findNumberToken(lines.get(lineIdx + 1));
                    }
                    if (valueToken != null) {
                        results.add(new MatchedCandidate(
                                label, valueToken.text(), period, valueToken.pageNumber(),
                                valueToken.x(), valueToken.y(), valueToken.width(), valueToken.height(),
                                valueToken.pageImageWidth(), valueToken.pageImageHeight(), valueToken.source()));
                    }
                    break;
                }
            }
        }
        return results;
    }

    private boolean containsLabel(String lineText, String label) {
        return lineText.toLowerCase().contains(label.toLowerCase());
    }

    private DocumentToken findNumberToken(List<DocumentToken> line) {
        for (DocumentToken token : line) {
            Matcher m = NUMBER_PATTERN.matcher(token.text());
            if (m.matches() && token.text().chars().anyMatch(Character::isDigit)) {
                return token;
            }
        }
        return null;
    }

    private String findPeriod(List<DocumentToken> pageTokens) {
        for (DocumentToken token : pageTokens) {
            Matcher m = PERIOD_PATTERN.matcher(token.text());
            if (m.find()) {
                return m.group();
            }
        }
        return null;
    }

    private List<List<DocumentToken>> groupIntoLines(List<DocumentToken> pageTokens) {
        List<DocumentToken> sorted = pageTokens.stream()
                .sorted(Comparator.comparingDouble(DocumentToken::y).thenComparingDouble(DocumentToken::x))
                .toList();

        List<List<DocumentToken>> lines = new ArrayList<>();
        List<DocumentToken> current = new ArrayList<>();
        double currentLineY = 0;
        for (DocumentToken token : sorted) {
            if (current.isEmpty()) {
                current.add(token);
                currentLineY = token.y();
            } else if (Math.abs(token.y() - currentLineY) <= LINE_TOLERANCE) {
                current.add(token);
            } else {
                lines.add(current);
                current = new ArrayList<>();
                current.add(token);
                currentLineY = token.y();
            }
        }
        if (!current.isEmpty()) {
            lines.add(current);
        }
        return lines;
    }
}
