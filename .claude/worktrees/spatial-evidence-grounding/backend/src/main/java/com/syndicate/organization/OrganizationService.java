package com.syndicate.organization;

import com.syndicate.common.BadRequestException;
import com.syndicate.common.ForbiddenException;
import com.syndicate.common.ResourceNotFoundException;
import com.syndicate.organization.dto.AddMemberRequest;
import com.syndicate.organization.dto.CreateOrganizationRequest;
import com.syndicate.organization.dto.MembershipDto;
import com.syndicate.organization.dto.OrganizationDto;
import com.syndicate.user.User;
import com.syndicate.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final UserRepository userRepository;

    public OrganizationService(OrganizationRepository organizationRepository,
                                OrganizationMembershipRepository membershipRepository,
                                UserRepository userRepository) {
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Organization createOrganizationWithOwner(String name, OrganizationType type, User owner) {
        Organization organization = organizationRepository.save(new Organization(name, type));
        membershipRepository.save(new OrganizationMembership(organization, owner, OrgRole.OWNER));
        return organization;
    }

    @Transactional
    public OrganizationDto createOrganization(CreateOrganizationRequest request, User creator) {
        Organization organization = createOrganizationWithOwner(request.name(), request.type(), creator);
        return OrganizationDto.from(organization);
    }

    public List<OrganizationDto> listForUser(UUID userId) {
        return membershipRepository.findByUserId(userId).stream()
                .map(m -> OrganizationDto.from(m.getOrganization()))
                .toList();
    }

    public OrganizationDto get(UUID organizationId, UUID callerId) {
        requireMembership(organizationId, callerId);
        return OrganizationDto.from(findOrganization(organizationId));
    }

    public List<MembershipDto> listMembers(UUID organizationId, UUID callerId) {
        requireMembership(organizationId, callerId);
        return membershipRepository.findByOrganizationId(organizationId).stream()
                .map(MembershipDto::from)
                .toList();
    }

    @Transactional
    public MembershipDto addMember(UUID organizationId, AddMemberRequest request, UUID callerId) {
        OrganizationMembership callerMembership = membershipRepository
                .findByOrganizationIdAndUserId(organizationId, callerId)
                .orElseThrow(() -> new ForbiddenException("You are not a member of this organization"));
        if (callerMembership.getRole() != OrgRole.OWNER && callerMembership.getRole() != OrgRole.ADMIN) {
            throw new ForbiddenException("Only an OWNER or ADMIN can add members");
        }
        Organization organization = findOrganization(organizationId);
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BadRequestException("No registered user with email " + request.email()));
        if (membershipRepository.existsByOrganizationIdAndUserId(organizationId, user.getId())) {
            throw new BadRequestException("User is already a member of this organization");
        }
        OrganizationMembership membership = membershipRepository.save(
                new OrganizationMembership(organization, user, request.role()));
        return MembershipDto.from(membership);
    }

    public void requireMembership(UUID organizationId, UUID userId) {
        if (!membershipRepository.existsByOrganizationIdAndUserId(organizationId, userId)) {
            throw new ForbiddenException("You are not a member of this organization");
        }
    }

    public Organization findOrganization(UUID organizationId) {
        return organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found: " + organizationId));
    }
}
