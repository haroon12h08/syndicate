package com.syndicate.audit;

import com.syndicate.audit.dto.AuditEventDto;
import com.syndicate.transaction.TransactionService;
import com.syndicate.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditEventRepository repository;
    private final TransactionService transactionService;

    public AuditService(AuditEventRepository repository, TransactionService transactionService) {
        this.repository = repository;
        this.transactionService = transactionService;
    }

    /**
     * Recording an event must never break the operation being recorded, so failures here are
     * logged rather than propagated.
     */
    @Transactional
    public void record(UUID transactionId, User actor, AuditAction action, String entityType,
                        UUID entityId, String summary, String previousValue, String newValue, String reason) {
        try {
            repository.save(new AuditEvent(transactionId, actor, action, entityType, entityId,
                    summary, previousValue, newValue, reason));
        } catch (Exception e) {
            log.warn("Failed to record audit event {} for {} {}: {}", action, entityType, entityId, e.toString());
        }
    }

    public void record(UUID transactionId, User actor, AuditAction action, String entityType,
                        UUID entityId, String summary) {
        record(transactionId, actor, action, entityType, entityId, summary, null, null, null);
    }

    @Transactional(readOnly = true)
    public List<AuditEventDto> listForTransaction(UUID transactionId, UUID callerId) {
        transactionService.requireMembership(transactionId, callerId);
        return repository.findByTransactionIdOrderByOccurredAtDesc(transactionId).stream()
                .map(AuditEventDto::from)
                .toList();
    }
}
