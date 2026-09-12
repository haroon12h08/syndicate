package com.syndicate.invitation;

import com.syndicate.common.BadRequestException;
import com.syndicate.common.ForbiddenException;
import com.syndicate.common.ResourceNotFoundException;
import com.syndicate.invitation.dto.InvitationPreviewDto;
import com.syndicate.invitation.dto.OrganizationInvitationDto;
import com.syndicate.invitation.dto.SendOrganizationInvitationRequest;
import com.syndicate.invitation.dto.SendTransactionInvitationRequest;
import com.syndicate.invitation.dto.TransactionInvitationDto;
import com.syndicate.organization.OrgRole;
import com.syndicate.organization.Organization;
import com.syndicate.organization.OrganizationMembership;
import com.syndicate.organization.OrganizationMembershipRepository;
import com.syndicate.organization.OrganizationRepository;
import com.syndicate.permission.Permission;
import com.syndicate.permission.PermissionService;
import com.syndicate.transaction.Transaction;
import com.syndicate.transaction.TransactionMembership;
import com.syndicate.transaction.TransactionMembershipRepository;
import com.syndicate.transaction.TransactionRepository;
import com.syndicate.user.User;
import com.syndicate.user.UserRepository;
import com.syndicate.workstream.Workstream;
import com.syndicate.workstream.WorkstreamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class InvitationService {

    private static final long EXPIRY_DAYS = 7;

    private final OrganizationInvitationRepository organizationInvitationRepository;
    private final TransactionInvitationRepository transactionInvitationRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMembershipRepository organizationMembershipRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionMembershipRepository transactionMembershipRepository;
    private final WorkstreamRepository workstreamRepository;
    private final UserRepository userRepository;
    private final PermissionService permissionService;

    public InvitationService(OrganizationInvitationRepository organizationInvitationRepository,
                              TransactionInvitationRepository transactionInvitationRepository,
                              OrganizationRepository organizationRepository,
                              OrganizationMembershipRepository organizationMembershipRepository,
                              TransactionRepository transactionRepository,
                              TransactionMembershipRepository transactionMembershipRepository,
                              WorkstreamRepository workstreamRepository,
                              UserRepository userRepository,
                              PermissionService permissionService) {
        this.organizationInvitationRepository = organizationInvitationRepository;
        this.transactionInvitationRepository = transactionInvitationRepository;
        this.organizationRepository = organizationRepository;
        this.organizationMembershipRepository = organizationMembershipRepository;
        this.transactionRepository = transactionRepository;
        this.transactionMembershipRepository = transactionMembershipRepository;
        this.workstreamRepository = workstreamRepository;
        this.userRepository = userRepository;
        this.permissionService = permissionService;
    }

    // ---- Organization invitations ----

    @Transactional
    public OrganizationInvitationDto sendOrganizationInvitation(UUID organizationId, SendOrganizationInvitationRequest request, User caller) {
        permissionService.requireOrgPermission(organizationId, caller.getId(), Permission.ORG_INVITATION_SEND);
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found: " + organizationId));

        Optional<User> existingUser = userRepository.findByEmail(request.email());
        if (existingUser.isPresent()
                && organizationMembershipRepository.existsByOrganizationIdAndUserId(organizationId, existingUser.get().getId())) {
            throw new BadRequestException("This user is already a member of the organization");
        }

        OrganizationInvitation invitation = organizationInvitationRepository.save(new OrganizationInvitation(
                organization, caller, request.email(), request.role(), newToken(), expiryInstant()));
        return OrganizationInvitationDto.from(invitation);
    }

    public List<OrganizationInvitationDto> listOrganizationInvitations(UUID organizationId, UUID callerId) {
        permissionService.requireOrgPermission(organizationId, callerId, Permission.ORG_INVITATION_SEND);
        return organizationInvitationRepository.findByOrganizationId(organizationId).stream()
                .map(OrganizationInvitationDto::from)
                .toList();
    }

    @Transactional
    public void revokeOrganizationInvitation(UUID organizationId, UUID invitationId, UUID callerId) {
        permissionService.requireOrgPermission(organizationId, callerId, Permission.ORG_INVITATION_SEND);
        OrganizationInvitation invitation = organizationInvitationRepository.findById(invitationId)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation not found: " + invitationId));
        if (!invitation.getOrganization().getId().equals(organizationId)) {
            throw new ResourceNotFoundException("Invitation not found: " + invitationId);
        }
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new BadRequestException("Only a pending invitation can be revoked");
        }
        invitation.revoke();
    }

    // ---- Transaction invitations ----

    @Transactional
    public TransactionInvitationDto sendTransactionInvitation(UUID transactionId, SendTransactionInvitationRequest request, User caller) {
        permissionService.requireTransactionPermission(transactionId, caller.getId(), Permission.TRANSACTION_INVITATION_SEND);
        Transaction transaction = findTransaction(transactionId);
        Organization organization = organizationRepository.findById(request.targetOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found: " + request.targetOrganizationId()));

        Workstream workstream = null;
        if (request.workstreamId() != null) {
            workstream = workstreamRepository.findById(request.workstreamId())
                    .orElseThrow(() -> new ResourceNotFoundException("Workstream not found: " + request.workstreamId()));
            if (!workstream.getTransaction().getId().equals(transactionId)) {
                throw new BadRequestException("Workstream does not belong to this transaction");
            }
        }

        if (request.targetEmail() != null && !request.targetEmail().isBlank()) {
            Optional<User> existingUser = userRepository.findByEmail(request.targetEmail());
            if (existingUser.isPresent()
                    && transactionMembershipRepository.existsByTransactionIdAndUserId(transactionId, existingUser.get().getId())) {
                throw new BadRequestException("This user is already a member of the transaction");
            }
            TransactionInvitation invitation = transactionInvitationRepository.save(new TransactionInvitation(
                    transaction, caller, request.targetEmail(), organization, workstream, request.role(),
                    newToken(), expiryInstant()));
            return TransactionInvitationDto.from(invitation);
        }

        TransactionInvitation invitation = transactionInvitationRepository.save(new TransactionInvitation(
                transaction, caller, null, organization, workstream, request.role(), newToken(), expiryInstant()));
        return TransactionInvitationDto.from(invitation);
    }

    public List<TransactionInvitationDto> listTransactionInvitations(UUID transactionId, UUID callerId) {
        permissionService.requireTransactionPermission(transactionId, callerId, Permission.TRANSACTION_INVITATION_SEND);
        return transactionInvitationRepository.findByTransactionId(transactionId).stream()
                .map(TransactionInvitationDto::from)
                .toList();
    }

    @Transactional
    public void revokeTransactionInvitation(UUID transactionId, UUID invitationId, UUID callerId) {
        permissionService.requireTransactionPermission(transactionId, callerId, Permission.TRANSACTION_INVITATION_SEND);
        TransactionInvitation invitation = transactionInvitationRepository.findById(invitationId)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation not found: " + invitationId));
        if (!invitation.getTransaction().getId().equals(transactionId)) {
            throw new ResourceNotFoundException("Invitation not found: " + invitationId);
        }
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new BadRequestException("Only a pending invitation can be revoked");
        }
        invitation.revoke();
    }

    /** Used by TransactionService to let an org's lead add members before they themselves have a transaction seat. */
    public boolean hasAcceptedOrganizationInvitation(UUID transactionId, UUID organizationId) {
        return transactionInvitationRepository.findByOrganizationIdAndStatus(organizationId, InvitationStatus.ACCEPTED).stream()
                .anyMatch(inv -> inv.isOrganizationInvite() && inv.getTransaction().getId().equals(transactionId));
    }

    // ---- Token-based flows (preview / accept / reject) ----

    public InvitationPreviewDto preview(String token) {
        Optional<OrganizationInvitation> orgInvite = organizationInvitationRepository.findByToken(token);
        if (orgInvite.isPresent()) {
            OrganizationInvitation inv = orgInvite.get();
            return new InvitationPreviewDto("ORGANIZATION", inv.getOrganization().getName(), null, null,
                    inv.getRole().name(), inv.getInviterUser().getFullName(), inv.getStatus(), inv.getExpiresAt(), inv.isExpired());
        }
        TransactionInvitation txnInvite = transactionInvitationRepository.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation not found"));
        return new InvitationPreviewDto(
                txnInvite.isOrganizationInvite() ? "TRANSACTION_ORGANIZATION" : "TRANSACTION_INDIVIDUAL",
                txnInvite.getOrganization().getName(),
                txnInvite.getTransaction().getName(),
                txnInvite.getWorkstream() != null ? txnInvite.getWorkstream().getType().name() : null,
                txnInvite.getRole().name(),
                txnInvite.getInviterUser().getFullName(),
                txnInvite.getStatus(),
                txnInvite.getExpiresAt(),
                txnInvite.isExpired());
    }

    @Transactional
    public void acceptByToken(String token, User caller) {
        Optional<OrganizationInvitation> orgInvite = organizationInvitationRepository.findByToken(token);
        if (orgInvite.isPresent()) {
            acceptOrganizationInvitation(orgInvite.get(), caller);
            return;
        }
        TransactionInvitation txnInvite = transactionInvitationRepository.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation not found"));
        acceptTransactionInvitation(txnInvite, caller);
    }

    @Transactional
    public void rejectByToken(String token, User caller) {
        Optional<OrganizationInvitation> orgInvite = organizationInvitationRepository.findByToken(token);
        if (orgInvite.isPresent()) {
            requirePending(orgInvite.get());
            orgInvite.get().reject(caller);
            return;
        }
        TransactionInvitation txnInvite = transactionInvitationRepository.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation not found"));
        requirePending(txnInvite);
        txnInvite.reject(caller);
    }

    private void acceptOrganizationInvitation(OrganizationInvitation invitation, User caller) {
        requirePending(invitation);
        if (!organizationMembershipRepository.existsByOrganizationIdAndUserId(invitation.getOrganization().getId(), caller.getId())) {
            organizationMembershipRepository.save(
                    new OrganizationMembership(invitation.getOrganization(), caller, invitation.getRole()));
        }
        invitation.accept(caller);
    }

    private void acceptTransactionInvitation(TransactionInvitation invitation, User caller) {
        requirePending(invitation);

        if (invitation.isOrganizationInvite()) {
            OrganizationMembership membership = organizationMembershipRepository
                    .findByOrganizationIdAndUserId(invitation.getOrganization().getId(), caller.getId())
                    .orElseThrow(() -> new ForbiddenException(
                            "Only an OWNER or ADMIN of the invited organization can accept on its behalf"));
            if (membership.getRole() != OrgRole.OWNER && membership.getRole() != OrgRole.ADMIN) {
                throw new ForbiddenException("Only an OWNER or ADMIN of the invited organization can accept on its behalf");
            }
            invitation.accept(caller);
            return;
        }

        if (!organizationMembershipRepository.existsByOrganizationIdAndUserId(invitation.getOrganization().getId(), caller.getId())) {
            organizationMembershipRepository.save(
                    new OrganizationMembership(invitation.getOrganization(), caller, OrgRole.MEMBER));
        }
        if (!transactionMembershipRepository.existsByTransactionIdAndUserId(invitation.getTransaction().getId(), caller.getId())) {
            transactionMembershipRepository.save(new TransactionMembership(
                    invitation.getTransaction(), invitation.getOrganization(), caller, invitation.getRole()));
        }
        invitation.accept(caller);
    }

    public List<Object> listMine(User caller) {
        List<Object> mine = new ArrayList<>();
        mine.addAll(organizationInvitationRepository.findByInviteeEmailAndStatus(caller.getEmail(), InvitationStatus.PENDING).stream()
                .map(OrganizationInvitationDto::from).toList());
        mine.addAll(transactionInvitationRepository.findByInviteeEmailAndStatus(caller.getEmail(), InvitationStatus.PENDING).stream()
                .map(TransactionInvitationDto::from).toList());

        List<UUID> leadOrgIds = organizationMembershipRepository.findByUserId(caller.getId()).stream()
                .filter(m -> m.getRole() == OrgRole.OWNER || m.getRole() == OrgRole.ADMIN)
                .map(m -> m.getOrganization().getId())
                .toList();
        for (UUID orgId : leadOrgIds) {
            mine.addAll(transactionInvitationRepository.findByOrganizationIdAndStatus(orgId, InvitationStatus.PENDING).stream()
                    .filter(TransactionInvitation::isOrganizationInvite)
                    .map(TransactionInvitationDto::from)
                    .toList());
        }
        return mine;
    }

    private void requirePending(OrganizationInvitation invitation) {
        if (invitation.isExpired() && invitation.getStatus() == InvitationStatus.PENDING) {
            invitation.revoke();
        }
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new BadRequestException("This invitation is no longer pending");
        }
    }

    private void requirePending(TransactionInvitation invitation) {
        if (invitation.isExpired() && invitation.getStatus() == InvitationStatus.PENDING) {
            invitation.revoke();
        }
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new BadRequestException("This invitation is no longer pending");
        }
    }

    private Transaction findTransaction(UUID transactionId) {
        return transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + transactionId));
    }

    private String newToken() {
        return UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
    }

    private Instant expiryInstant() {
        return Instant.now().plus(EXPIRY_DAYS, ChronoUnit.DAYS);
    }
}
