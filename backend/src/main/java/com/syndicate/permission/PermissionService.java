package com.syndicate.permission;

import com.syndicate.common.ForbiddenException;
import com.syndicate.organization.OrgRole;
import com.syndicate.organization.OrganizationMembershipRepository;
import com.syndicate.transaction.TransactionMembershipRepository;
import com.syndicate.transaction.TransactionRole;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class PermissionService {

    private static final Map<OrgRole, Set<Permission>> ORG_ROLE_PERMISSIONS = new EnumMap<>(OrgRole.class);
    private static final Map<TransactionRole, Set<Permission>> TRANSACTION_ROLE_PERMISSIONS = new EnumMap<>(TransactionRole.class);

    static {
        ORG_ROLE_PERMISSIONS.put(OrgRole.OWNER, EnumSet.of(Permission.ORG_MEMBERSHIP_MANAGE, Permission.ORG_INVITATION_SEND));
        ORG_ROLE_PERMISSIONS.put(OrgRole.ADMIN, EnumSet.of(Permission.ORG_MEMBERSHIP_MANAGE, Permission.ORG_INVITATION_SEND));
        ORG_ROLE_PERMISSIONS.put(OrgRole.MEMBER, EnumSet.noneOf(Permission.class));

        for (TransactionRole role : TransactionRole.values()) {
            TRANSACTION_ROLE_PERMISSIONS.put(role, EnumSet.noneOf(Permission.class));
        }
        TRANSACTION_ROLE_PERMISSIONS.put(TransactionRole.ISSUER_ADMIN, EnumSet.of(
                Permission.TRANSACTION_MEMBERSHIP_MANAGE, Permission.TRANSACTION_INVITATION_SEND,
                Permission.TRANSACTION_STATUS_TRANSITION, Permission.FACT_VERIFY_FINANCIAL));
        TRANSACTION_ROLE_PERMISSIONS.put(TransactionRole.LEAD_BANKER, EnumSet.of(
                Permission.TRANSACTION_MEMBERSHIP_MANAGE, Permission.TRANSACTION_INVITATION_SEND,
                Permission.TRANSACTION_STATUS_TRANSITION));
        TRANSACTION_ROLE_PERMISSIONS.put(TransactionRole.AUDITOR, EnumSet.of(Permission.FACT_VERIFY_FINANCIAL));
        TRANSACTION_ROLE_PERMISSIONS.put(TransactionRole.LEAD_LAWYER, EnumSet.of(Permission.ISSUE_RESOLVE_LITIGATION));
        TRANSACTION_ROLE_PERMISSIONS.put(TransactionRole.LEGAL_ASSOCIATE, EnumSet.of(Permission.ISSUE_RESOLVE_LITIGATION));
    }

    /** Transaction roles whose sign-off is required for a top-level status transition (e.g. DRAFT -> ACTIVE). */
    public static final Set<TransactionRole> TRANSACTION_STATUS_SIGNERS = EnumSet.of(
            TransactionRole.ISSUER_ADMIN, TransactionRole.LEAD_BANKER);

    private final OrganizationMembershipRepository organizationMembershipRepository;
    private final TransactionMembershipRepository transactionMembershipRepository;

    public PermissionService(OrganizationMembershipRepository organizationMembershipRepository,
                              TransactionMembershipRepository transactionMembershipRepository) {
        this.organizationMembershipRepository = organizationMembershipRepository;
        this.transactionMembershipRepository = transactionMembershipRepository;
    }

    public void requireOrgPermission(UUID organizationId, UUID userId, Permission permission) {
        OrgRole role = organizationMembershipRepository.findByOrganizationIdAndUserId(organizationId, userId)
                .map(m -> m.getRole())
                .orElseThrow(() -> new ForbiddenException("You are not a member of this organization"));
        if (!ORG_ROLE_PERMISSIONS.getOrDefault(role, Set.of()).contains(permission)) {
            throw new ForbiddenException("Your role (" + role + ") does not permit this action");
        }
    }

    public TransactionRole requireTransactionMembershipRole(UUID transactionId, UUID userId) {
        return transactionMembershipRepository.findByTransactionIdAndUserId(transactionId, userId)
                .map(m -> m.getRole())
                .orElseThrow(() -> new ForbiddenException("You are not a member of this transaction"));
    }

    public void requireTransactionPermission(UUID transactionId, UUID userId, Permission permission) {
        TransactionRole role = requireTransactionMembershipRole(transactionId, userId);
        if (!TRANSACTION_ROLE_PERMISSIONS.getOrDefault(role, Set.of()).contains(permission)) {
            throw new ForbiddenException("Your role (" + role + ") does not permit this action");
        }
    }
}
