package com.syndicate.organization;

import com.syndicate.organization.dto.AddMemberRequest;
import com.syndicate.organization.dto.CreateOrganizationRequest;
import com.syndicate.organization.dto.MembershipDto;
import com.syndicate.organization.dto.OrganizationDto;
import com.syndicate.user.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/organizations")
public class OrganizationController {

    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @GetMapping
    public List<OrganizationDto> list(@AuthenticationPrincipal User currentUser) {
        return organizationService.listForUser(currentUser.getId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrganizationDto create(@Valid @RequestBody CreateOrganizationRequest request,
                                   @AuthenticationPrincipal User currentUser) {
        return organizationService.createOrganization(request, currentUser);
    }

    @GetMapping("/{id}")
    public OrganizationDto get(@PathVariable UUID id, @AuthenticationPrincipal User currentUser) {
        return organizationService.get(id, currentUser.getId());
    }

    @GetMapping("/{id}/members")
    public List<MembershipDto> members(@PathVariable UUID id, @AuthenticationPrincipal User currentUser) {
        return organizationService.listMembers(id, currentUser.getId());
    }

    @PostMapping("/{id}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public MembershipDto addMember(@PathVariable UUID id,
                                    @Valid @RequestBody AddMemberRequest request,
                                    @AuthenticationPrincipal User currentUser) {
        return organizationService.addMember(id, request, currentUser.getId());
    }
}
