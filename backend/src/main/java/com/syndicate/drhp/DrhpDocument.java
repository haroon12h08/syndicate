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

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "drhp_documents")
public class DrhpDocument extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    @Column(nullable = false)
    private int version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DrhpStatus status;

    @Column(name = "compiled_body", nullable = false)
    private String compiledBody;

    @Column(name = "lint_summary")
    private String lintSummary;

    @Column(name = "invalidated_reason")
    private String invalidatedReason;

    @Column(name = "compiled_at", nullable = false)
    private Instant compiledAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "compiled_by_user_id")
    private User compiledByUser;

    /** Every fact whose value was printed into this document. */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "drhp_fact_link",
            joinColumns = @JoinColumn(name = "drhp_document_id"),
            inverseJoinColumns = @JoinColumn(name = "fact_id")
    )
    private Set<Fact> citedFacts = new HashSet<>();

    protected DrhpDocument() {
    }

    public DrhpDocument(Transaction transaction, int version, String compiledBody, String lintSummary,
                         User compiledByUser) {
        this.transaction = transaction;
        this.version = version;
        this.compiledBody = compiledBody;
        this.lintSummary = lintSummary;
        this.compiledByUser = compiledByUser;
        this.status = DrhpStatus.COMPILED;
        this.compiledAt = Instant.now();
    }

    /** A cited fact was superseded, so this compiled document no longer reflects the record. */
    public void invalidate(String reason) {
        this.status = DrhpStatus.INVALIDATED;
        this.invalidatedReason = reason;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public int getVersion() {
        return version;
    }

    public DrhpStatus getStatus() {
        return status;
    }

    public String getCompiledBody() {
        return compiledBody;
    }

    public String getLintSummary() {
        return lintSummary;
    }

    public String getInvalidatedReason() {
        return invalidatedReason;
    }

    public Instant getCompiledAt() {
        return compiledAt;
    }

    public User getCompiledByUser() {
        return compiledByUser;
    }

    public Set<Fact> getCitedFacts() {
        return citedFacts;
    }
}
