package com.syndicate.drhp.render;

import com.syndicate.drhp.CompileMode;
import com.syndicate.drhp.DrhpStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DocumentRenderModel(
        UUID documentId,
        UUID transactionId,
        String companyName,
        String transactionName,
        int version,
        DrhpStatus status,
        CompileMode compileMode,
        String merkleRoot,
        Instant compiledAt,
        String compiledByName,
        String lintSummary,
        List<RenderSection> sections,
        List<RenderFactFootnote> footnotes,
        List<RenderApproval> approvals
) {
    public record RenderSection(
            UUID disclosureId,
            String sectionCode,
            String title,
            String renderedProse,
            List<SectionSegment> segments
    ) {}

    public record SectionSegment(
            String type,
            String text,
            Integer footnoteIndex,
            String value,
            String label
    ) {}

    public record RenderFactFootnote(
            int index,
            UUID factId,
            String factKey,
            String label,
            String value,
            String unit,
            String period,
            String verifiedBy,
            Instant verifiedAt,
            List<EvidenceSummary> evidence
    ) {}

    public record EvidenceSummary(
            UUID evidenceId,
            String fileName,
            String documentType,
            String fileSha256
    ) {}

    public record RenderApproval(
            String approverName,
            String role,
            Instant grantedAt,
            boolean current,
            String invalidationReason
    ) {}
}
