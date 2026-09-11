package com.syndicate.transaction;

import com.syndicate.common.BaseEntity;
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
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "transaction_memberships", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"transaction_id", "user_id"})
})
public class TransactionMembership extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionRole role;

    protected TransactionMembership() {
    }

    public TransactionMembership(Transaction transaction, Organization organization, User user, TransactionRole role) {
        this.transaction = transaction;
        this.organization = organization;
        this.user = user;
        this.role = role;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public Organization getOrganization() {
        return organization;
    }

    public User getUser() {
        return user;
    }

    public TransactionRole getRole() {
        return role;
    }
}
