package com.syndicate.permission;

import com.syndicate.common.ForbiddenException;
import com.syndicate.organization.OrgRole;
import com.syndicate.organization.OrganizationMembershipRepository;
import com.syndicate.transaction.TransactionMembershipRepository;
import com.syndicate.transaction.TransactionRole;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
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
                Permission.TRANSACTION_STATUS_TRANSITION, Permission.CONFLICT_RESOLVE));
        TRANSACTION_ROLE_PERMISSIONS.put(TransactionRole.DUE_DILIGENCE_TEAM, EnumSet.of(Permission.CONFLICT_RESOLVE));
        TRANSACTION_ROLE_PERMISSIONS.put(TransactionRole.AUDITOR, EnumSet.of(
                Permission.FACT_VERIFY_FINANCIAL, Permission.CONFLICT_RESOLVE));
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
            throw new ForbiddenException(describe(permission) + " Your role on this transaction is "
                    + humanise(role) + ".");
        }
    }

    /** Who can do this, in the words a transaction team uses. A refusal should name the way forward. */
    private static String describe(Permission permission) {
        String who = rolesWith(permission).stream()
                .map(PermissionService::humanise)
                .reduce((a, b) -> a + " or " + b)
                .orElse("nobody on this transaction");
        return switch (permission) {
            case FACT_VERIFY_FINANCIAL -> "Verifying a financial fact is for " + who + ".";
            case CONFLICT_RESOLVE -> "Choosing between conflicting values is for " + who + ".";
            case TRANSACTION_MEMBERSHIP_MANAGE -> "Adding people to this transaction is for " + who + ".";
            case TRANSACTION_INVITATION_SEND -> "Inviting organizations is for " + who + ".";
            case TRANSACTION_STATUS_TRANSITION -> "Moving this transaction forward is for " + who + ".";
            case ISSUE_RESOLVE_LITIGATION -> "Resolving a litigation issue is for " + who + ".";
            default -> "This action is for " + who + ".";
        };
    }

    public static List<TransactionRole> rolesWith(Permission permission) {
        return TRANSACTION_ROLE_PERMISSIONS.entrySet().stream()
                .filter(e -> e.getValue().contains(permission))
                .map(Map.Entry::getKey)
                .toList();
    }

    private static String humanise(TransactionRole role) {
        String[] words = role.name().toLowerCase(java.util.Locale.ROOT).split("_");
        StringBuilder text = new StringBuilder();
        for (String word : words) {
            text.append(text.isEmpty() ? "" : " ")
                .append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return text.toString();
    }
}
