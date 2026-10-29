package com.syndicate.ai.suggestion;

import java.util.List;
import java.util.UUID;

public record GenerateSuggestionsResponse(
        int generatedCount,
        int pendingCount,
        List<AiSuggestionDto> suggestions,
        UUID aiArtifactId
) {}
