package com.syndicate.regulatory.engine;

import com.syndicate.common.BaseEntity;
import com.syndicate.transaction.Transaction;
import com.syndicate.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Pinned set of regulatory requirement versions for a specific transaction (spec §13; plan D3).
 * Prevents silent re-evaluations when regulations are upgraded.
 */
@Entity
@Table(name = "regulatory_snapshots")
public class RegulatorySnapshot extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    @Column(nullable = false)
    private String name;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "pinned_at", nullable = false)
    private Instant pinnedAt = Instant.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pinned_by_id")
    private User pinnedBy;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @OneToMany(mappedBy = "snapshot", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RegulatorySnapshotItem> items = new ArrayList<>();

    protected RegulatorySnapshot() {
    }

    public RegulatorySnapshot(Transaction transaction, String name, LocalDate effectiveDate, User pinnedBy, String notes) {
        this.transaction = transaction;
        this.name = name;
        this.effectiveDate = effectiveDate;
        this.pinnedBy = pinnedBy;
        this.notes = notes;
        this.pinnedAt = Instant.now();
        this.active = true;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public void setTransaction(Transaction transaction) {
        this.transaction = transaction;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public void setEffectiveDate(LocalDate effectiveDate) {
        this.effectiveDate = effectiveDate;
    }

    public Instant getPinnedAt() {
        return pinnedAt;
    }

    public void setPinnedAt(Instant pinnedAt) {
        this.pinnedAt = pinnedAt;
    }

    public User getPinnedBy() {
        return pinnedBy;
    }

    public void setPinnedBy(User pinnedBy) {
        this.pinnedBy = pinnedBy;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public List<RegulatorySnapshotItem> getItems() {
        return items;
    }

    public void setItems(List<RegulatorySnapshotItem> items) {
        this.items = items;
    }
}
