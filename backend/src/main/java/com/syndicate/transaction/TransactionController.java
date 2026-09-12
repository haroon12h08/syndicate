package com.syndicate.transaction;

import com.syndicate.transaction.dto.AddTransactionMembershipRequest;
import com.syndicate.transaction.dto.ApprovalStatusDto;
import com.syndicate.transaction.dto.ApprovalSignatureDto;
import com.syndicate.transaction.dto.CreateTransactionRequest;
import com.syndicate.transaction.dto.SignApprovalRequest;
import com.syndicate.transaction.dto.TransactionDto;
import com.syndicate.transaction.dto.TransactionMembershipDto;
import com.syndicate.transaction.dto.UpdateTransactionRequest;
import com.syndicate.user.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping("/api/companies/{companyId}/transactions")
    public List<TransactionDto> listForCompany(@PathVariable UUID companyId, @AuthenticationPrincipal User currentUser) {
        return transactionService.listForCompany(companyId, currentUser.getId());
    }

    @PostMapping("/api/companies/{companyId}/transactions")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionDto create(@PathVariable UUID companyId,
                                  @Valid @RequestBody CreateTransactionRequest request,
                                  @AuthenticationPrincipal User currentUser) {
        return transactionService.create(companyId, request, currentUser);
    }

    @GetMapping("/api/transactions")
    public List<TransactionDto> listForUser(@AuthenticationPrincipal User currentUser) {
        return transactionService.listForUser(currentUser.getId());
    }

    @GetMapping("/api/transactions/{id}")
    public TransactionDto get(@PathVariable UUID id, @AuthenticationPrincipal User currentUser) {
        return transactionService.get(id, currentUser.getId());
    }

    @PutMapping("/api/transactions/{id}")
    public TransactionDto updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdateTransactionRequest request,
                                        @AuthenticationPrincipal User currentUser) {
        return transactionService.updateStatus(id, request, currentUser.getId());
    }

    @GetMapping("/api/transactions/{id}/memberships")
    public List<TransactionMembershipDto> memberships(@PathVariable UUID id, @AuthenticationPrincipal User currentUser) {
        return transactionService.listMemberships(id, currentUser.getId());
    }

    @PostMapping("/api/transactions/{id}/memberships")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionMembershipDto addMembership(@PathVariable UUID id,
                                                    @Valid @RequestBody AddTransactionMembershipRequest request,
                                                    @AuthenticationPrincipal User currentUser) {
        return transactionService.addMembership(id, request, currentUser.getId());
    }

    @DeleteMapping("/api/transactions/{id}/memberships/{membershipId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMembership(@PathVariable UUID id, @PathVariable UUID membershipId,
                                  @AuthenticationPrincipal User currentUser) {
        transactionService.removeMembership(id, membershipId, currentUser.getId());
    }

    @PostMapping("/api/transactions/{id}/approval-signatures")
    @ResponseStatus(HttpStatus.CREATED)
    public ApprovalSignatureDto signApproval(@PathVariable UUID id, @Valid @RequestBody SignApprovalRequest request,
                                              @AuthenticationPrincipal User currentUser) {
        return transactionService.signApproval(id, request, currentUser);
    }

    @GetMapping("/api/transactions/{id}/approval-signatures")
    public ApprovalStatusDto approvalStatus(@PathVariable UUID id, @RequestParam String transition,
                                             @AuthenticationPrincipal User currentUser) {
        return transactionService.getApprovalStatus(id, transition, currentUser.getId());
    }
}
