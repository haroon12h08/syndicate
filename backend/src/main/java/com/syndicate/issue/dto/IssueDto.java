package com.syndicate.issue.dto;

import com.syndicate.issue.Issue;
import com.syndicate.issue.IssueSeverity;
import com.syndicate.issue.IssueStatus;
import com.syndicate.user.UserDto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record IssueDto(
        UUID id,
        UUID workstreamId,
        String title,
        String description,
        IssueSeverity severity,
        IssueStatus status,
        UserDto owner,
        LocalDate dueDate,
        String resolution,
        UserDto createdBy,
        List<UUID> relatedFactIds,
        List<UUID> relatedEvidenceIds
) {
    public static IssueDto from(Issue issue) {
        return new IssueDto(
                issue.getId(),
                issue.getWorkstream().getId(),
                issue.getTitle(),
                issue.getDescription(),
                issue.getSeverity(),
                issue.getStatus(),
                issue.getOwnerUser() != null ? UserDto.from(issue.getOwnerUser()) : null,
                issue.getDueDate(),
                issue.getResolution(),
                UserDto.from(issue.getCreatedByUser()),
                issue.getRelatedFacts().stream().map(f -> f.getId()).toList(),
                issue.getRelatedEvidence().stream().map(e -> e.getId()).toList()
        );
    }
}
