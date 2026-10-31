package com.syndicate.captable;

import com.syndicate.common.BaseEntity;
import com.syndicate.transaction.Transaction;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "cap_table_holders")
public class CapTableHolder extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    @Column(name = "party_id")
    private UUID partyId;

    @Column(name = "legal_name", nullable = false, length = 255)
    private String legalName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private HolderCategory category;

    @Column(length = 32)
    private String pan;

    @Column(name = "demat_account_no", length = 64)
    private String dematAccountNo;

    @Column(name = "residential_status", length = 32)
    private String residentialStatus = "RESIDENT_INDIAN";

    protected CapTableHolder() {
    }

    public CapTableHolder(Transaction transaction, UUID partyId, String legalName,
                          HolderCategory category, String pan, String dematAccountNo,
                          String residentialStatus) {
        this.transaction = transaction;
        this.partyId = partyId;
        this.legalName = legalName;
        this.category = category;
        this.pan = pan;
        this.dematAccountNo = dematAccountNo;
        this.residentialStatus = residentialStatus != null ? residentialStatus : "RESIDENT_INDIAN";
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public UUID getPartyId() {
        return partyId;
    }

    public void setPartyId(UUID partyId) {
        this.partyId = partyId;
    }

    public String getLegalName() {
        return legalName;
    }

    public void setLegalName(String legalName) {
        this.legalName = legalName;
    }

    public HolderCategory getCategory() {
        return category;
    }

    public void setCategory(HolderCategory category) {
        this.category = category;
    }

    public String getPan() {
        return pan;
    }

    public void setPan(String pan) {
        this.pan = pan;
    }

    public String getDematAccountNo() {
        return dematAccountNo;
    }

    public void setDematAccountNo(String dematAccountNo) {
        this.dematAccountNo = dematAccountNo;
    }

    public String getResidentialStatus() {
        return residentialStatus;
    }

    public void setResidentialStatus(String residentialStatus) {
        this.residentialStatus = residentialStatus;
    }
}
