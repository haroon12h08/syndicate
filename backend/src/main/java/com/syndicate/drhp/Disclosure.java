package com.syndicate.drhp;

import com.syndicate.common.BaseEntity;
import com.syndicate.fact.Fact;
import com.syndicate.transaction.Transaction;
import com.syndicate.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "disclosures")
public class Disclosure extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    @Column(name = "section_code", nullable = false)
    private String sectionCode;

    @Column(nullable = false)
    private String title;

    @Column(name = "body_template", nullable = false)
    private String bodyTemplate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DisclosureStatus status;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    @Column(name = "stale_reason")
    private String staleReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdByUser;

    /** The facts this disclosure is derived from — the dependency edges of the DAG. */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "disclosure_fact_link",
            joinColumns = @JoinColumn(name = "disclosure_id"),
            inverseJoinColumns = @JoinColumn(name = "fact_id")
    )
    private Set<Fact> sourceFacts = new HashSet<>();

    protected Disclosure() {
    }

    public Disclosure(Transaction transaction, String sectionCode, String title, String bodyTemplate,
                       int orderIndex, User createdByUser) {
        this.transaction = transaction;
        this.sectionCode = sectionCode;
        this.title = title;
        this.bodyTemplate = bodyTemplate;
        this.orderIndex = orderIndex;
        this.createdByUser = createdByUser;
        this.status = DisclosureStatus.DRAFT;
    }

    public void edit(String title, String bodyTemplate, DisclosureStatus status, int orderIndex) {
        this.title = title;
        this.bodyTemplate = bodyTemplate;
        this.orderIndex = orderIndex;
        if (status != null) {
            this.status = status;
            if (status != DisclosureStatus.STALE_REQUIRING_REVIEW) {
                this.staleReason = null;
            }
        }
    }

    /** A source fact changed underneath this text, so a human has to re-read it. */
    public void markStale(String reason) {
        this.status = DisclosureStatus.STALE_REQUIRING_REVIEW;
        this.staleReason = reason;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public String getSectionCode() {
        return sectionCode;
    }

    public String getTitle() {
        return title;
    }

    public String getBodyTemplate() {
        return bodyTemplate;
    }

    public DisclosureStatus getStatus() {
        return status;
    }

    public int getOrderIndex() {
        return orderIndex;
    }

    public String getStaleReason() {
        return staleReason;
    }

    public User getCreatedByUser() {
        return createdByUser;
    }

    public Set<Fact> getSourceFacts() {
        return sourceFacts;
    }
}
