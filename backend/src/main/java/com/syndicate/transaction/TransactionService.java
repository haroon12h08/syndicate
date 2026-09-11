package com.syndicate.transaction;

import com.syndicate.common.BadRequestException;
import com.syndicate.common.ForbiddenException;
import com.syndicate.common.ResourceNotFoundException;
import com.syndicate.company.Company;
import com.syndicate.company.CompanyService;
import com.syndicate.organization.Organization;
import com.syndicate.organization.OrganizationMembershipRepository;
import com.syndicate.organization.OrganizationRepository;
import com.syndicate.transaction.dto.AddTransactionMembershipRequest;
import com.syndicate.transaction.dto.CreateTransactionRequest;
import com.syndicate.transaction.dto.TransactionDto;
import com.syndicate.transaction.dto.TransactionMembershipDto;
import com.syndicate.transaction.dto.UpdateTransactionRequest;
import com.syndicate.user.User;
import com.syndicate.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final TransactionMembershipRepository membershipRepository;
    private final CompanyService companyService;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMembershipRepository organizationMembershipRepository;
    private final UserRepository userRepository;

    public TransactionService(TransactionRepository transactionRepository,
                               TransactionMembershipRepository membershipRepository,
                               CompanyService companyService,
                               OrganizationRepository organizationRepository,
                               OrganizationMembershipRepository organizationMembershipRepository,
                               UserRepository userRepository) {
        this.transactionRepository = transactionRepository;
        this.membershipRepository = membershipRepository;
        this.companyService = companyService;
        this.organizationRepository = organizationRepository;
        this.organizationMembershipRepository = organizationMembershipRepository;
        this.userRepository = userRepository;
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
        requireMembership(transactionId, callerId);
        transaction.setStatus(request.status());
        return TransactionDto.from(transaction);
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
        requireMembership(transactionId, callerId);

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
        requireMembership(transactionId, callerId);
        TransactionMembership membership = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new ResourceNotFoundException("Membership not found: " + membershipId));
        if (!membership.getTransaction().getId().equals(transactionId)) {
            throw new ResourceNotFoundException("Membership not found: " + membershipId);
        }
        membershipRepository.delete(membership);
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
