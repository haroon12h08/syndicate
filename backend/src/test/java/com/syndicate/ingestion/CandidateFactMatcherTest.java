package com.syndicate.ingestion;

import com.syndicate.candidatefact.CandidateFactSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CandidateFactMatcherTest {

    private final CandidateFactMatcher matcher = new CandidateFactMatcher();

    @Test
    void matchesLabelAndNumberOnSameLine() {
        List<DocumentToken> tokens = List.of(
                token("Total", 1, 10, 100),
                token("Revenue", 1, 60, 100),
                token("42,18,000", 1, 130, 100),
                token("FY2026", 1, 10, 50)
        );

        List<MatchedCandidate> results = matcher.match(tokens);

        assertThat(results).hasSize(1);
        MatchedCandidate candidate = results.get(0);
        assertThat(candidate.label()).isEqualTo("Total Revenue");
        assertThat(candidate.value()).isEqualTo("42,18,000");
        assertThat(candidate.period()).isEqualTo("FY2026");
        assertThat(candidate.bboxX()).isEqualTo(130);
        assertThat(candidate.pageNumber()).isEqualTo(1);
    }

    @Test
    void matchesNumberOnFollowingLineWhenNotOnSameLine() {
        List<DocumentToken> tokens = List.of(
                token("EBITDA", 1, 10, 200),
                token("15,00,000", 1, 10, 180)
        );

        List<MatchedCandidate> results = matcher.match(tokens);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).label()).isEqualTo("EBITDA");
        assertThat(results.get(0).value()).isEqualTo("15,00,000");
    }

    @Test
    void returnsEmptyWhenNoKnownLabelPresent() {
        List<DocumentToken> tokens = List.of(
                token("Random", 1, 10, 100),
                token("Text", 1, 60, 100)
        );

        assertThat(matcher.match(tokens)).isEmpty();
    }

    @Test
    void returnsEmptyForEmptyInput() {
        assertThat(matcher.match(List.of())).isEmpty();
    }

    @Test
    void handlesMultiplePagesIndependently() {
        List<DocumentToken> tokens = List.of(
                token("Revenue", 1, 10, 100),
                token("100", 1, 60, 100),
                token("Revenue", 2, 10, 100),
                token("200", 2, 60, 100)
        );

        List<MatchedCandidate> results = matcher.match(tokens);

        assertThat(results).hasSize(2);
        assertThat(results).anySatisfy(c -> {
            assertThat(c.pageNumber()).isEqualTo(1);
            assertThat(c.value()).isEqualTo("100");
        });
        assertThat(results).anySatisfy(c -> {
            assertThat(c.pageNumber()).isEqualTo(2);
            assertThat(c.value()).isEqualTo("200");
        });
    }

    private DocumentToken token(String text, int page, double x, double y) {
        return new DocumentToken(text, page, x, y, 40, 12, 1000, 1400, CandidateFactSource.TEXT_LAYER);
    }
}
