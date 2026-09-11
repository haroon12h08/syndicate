package com.syndicate.company;

import com.syndicate.common.BadRequestException;
import com.syndicate.common.ForbiddenException;
import com.syndicate.common.ResourceNotFoundException;
import com.syndicate.company.dto.CompanyDto;
import com.syndicate.company.dto.CreateCompanyRequest;
import com.syndicate.company.dto.UpdateCompanyRequest;
import com.syndicate.organization.Organization;
import com.syndicate.organization.OrgRole;
import com.syndicate.organization.OrganizationMembership;
import com.syndicate.organization.OrganizationMembershipRepository;
import com.syndicate.organization.OrganizationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMembershipRepository membershipRepository;

    public CompanyService(CompanyRepository companyRepository,
                           OrganizationRepository organizationRepository,
                           OrganizationMembershipRepository membershipRepository) {
        this.companyRepository = companyRepository;
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
    }

    public List<CompanyDto> listForUser(UUID userId) {
        List<UUID> orgIds = membershipRepository.findByUserId(userId).stream()
                .map(m -> m.getOrganization().getId())
                .toList();
        Map<UUID, Company> visible = new LinkedHashMap<>();
        companyRepository.findByOwnerOrganizationIdIn(orgIds).forEach(c -> visible.put(c.getId(), c));
        companyRepository.findVisibleViaTransactionMembership(userId).forEach(c -> visible.put(c.getId(), c));
        return visible.values().stream().map(CompanyDto::from).toList();
    }

    @Transactional
    public CompanyDto create(CreateCompanyRequest request, UUID callerId) {
        requireOwnerOrAdmin(request.ownerOrganizationId(), callerId);
        if (request.cin() != null && !request.cin().isBlank() && companyRepository.existsByCin(request.cin())) {
            throw new BadRequestException("A company with CIN " + request.cin() + " already exists");
        }
        Organization owner = organizationRepository.findById(request.ownerOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found: " + request.ownerOrganizationId()));
        Company company = new Company(
                request.legalName(), request.cin(), request.pan(), request.registeredOffice(),
                request.incorporationDate(), request.constitution(), owner);
        return CompanyDto.from(companyRepository.save(company));
    }

    public CompanyDto get(UUID companyId, UUID callerId) {
        Company company = findCompany(companyId);
        requireVisible(company, callerId);
        return CompanyDto.from(company);
    }

    @Transactional
    public CompanyDto update(UUID companyId, UpdateCompanyRequest request, UUID callerId) {
        Company company = findCompany(companyId);
        requireOwnerOrAdmin(company.getOwnerOrganization().getId(), callerId);
        company.update(request.legalName(), request.cin(), request.pan(), request.registeredOffice(),
                request.incorporationDate(), request.constitution());
        return CompanyDto.from(company);
    }

    public Company findCompany(UUID companyId) {
        return companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found: " + companyId));
    }

    public void requireVisible(Company company, UUID userId) {
        boolean ownerMember = membershipRepository.existsByOrganizationIdAndUserId(
                company.getOwnerOrganization().getId(), userId);
        if (ownerMember) {
            return;
        }
        if (companyRepository.isVisibleViaTransactionMembership(company.getId(), userId)) {
            return;
        }
        throw new ForbiddenException("You do not have access to this company");
    }

    private void requireOwnerOrAdmin(UUID organizationId, UUID userId) {
        OrganizationMembership membership = membershipRepository.findByOrganizationIdAndUserId(organizationId, userId)
                .orElseThrow(() -> new ForbiddenException("You are not a member of this organization"));
        if (membership.getRole() != OrgRole.OWNER && membership.getRole() != OrgRole.ADMIN) {
            throw new ForbiddenException("Only an OWNER or ADMIN can manage this organization's companies");
        }
    }
}
