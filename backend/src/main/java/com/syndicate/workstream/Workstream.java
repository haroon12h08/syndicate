package com.syndicate.workstream;

import com.syndicate.common.BaseEntity;
import com.syndicate.transaction.Transaction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "workstreams")
public class Workstream extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WorkstreamType type;

    @Column
    private String description;

    protected Workstream() {
    }

    public Workstream(Transaction transaction, WorkstreamType type, String description) {
        this.transaction = transaction;
        this.type = type;
        this.description = description;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public WorkstreamType getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
