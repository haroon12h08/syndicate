package com.syndicate.review;

import com.syndicate.audit.AuditAction;
import com.syndicate.audit.AuditService;
import com.syndicate.common.BadRequestException;
import com.syndicate.common.ConflictException;
import com.syndicate.common.ResourceNotFoundException;
import com.syndicate.evidence.Evidence;
import com.syndicate.evidence.EvidenceRepository;
import com.syndicate.fact.Fact;
import com.syndicate.fact.FactRepository;
import com.syndicate.fact.FactStatus;
import com.syndicate.permission.PermissionService;
import com.syndicate.review.dto.CreateReviewRequest;
import com.syndicate.review.dto.MissingReviewDto;
import com.syndicate.review.dto.ReviewDto;
import com.syndicate.transaction.TransactionRole;
import com.syndicate.transaction.TransactionService;
import com.syndicate.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Records reviews against exact fact versions and reports which required reviews are missing. */
@Service
@Transactional
public class ReviewService {

    private static final Set<FactStatus> NOT_CURRENT = EnumSet.of(FactStatus.SUPERSEDED, FactStatus.REJECTED);

    private final ReviewRepository reviewRepository;
    private final FactRepository factRepository;
    private final EvidenceRepository evidenceRepository;
    private final TransactionService transactionService;
    private final PermissionService permissionService;
    private final AuditService auditService;

    public ReviewService(ReviewRepository reviewRepository, FactRepository factRepository,
                         EvidenceRepository evidenceRepository, TransactionService transactionService,
                         PermissionService permissionService, AuditService auditService) {
        this.reviewRepository = reviewRepository;
        this.factRepository = factRepository;
        this.evidenceRepository = evidenceRepository;
        this.transactionService = transactionService;
        this.permissionService = permissionService;
        this.auditService = auditService;
    }

    public ReviewDto reviewFact(UUID factId, CreateReviewRequest request, User caller) {
        Fact fact = factRepository.findById(factId)
                .orElseThrow(() -> new ResourceNotFoundException("Fact not found: " + factId));
        UUID transactionId = fact.getWorkstream().getTransaction().getId();
        TransactionRole role = permissionService.requireTransactionMembershipRole(transactionId, caller.getId());
        if (NOT_CURRENT.contains(fact.getStatus())) {
            throw new ConflictException("REVIEW_TARGET_NOT_CURRENT",
                    "This version is " + fact.getStatus() + "; review the current version instead");
        }
        if (fact.getCreatedByUser().getId().equals(caller.getId())) {
            throw new ConflictException("REVIEWER_NOT_INDEPENDENT", "You recorded this fact, so you cannot review it");
        }
        if (request.decision() != ReviewDecision.APPROVED_FOR_USE
                && (request.comments() == null || request.comments().isBlank())) {
            throw new BadRequestException("Explain what needs to change");
        }
        Set<Evidence> considered = new HashSet<>();
        if (request.evidenceConsideredIds() != null) {
            for (UUID evidenceId : request.evidenceConsideredIds()) {
                considered.add(evidenceRepository.findById(evidenceId)
                        .filter(e -> e.getWorkstream().getTransaction().getId().equals(transactionId))
                        .orElseThrow(() -> new BadRequestException("Unknown evidence " + evidenceId)));
            }
        }
        Review review = reviewRepository.save(new Review(transactionId, ReviewTargetType.FACT, fact.getId(),
                fact.getLineageId(), caller, role, request.decision(), request.comments(), considered));
        auditService.record(transactionId, caller, AuditAction.REVIEW_RECORDED, "Fact", fact.getId(),
                humanDecision(request.decision()) + " " + fact.getLabel() + " v" + fact.getVersion() + " as " + role,
                null, request.decision().name(), request.comments());
        return ReviewDto.from(review, true);
    }

    @Transactional(readOnly = true)
    public List<ReviewDto> listForFact(UUID factId, UUID callerId) {
        Fact fact = factRepository.findById(factId)
                .orElseThrow(() -> new ResourceNotFoundException("Fact not found: " + factId));
        transactionService.requireMembership(fact.getWorkstream().getTransaction().getId(), callerId);
        return reviewRepository.findByTargetTypeAndTargetLineageIdOrderByReviewedAtDesc(ReviewTargetType.FACT,
                        fact.getLineageId()).stream()
                .map(r -> ReviewDto.from(r, isCurrent(r)))
                .toList();
    }

    /** Required roles that have not yet approved the current version of each material fact. */
    @Transactional(readOnly = true)
    public List<MissingReviewDto> missing(UUID transactionId, UUID callerId) {
        transactionService.requireMembership(transactionId, callerId);
        return missingReviews(transactionId);
    }

    @Transactional(readOnly = true)
    public List<MissingReviewDto> missingReviews(UUID transactionId) {
        List<MissingReviewDto> missing = new ArrayList<>();
        for (Fact fact : factRepository.findByWorkstreamTransactionId(transactionId)) {
            if (NOT_CURRENT.contains(fact.getStatus())) {
                continue;
            }
            List<String> required = reviewRepository.requiredRoles("FACT", fact.getMateriality().name());
            if (required.isEmpty()) {
                continue;
            }
            Set<TransactionRole> approvedBy = reviewRepository
                    .findByTargetTypeAndTargetVersionId(ReviewTargetType.FACT, fact.getId()).stream()
                    .filter(r -> r.getDecision() == ReviewDecision.APPROVED_FOR_USE)
                    .map(Review::getReviewerRole)
                    .collect(Collectors.toSet());
            for (String role : required) {
                TransactionRole requiredRole = TransactionRole.valueOf(role);
                if (!approvedBy.contains(requiredRole)) {
                    missing.add(new MissingReviewDto(fact.getId(), fact.getLineageId(), fact.getFactKey(),
                            fact.getLabel(), fact.getMateriality(), requiredRole));
                }
            }
        }
        return missing;
    }

    private boolean isCurrent(Review review) {
        return factRepository.findById(review.getTargetVersionId())
                .map(f -> !NOT_CURRENT.contains(f.getStatus()))
                .orElse(false);
    }

    private static String humanDecision(ReviewDecision decision) {
        return switch (decision) {
            case APPROVED_FOR_USE -> "Approved";
            case CHANGES_REQUESTED -> "Requested changes to";
            case REJECTED -> "Rejected";
        };
    }
}
