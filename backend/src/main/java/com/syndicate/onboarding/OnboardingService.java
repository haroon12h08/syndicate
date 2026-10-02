package com.syndicate.onboarding;

import com.syndicate.audit.AuditAction;
import com.syndicate.audit.AuditService;
import com.syndicate.common.BadRequestException;
import com.syndicate.company.Company;
import com.syndicate.company.CompanyRepository;
import com.syndicate.organization.Organization;
import com.syndicate.organization.OrganizationMembership;
import com.syndicate.organization.OrganizationMembershipRepository;
import com.syndicate.organization.OrganizationType;
import com.syndicate.transaction.Transaction;
import com.syndicate.transaction.TransactionMembership;
import com.syndicate.transaction.TransactionMembershipRepository;
import com.syndicate.transaction.TransactionRepository;
import com.syndicate.transaction.TransactionRole;
import com.syndicate.transaction.TransactionType;
import com.syndicate.transaction.dto.TransactionDto;
import com.syndicate.user.User;
import com.syndicate.workstream.Workstream;
import com.syndicate.workstream.WorkstreamRepository;
import com.syndicate.workstream.WorkstreamType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Opens a deal from the one thing only a person can supply: whose issue it is.
 *
 * <p>The issue type, the lead organisation, the opening role and the diligence areas all follow
 * from who is asking, so the product decides them rather than interviewing the user. Each can be
 * changed afterwards, in the place where it matters.
 */
@Service
public class OnboardingService {

    /** The areas every SME issue has to cover; more can be added when a transaction needs them. */
    private static final List<WorkstreamType> STANDARD_AREAS = List.of(
            WorkstreamType.CAPITAL_STRUCTURE,
            WorkstreamType.FINANCIAL_DUE_DILIGENCE,
            WorkstreamType.LEGAL_DUE_DILIGENCE,
            WorkstreamType.REGULATORY_DUE_DILIGENCE);

    private final CompanyRepository companyRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionMembershipRepository membershipRepository;
    private final WorkstreamRepository workstreamRepository;
    private final OrganizationMembershipRepository organizationMembershipRepository;
    private final AuditService auditService;

    public OnboardingService(CompanyRepository companyRepository, TransactionRepository transactionRepository,
                             TransactionMembershipRepository membershipRepository,
                             WorkstreamRepository workstreamRepository,
                             OrganizationMembershipRepository organizationMembershipRepository,
                             AuditService auditService) {
        this.companyRepository = companyRepository;
        this.transactionRepository = transactionRepository;
        this.membershipRepository = membershipRepository;
        this.workstreamRepository = workstreamRepository;
        this.organizationMembershipRepository = organizationMembershipRepository;
        this.auditService = auditService;
    }

    @Transactional
    public TransactionDto start(StartTransactionRequest request, User caller) {
        OrganizationMembership membership = organizationMembershipRepository.findByUserId(caller.getId()).stream()
                .findFirst()
                .orElseThrow(() -> new BadRequestException("You do not belong to an organization yet"));
        Organization organization = membership.getOrganization();

        if (request.cin() != null && !request.cin().isBlank() && companyRepository.existsByCin(request.cin())) {
            throw new BadRequestException("A company with CIN " + request.cin() + " is already on Syndicate");
        }
        Company company = companyRepository.save(new Company(request.companyName().trim(),
                blankToNull(request.cin()), null, null, null, null, organization));

        Transaction transaction = transactionRepository.save(new Transaction(company, organization,
                TransactionType.SME_IPO, company.getLegalName() + " SME IPO"));
        membershipRepository.save(new TransactionMembership(transaction, organization, caller,
                openingRole(organization.getType())));
        for (WorkstreamType area : STANDARD_AREAS) {
            workstreamRepository.save(new Workstream(transaction, area, null));
        }

        auditService.record(transaction.getId(), caller, AuditAction.TRANSACTION_STATUS_CHANGED, "Transaction",
                transaction.getId(), "Opened " + transaction.getName(), null, transaction.getStatus().name(), null);
        return TransactionDto.from(transaction);
    }

    /** The issuer runs its own deal; everyone else arrives as the adviser they are. */
    private static TransactionRole openingRole(OrganizationType type) {
        return switch (type) {
            case ISSUER -> TransactionRole.ISSUER_ADMIN;
            case LEGAL_COUNSEL -> TransactionRole.LEAD_LAWYER;
            case STATUTORY_AUDITOR -> TransactionRole.AUDITOR;
            case CA_TAX_ADVISOR -> TransactionRole.TAX_ADVISOR;
            case COMPANY_SECRETARY -> TransactionRole.COMPANY_SECRETARY;
            case REGULATORY_CONSULTANT -> TransactionRole.REGULATORY_CONSULTANT;
            case MERCHANT_BANKER -> TransactionRole.LEAD_BANKER;
            default -> TransactionRole.ADVISOR;
        };
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
