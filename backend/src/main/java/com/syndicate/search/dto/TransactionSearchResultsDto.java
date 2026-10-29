package com.syndicate.search.dto;

import java.util.List;
import java.util.Map;

public record TransactionSearchResultsDto(
        String query,
        int totalCount,
        Map<String, Integer> countsByType,
        List<SearchResultDto> results
) {}
