package com.syndicate.invitation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationInvitationRepository extends JpaRepository<OrganizationInvitation, UUID> {
    Optional<OrganizationInvitation> findByToken(String token);

    List<OrganizationInvitation> findByOrganizationId(UUID organizationId);

    List<OrganizationInvitation> findByInviteeEmailAndStatus(String inviteeEmail, InvitationStatus status);
}
