package com.syndicate.diligence.dto;

import com.syndicate.diligence.DiligenceQuestion;
import com.syndicate.diligence.DiligenceStatus;
import com.syndicate.evidence.Evidence;
import com.syndicate.fact.Fact;
import com.syndicate.issue.IssueSeverity;
import com.syndicate.user.UserDto;
import com.syndicate.workstream.WorkstreamType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record DiligenceQuestionDto(UUID id, String code, String question, WorkstreamType workstreamType,
                                   IssueSeverity severity, DiligenceStatus status, String answer,
                                   String notApplicableReason, UserDto owner, UserDto answeredBy, Instant answeredAt,
                                   LocalDate dueDate, List<UUID> supportingFactIds, List<UUID> supportingEvidenceIds) {

    public static DiligenceQuestionDto from(DiligenceQuestion q) {
        return new DiligenceQuestionDto(q.getId(), q.getCode(), q.getQuestion(), q.getWorkstreamType(),
                q.getSeverity(), q.getStatus(), q.getAnswer(), q.getNotApplicableReason(),
                q.getOwner() != null ? UserDto.from(q.getOwner()) : null,
                q.getAnsweredBy() != null ? UserDto.from(q.getAnsweredBy()) : null,
                q.getAnsweredAt(), q.getDueDate(),
                q.getSupportingFacts().stream().map(Fact::getId).sorted().toList(),
                q.getSupportingEvidence().stream().map(Evidence::getId).sorted().toList());
    }
}
