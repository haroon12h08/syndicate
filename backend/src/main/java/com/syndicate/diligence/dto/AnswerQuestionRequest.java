package com.syndicate.diligence.dto;

import java.util.List;
import java.util.UUID;

/** An answer is only worth recording with what it rests on, so the links come with it. */
public record AnswerQuestionRequest(String answer, List<UUID> factIds, List<UUID> evidenceIds,
                                    String notApplicableReason) {
}
