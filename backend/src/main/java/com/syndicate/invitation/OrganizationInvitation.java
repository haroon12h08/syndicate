package com.syndicate.invitation;

import com.syndicate.common.BaseEntity;
import com.syndicate.organization.OrgRole;
import com.syndicate.organization.Organization;
import com.syndicate.user.User;
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
@Table(name = "organization_invitations")
public class OrganizationInvitation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inviter_user_id", nullable = false)
    private User inviterUser;

    @Column(name = "invitee_email", nullable = false)
    private String inviteeEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrgRole role;

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

    protected OrganizationInvitation() {
    }

    public OrganizationInvitation(Organization organization, User inviterUser, String inviteeEmail, OrgRole role,
                                   String token, Instant expiresAt) {
        this.organization = organization;
        this.inviterUser = inviterUser;
        this.inviteeEmail = inviteeEmail;
        this.role = role;
        this.token = token;
        this.status = InvitationStatus.PENDING;
        this.expiresAt = expiresAt;
    }

    public Organization getOrganization() {
        return organization;
    }

    public User getInviterUser() {
        return inviterUser;
    }

    public String getInviteeEmail() {
        return inviteeEmail;
    }

    public OrgRole getRole() {
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
