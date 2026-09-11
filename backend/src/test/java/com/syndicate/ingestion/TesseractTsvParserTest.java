package com.syndicate.ingestion;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TesseractTsvParserTest {

    private static final String HEADER =
            "level\tpage_num\tblock_num\tpar_num\tline_num\tword_num\tleft\ttop\twidth\theight\tconf\ttext";

    @Test
    void parsesWordLevelRowsAboveConfidenceThreshold() {
        String tsv = String.join("\n",
                HEADER,
                "1\t1\t0\t0\t0\t0\t0\t0\t1000\t1400\t-1\t",
                "5\t1\t1\t1\t1\t1\t100\t200\t80\t20\t92.5\tRevenue",
                "5\t1\t1\t1\t1\t2\t200\t200\t90\t20\t88.0\t42,18,000"
        );

        List<DocumentToken> tokens = TesseractTsvParser.parse(tsv, 1, 1000, 1400);

        assertThat(tokens).hasSize(2);
        assertThat(tokens.get(0).text()).isEqualTo("Revenue");
        assertThat(tokens.get(0).x()).isEqualTo(100);
        assertThat(tokens.get(0).y()).isEqualTo(200);
        assertThat(tokens.get(1).text()).isEqualTo("42,18,000");
    }

    @Test
    void filtersOutLowConfidenceAndNonWordRows() {
        String tsv = String.join("\n",
                HEADER,
                "5\t1\t1\t1\t1\t1\t100\t200\t80\t20\t15.0\tnoise",
                "4\t1\t1\t1\t1\t0\t100\t200\t200\t20\t-1\t",
                "5\t1\t1\t1\t1\t2\t200\t200\t90\t20\t95.0\tGoodWord"
        );

        List<DocumentToken> tokens = TesseractTsvParser.parse(tsv, 1, 1000, 1400);

        assertThat(tokens).hasSize(1);
        assertThat(tokens.get(0).text()).isEqualTo("GoodWord");
    }

    @Test
    void returnsEmptyForHeaderOnlyInput() {
        assertThat(TesseractTsvParser.parse(HEADER, 1, 1000, 1400)).isEmpty();
    }
}
