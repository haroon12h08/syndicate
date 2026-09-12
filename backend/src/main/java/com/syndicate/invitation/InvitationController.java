package com.syndicate.invitation;

import com.syndicate.invitation.dto.InvitationPreviewDto;
import com.syndicate.invitation.dto.OrganizationInvitationDto;
import com.syndicate.invitation.dto.SendOrganizationInvitationRequest;
import com.syndicate.invitation.dto.SendTransactionInvitationRequest;
import com.syndicate.invitation.dto.TransactionInvitationDto;
import com.syndicate.user.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class InvitationController {

    private final InvitationService invitationService;

    public InvitationController(InvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @PostMapping("/api/organizations/{id}/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public OrganizationInvitationDto sendOrganizationInvitation(@PathVariable UUID id,
                                                                  @Valid @RequestBody SendOrganizationInvitationRequest request,
                                                                  @AuthenticationPrincipal User currentUser) {
        return invitationService.sendOrganizationInvitation(id, request, currentUser);
    }

    @GetMapping("/api/organizations/{id}/invitations")
    public List<OrganizationInvitationDto> listOrganizationInvitations(@PathVariable UUID id,
                                                                         @AuthenticationPrincipal User currentUser) {
        return invitationService.listOrganizationInvitations(id, currentUser.getId());
    }

    @DeleteMapping("/api/organizations/{id}/invitations/{invitationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeOrganizationInvitation(@PathVariable UUID id, @PathVariable UUID invitationId,
                                              @AuthenticationPrincipal User currentUser) {
        invitationService.revokeOrganizationInvitation(id, invitationId, currentUser.getId());
    }

    @PostMapping("/api/transactions/{id}/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionInvitationDto sendTransactionInvitation(@PathVariable UUID id,
                                                                @Valid @RequestBody SendTransactionInvitationRequest request,
                                                                @AuthenticationPrincipal User currentUser) {
        return invitationService.sendTransactionInvitation(id, request, currentUser);
    }

    @GetMapping("/api/transactions/{id}/invitations")
    public List<TransactionInvitationDto> listTransactionInvitations(@PathVariable UUID id,
                                                                       @AuthenticationPrincipal User currentUser) {
        return invitationService.listTransactionInvitations(id, currentUser.getId());
    }

    @DeleteMapping("/api/transactions/{id}/invitations/{invitationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeTransactionInvitation(@PathVariable UUID id, @PathVariable UUID invitationId,
                                             @AuthenticationPrincipal User currentUser) {
        invitationService.revokeTransactionInvitation(id, invitationId, currentUser.getId());
    }

    @GetMapping("/api/invitations/mine")
    public List<Object> listMine(@AuthenticationPrincipal User currentUser) {
        return invitationService.listMine(currentUser);
    }

    @GetMapping("/api/invitations/{token}")
    public InvitationPreviewDto preview(@PathVariable String token) {
        return invitationService.preview(token);
    }

    @PostMapping("/api/invitations/{token}/accept")
    public void accept(@PathVariable String token, @AuthenticationPrincipal User currentUser) {
        invitationService.acceptByToken(token, currentUser);
    }

    @PostMapping("/api/invitations/{token}/reject")
    public void reject(@PathVariable String token, @AuthenticationPrincipal User currentUser) {
        invitationService.rejectByToken(token, currentUser);
    }
}
