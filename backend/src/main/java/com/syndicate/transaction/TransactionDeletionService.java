package com.syndicate.transaction;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Hard-deletes a transaction and everything derived from it.
 *
 * <p>Kept separate from {@link TransactionService} so the delete order lives in one readable
 * place, and so pulling in every downstream repository does not create a dependency cycle with
 * the readiness, task and DRHP services that already depend on TransactionService.
 *
 * <p>Order matters: children before parents, and facts need their self-referencing supersede
 * chain broken first or the FK blocks the delete.
 */
@Service
public class TransactionDeletionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionDeletionService.class);

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public void deleteTransaction(UUID transactionId) {
        List<String> statements = List.of(
                // Task engine and notifications
                "DELETE FROM notifications WHERE transaction_id = :id "
                        + "OR task_id IN (SELECT id FROM transaction_tasks WHERE transaction_id = :id)",
                "DELETE FROM transaction_tasks WHERE transaction_id = :id",

                // Readiness engine
                "DELETE FROM rule_evaluation_fact_link WHERE rule_evaluation_id IN "
                        + "(SELECT id FROM rule_evaluations WHERE transaction_id = :id)",
                "DELETE FROM rule_evaluations WHERE transaction_id = :id",

                // DRHP and disclosures
                "DELETE FROM drhp_fact_link WHERE drhp_document_id IN "
                        + "(SELECT id FROM drhp_documents WHERE transaction_id = :id)",
                "DELETE FROM drhp_documents WHERE transaction_id = :id",
                "DELETE FROM disclosure_fact_link WHERE disclosure_id IN "
                        + "(SELECT id FROM disclosures WHERE transaction_id = :id)",
                "DELETE FROM disclosures WHERE transaction_id = :id",

                // Governance
                "DELETE FROM transaction_approval_signatures WHERE transaction_id = :id",
                "DELETE FROM transaction_invitations WHERE transaction_id = :id",

                // Issues raised inside the transaction's workstreams
                "DELETE FROM issue_fact_link WHERE issue_id IN (SELECT i.id FROM issues i "
                        + "JOIN workstreams w ON i.workstream_id = w.id WHERE w.transaction_id = :id)",
                "DELETE FROM issue_evidence_link WHERE issue_id IN (SELECT i.id FROM issues i "
                        + "JOIN workstreams w ON i.workstream_id = w.id WHERE w.transaction_id = :id)",
                "DELETE FROM issues WHERE workstream_id IN "
                        + "(SELECT id FROM workstreams WHERE transaction_id = :id)",

                // Extraction output must go before the facts it points at
                "DELETE FROM candidate_facts WHERE workstream_id IN "
                        + "(SELECT id FROM workstreams WHERE transaction_id = :id)",
                "DELETE FROM fact_evidence_link WHERE fact_id IN (SELECT f.id FROM facts f "
                        + "JOIN workstreams w ON f.workstream_id = w.id WHERE w.transaction_id = :id)",

                // Break the supersede chain before deleting the facts themselves
                "UPDATE facts SET supersedes_fact_id = NULL WHERE workstream_id IN "
                        + "(SELECT id FROM workstreams WHERE transaction_id = :id)",
                "DELETE FROM facts WHERE workstream_id IN "
                        + "(SELECT id FROM workstreams WHERE transaction_id = :id)",

                "DELETE FROM evidence WHERE workstream_id IN "
                        + "(SELECT id FROM workstreams WHERE transaction_id = :id)",
                "DELETE FROM workstreams WHERE transaction_id = :id",

                "DELETE FROM transaction_memberships WHERE transaction_id = :id",
                "DELETE FROM transactions WHERE id = :id"
        );

        for (String sql : statements) {
            entityManager.createNativeQuery(sql)
                    .setParameter("id", transactionId)
                    .executeUpdate();
        }
        log.info("Deleted transaction {} and all derived records", transactionId);
    }
}
