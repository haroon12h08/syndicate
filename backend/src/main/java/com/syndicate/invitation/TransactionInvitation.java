package com.syndicate.invitation;

import com.syndicate.common.BaseEntity;
import com.syndicate.organization.Organization;
import com.syndicate.transaction.Transaction;
import com.syndicate.transaction.TransactionRole;
import com.syndicate.user.User;
import com.syndicate.workstream.Workstream;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "transaction_invitations")
public class TransactionInvitation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inviter_user_id", nullable = false)
    private User inviterUser;

    /** Null for an organization-level invite (see {@link #isOrganizationInvite()}). */
    @Column(name = "invitee_email")
    private String inviteeEmail;

    /**
     * For an individual invite: the org the invitee will represent (must already exist).
     * For an organization-level invite: the org being invited itself.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    /** Optional: the workstream this participant is primarily being brought on for. Informational only. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workstream_id")
    private Workstream workstream;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionRole role;

    @Column(nullable = false, unique = true)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvitationStatus status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responded_by_user_id")
    private User respondedByUser;

    protected TransactionInvitation() {
    }

    public TransactionInvitation(Transaction transaction, User inviterUser, String inviteeEmail,
                                  Organization organization, Workstream workstream, TransactionRole role,
                                  String token, Instant expiresAt) {
        this.transaction = transaction;
        this.inviterUser = inviterUser;
        this.inviteeEmail = inviteeEmail;
        this.organization = organization;
        this.workstream = workstream;
        this.role = role;
        this.token = token;
        this.status = InvitationStatus.PENDING;
        this.expiresAt = expiresAt;
    }

    public boolean isOrganizationInvite() {
        return inviteeEmail == null;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public User getInviterUser() {
        return inviterUser;
    }

    public String getInviteeEmail() {
        return inviteeEmail;
    }

    public Organization getOrganization() {
        return organization;
    }

    public Workstream getWorkstream() {
        return workstream;
    }

    public TransactionRole getRole() {
        return role;
    }

    public String getToken() {
        return token;
    }

    public InvitationStatus getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRespondedAt() {
        return respondedAt;
    }

    public User getRespondedByUser() {
        return respondedByUser;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public void accept(User responder) {
        this.status = InvitationStatus.ACCEPTED;
        this.respondedAt = Instant.now();
        this.respondedByUser = responder;
    }

    public void reject(User responder) {
        this.status = InvitationStatus.REJECTED;
        this.respondedAt = Instant.now();
        this.respondedByUser = responder;
    }

    public void revoke() {
        this.status = InvitationStatus.REVOKED;
    }
}
