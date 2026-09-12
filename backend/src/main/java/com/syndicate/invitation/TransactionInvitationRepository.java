package com.syndicate.invitation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionInvitationRepository extends JpaRepository<TransactionInvitation, UUID> {
    Optional<TransactionInvitation> findByToken(String token);

    List<TransactionInvitation> findByTransactionId(UUID transactionId);

    List<TransactionInvitation> findByInviteeEmailAndStatus(String inviteeEmail, InvitationStatus status);

    List<TransactionInvitation> findByOrganizationIdAndStatus(UUID organizationId, InvitationStatus status);
}
