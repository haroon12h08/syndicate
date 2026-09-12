package com.syndicate.transaction;

import com.syndicate.common.BadRequestException;
import com.syndicate.common.ForbiddenException;
import com.syndicate.common.ResourceNotFoundException;
import com.syndicate.company.Company;
import com.syndicate.company.CompanyService;
import com.syndicate.invitation.InvitationService;
import com.syndicate.organization.OrgRole;
import com.syndicate.organization.Organization;
import com.syndicate.organization.OrganizationMembershipRepository;
import com.syndicate.organization.OrganizationRepository;
import com.syndicate.permission.Permission;
import com.syndicate.permission.PermissionService;
import com.syndicate.transaction.dto.AddTransactionMembershipRequest;
import com.syndicate.transaction.dto.ApprovalSignatureDto;
import com.syndicate.transaction.dto.ApprovalStatusDto;
import com.syndicate.transaction.dto.CreateTransactionRequest;
import com.syndicate.transaction.dto.SignApprovalRequest;
import com.syndicate.transaction.dto.TransactionDto;
import com.syndicate.transaction.dto.TransactionMembershipDto;
import com.syndicate.transaction.dto.UpdateTransactionRequest;
import com.syndicate.user.User;
import com.syndicate.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class TransactionService {

    /** Transitions that require sign-off from every role in PermissionService.TRANSACTION_STATUS_SIGNERS. */
    private static final Set<String> GATED_TRANSITIONS = Set.of("DRAFT_TO_ACTIVE");

    private final TransactionRepository transactionRepository;
    private final TransactionMembershipRepository membershipRepository;
    private final CompanyService companyService;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMembershipRepository organizationMembershipRepository;
    private final UserRepository userRepository;
    private final PermissionService permissionService;
    private final InvitationService invitationService;
    private final TransactionApprovalSignatureRepository approvalSignatureRepository;

    public TransactionService(TransactionRepository transactionRepository,
                               TransactionMembershipRepository membershipRepository,
                               CompanyService companyService,
                               OrganizationRepository organizationRepository,
                               OrganizationMembershipRepository organizationMembershipRepository,
                               UserRepository userRepository,
                               PermissionService permissionService,
                               InvitationService invitationService,
                               TransactionApprovalSignatureRepository approvalSignatureRepository) {
        this.transactionRepository = transactionRepository;
        this.membershipRepository = membershipRepository;
        this.companyService = companyService;
        this.organizationRepository = organizationRepository;
        this.organizationMembershipRepository = organizationMembershipRepository;
        this.userRepository = userRepository;
        this.permissionService = permissionService;
        this.invitationService = invitationService;
        this.approvalSignatureRepository = approvalSignatureRepository;
    }

    @Transactional
    public TransactionDto create(UUID companyId, CreateTransactionRequest request, User caller) {
        Company company = companyService.findCompany(companyId);
        companyService.requireVisible(company, caller.getId());

        Organization leadOrganization = organizationRepository.findById(request.leadOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found: " + request.leadOrganizationId()));
        if (!organizationMembershipRepository.existsByOrganizationIdAndUserId(leadOrganization.getId(), caller.getId())) {
            throw new ForbiddenException("You must belong to the lead organization to start a transaction for it");
        }

        Transaction transaction = transactionRepository.save(
                new Transaction(company, leadOrganization, request.type(), request.name()));
        membershipRepository.save(new TransactionMembership(transaction, leadOrganization, caller, request.creatorRole()));
        return TransactionDto.from(transaction);
    }

    public List<TransactionDto> listForCompany(UUID companyId, UUID callerId) {
        Company company = companyService.findCompany(companyId);
        companyService.requireVisible(company, callerId);
        return transactionRepository.findByCompanyId(companyId).stream().map(TransactionDto::from).toList();
    }

    public List<TransactionDto> listForUser(UUID userId) {
        return membershipRepository.findByUserId(userId).stream()
                .map(m -> TransactionDto.from(m.getTransaction()))
                .toList();
    }

    public TransactionDto get(UUID transactionId, UUID callerId) {
        Transaction transaction = findTransaction(transactionId);
        requireMembership(transactionId, callerId);
        return TransactionDto.from(transaction);
    }

    @Transactional
    public TransactionDto updateStatus(UUID transactionId, UpdateTransactionRequest request, UUID callerId) {
        Transaction transaction = findTransaction(transactionId);
        permissionService.requireTransactionPermission(transactionId, callerId, Permission.TRANSACTION_STATUS_TRANSITION);

        String transitionName = transaction.getStatus().name() + "_TO_" + request.status().name();
        if (GATED_TRANSITIONS.contains(transitionName) && !isApprovalSatisfied(transactionId, transitionName)) {
            Set<TransactionRole> missing = missingSigners(transactionId, transitionName);
            throw new BadRequestException(
                    "This transition requires sign-off from: " + missing + " before it can proceed");
        }

        transaction.setStatus(request.status());
        return TransactionDto.from(transaction);
    }

    @Transactional
    public ApprovalSignatureDto signApproval(UUID transactionId, SignApprovalRequest request, User caller) {
        findTransaction(transactionId);
        TransactionRole callerRole = permissionService.requireTransactionMembershipRole(transactionId, caller.getId());
        if (!PermissionService.TRANSACTION_STATUS_SIGNERS.contains(callerRole)) {
            throw new ForbiddenException("Your role (" + callerRole + ") is not authorized to sign this transition");
        }
        if (approvalSignatureRepository.existsByTransactionIdAndTransitionAndRequiredRole(transactionId, request.transition(), callerRole)) {
            throw new BadRequestException("You have already signed this transition in your role");
        }
        TransactionApprovalSignature signature = approvalSignatureRepository.save(
                new TransactionApprovalSignature(findTransaction(transactionId), request.transition(), callerRole, caller, request.comment()));
        return ApprovalSignatureDto.from(signature);
    }

    public ApprovalStatusDto getApprovalStatus(UUID transactionId, String transition, UUID callerId) {
        requireMembership(transactionId, callerId);
        List<ApprovalSignatureDto> signatures = approvalSignatureRepository
                .findByTransactionIdAndTransition(transactionId, transition).stream()
                .map(ApprovalSignatureDto::from)
                .toList();
        return new ApprovalStatusDto(transition, PermissionService.TRANSACTION_STATUS_SIGNERS, signatures,
                isApprovalSatisfied(transactionId, transition));
    }

    private boolean isApprovalSatisfied(UUID transactionId, String transition) {
        return missingSigners(transactionId, transition).isEmpty();
    }

    private Set<TransactionRole> missingSigners(UUID transactionId, String transition) {
        Set<TransactionRole> missing = new LinkedHashSet<>(PermissionService.TRANSACTION_STATUS_SIGNERS);
        for (TransactionRole role : PermissionService.TRANSACTION_STATUS_SIGNERS) {
            if (approvalSignatureRepository.existsByTransactionIdAndTransitionAndRequiredRole(transactionId, transition, role)) {
                missing.remove(role);
            }
        }
        return missing;
    }

    public List<TransactionMembershipDto> listMemberships(UUID transactionId, UUID callerId) {
        requireMembership(transactionId, callerId);
        return membershipRepository.findByTransactionId(transactionId).stream()
                .map(TransactionMembershipDto::from)
                .toList();
    }

    @Transactional
    public TransactionMembershipDto addMembership(UUID transactionId, AddTransactionMembershipRequest request, UUID callerId) {
        Transaction transaction = findTransaction(transactionId);
        requireCanManageMembership(transactionId, request.organizationId(), callerId);

        Organization organization = organizationRepository.findById(request.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found: " + request.organizationId()));
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BadRequestException("No registered user with email " + request.email()));
        if (!organizationMembershipRepository.existsByOrganizationIdAndUserId(organization.getId(), user.getId())) {
            throw new BadRequestException("User does not belong to the given organization");
        }
        if (membershipRepository.existsByTransactionIdAndUserId(transactionId, user.getId())) {
            throw new BadRequestException("User is already a member of this transaction");
        }
        TransactionMembership membership = membershipRepository.save(
                new TransactionMembership(transaction, organization, user, request.role()));
        return TransactionMembershipDto.from(membership);
    }

    @Transactional
    public void removeMembership(UUID transactionId, UUID membershipId, UUID callerId) {
        TransactionMembership membership = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new ResourceNotFoundException("Membership not found: " + membershipId));
        if (!membership.getTransaction().getId().equals(transactionId)) {
            throw new ResourceNotFoundException("Membership not found: " + membershipId);
        }
        permissionService.requireTransactionPermission(transactionId, callerId, Permission.TRANSACTION_MEMBERSHIP_MANAGE);
        membershipRepository.delete(membership);
    }

    /**
     * Membership management is allowed for an existing member with the right permission,
     * OR for an OWNER/ADMIN of an organization whose organization-level invitation to this
     * transaction has already been accepted (they don't have a transaction seat themselves yet,
     * but they're authorized to place their own org's people).
     */
    private void requireCanManageMembership(UUID transactionId, UUID targetOrganizationId, UUID callerId) {
        if (membershipRepository.existsByTransactionIdAndUserId(transactionId, callerId)) {
            permissionService.requireTransactionPermission(transactionId, callerId, Permission.TRANSACTION_MEMBERSHIP_MANAGE);
            return;
        }
        boolean orgHasAcceptedInvite = invitationService.hasAcceptedOrganizationInvitation(transactionId, targetOrganizationId);
        boolean callerIsOrgLead = organizationMembershipRepository.findByOrganizationIdAndUserId(targetOrganizationId, callerId)
                .map(m -> m.getRole() == OrgRole.OWNER || m.getRole() == OrgRole.ADMIN)
                .orElse(false);
        if (!orgHasAcceptedInvite || !callerIsOrgLead) {
            throw new ForbiddenException("You are not authorized to add members to this transaction");
        }
    }

    public void requireMembership(UUID transactionId, UUID userId) {
        if (!membershipRepository.existsByTransactionIdAndUserId(transactionId, userId)) {
            throw new ForbiddenException("You are not a member of this transaction");
        }
    }

    public Transaction findTransaction(UUID transactionId) {
        return transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + transactionId));
    }
}
