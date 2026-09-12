package com.syndicate.regulatory.dto;

import com.syndicate.issue.Issue;
import com.syndicate.issue.IssueSeverity;
import com.syndicate.issue.IssueStatus;
import com.syndicate.workstream.WorkstreamType;

import java.util.UUID;

public record BlockingIssueDto(
        UUID id,
        UUID workstreamId,
        WorkstreamType workstreamType,
        String title,
        String description,
        IssueSeverity severity,
        IssueStatus status,
        String ruleCode
) {
    public static BlockingIssueDto from(Issue issue, String ruleCode) {
        return new BlockingIssueDto(
                issue.getId(),
                issue.getWorkstream().getId(),
                issue.getWorkstream().getType(),
                issue.getTitle(),
                issue.getDescription(),
                issue.getSeverity(),
                issue.getStatus(),
                ruleCode
        );
    }
}
