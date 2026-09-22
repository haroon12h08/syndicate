package com.syndicate.audit;

import com.syndicate.audit.dto.AuditEventDto;
import com.syndicate.audit.dto.AuditVerificationDto;
import com.syndicate.common.CorrelationId;
import com.syndicate.transaction.TransactionService;
import com.syndicate.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Writes the append-only audit trail (spec §38). There is deliberately no update or delete path:
 * the trail records what happened and is never rewritten.
 */
@Service
public class AuditService {

    private final AuditEventRepository repository;
    private final TransactionService transactionService;

    public AuditService(AuditEventRepository repository, TransactionService transactionService) {
        this.repository = repository;
        this.transactionService = transactionService;
    }

    /**
     * Appends to the transaction's hash chain inside the caller's database transaction. A failure
     * here propagates and rolls back the operation being recorded: an unaudited change must not
     * commit (spec §33).
     */
    @Transactional
    public void record(UUID transactionId, User actor, AuditAction action, String entityType,
                        UUID entityId, String summary, String previousValue, String newValue, String reason) {
        repository.lockChain(transactionId == null ? "audit:global" : "audit:" + transactionId);
        String prevHash = (transactionId == null
                ? repository.findTopByTransactionIdIsNullAndHashIsNotNullOrderByChainSeqDesc()
                : repository.findTopByTransactionIdAndHashIsNotNullOrderByChainSeqDesc(transactionId))
                .map(AuditEvent::getHash)
                .orElse(null);
        AuditEvent event = new AuditEvent(transactionId, actor, action, entityType, entityId,
                summary, previousValue, newValue, reason);
        event.seal(CorrelationId.current(), prevHash);
        repository.saveAndFlush(event);
    }

    public void record(UUID transactionId, User actor, AuditAction action, String entityType,
                        UUID entityId, String summary) {
        record(transactionId, actor, action, entityType, entityId, summary, null, null, null);
    }

    /**
     * Recomputes every hash in the transaction's chain. Legacy rows written before chaining existed
     * are counted but cannot be verified.
     */
    @Transactional(readOnly = true)
    public AuditVerificationDto verify(UUID transactionId, UUID callerId) {
        transactionService.requireMembership(transactionId, callerId);
        List<AuditEvent> events = repository.findByTransactionIdOrderByChainSeqAsc(transactionId);
        String expectedPrev = null;
        int legacy = 0;
        int verified = 0;
        for (AuditEvent event : events) {
            if (event.getHash() == null) {
                legacy++;
                continue;
            }
            boolean linked = java.util.Objects.equals(expectedPrev, event.getPrevHash());
            if (!linked || !event.getHash().equals(event.computeHash())) {
                return new AuditVerificationDto(false, verified, legacy, event.getId(),
                        linked ? "Event content does not match its hash" : "Chain link broken before this event");
            }
            expectedPrev = event.getHash();
            verified++;
        }
        return new AuditVerificationDto(true, verified, legacy, null, null);
    }

    @Transactional(readOnly = true)
    public List<AuditEventDto> listForTransaction(UUID transactionId, UUID callerId) {
        transactionService.requireMembership(transactionId, callerId);
        return repository.findByTransactionIdOrderByOccurredAtDesc(transactionId).stream()
                .map(AuditEventDto::from)
                .toList();
    }
}
